# Mem4J — Memory Manipulation Library for Java

Mem4J is a Java library that exposes process memory primitives — attaching to a running process, resolving module base addresses, following pointer chains, reading and writing typed values, and locating addresses by byte signatures — entirely from Java, without writing C++ or maintaining a JNI bridge.

It runs on **both Windows and Linux** behind the same `Pointer` / `Memory` API. The platform-specific layer is selected at runtime via a `NativeAccess` abstraction:

- On **Windows** it wraps the Win32 APIs `OpenProcess`, `ReadProcessMemory`, `WriteProcessMemory`, `CreateToolhelp32Snapshot`, `Module32First/NextW`, and `Process32NextW` through [JNA](https://github.com/java-native-access/jna).
- On **Linux** it uses `/proc/<pid>/maps` for module discovery and `/proc/<pid>/mem` for memory I/O. Process lookup is performed via `/proc/<pid>/comm` and the `/proc/<pid>/exe` symlink.

---

## Features

- **Process attachment** — open a handle to a target process by its executable name (`Pointer.getBaseAddress(String)`). `Pointer` implements `AutoCloseable`, so the handle / file descriptor is released on `close()`.
- **Module base resolution** — locate the in-memory base address of a loaded module / mapped binary.
- **Module enumeration** — `ProcessUtil.listModules(pid)` returns every loaded module with name, full path, base address and size (cross-platform).
- **Typed read/write** — read and write `byte`, `short`, `int`, `long`, `float`, and `double` directly at an absolute or offset-based address. Endianness is configurable per `Pointer` via `withByteOrder(ByteOrder)`.
- **Bulk I/O & strings** — `readBytes` / `writeBytes` for raw buffers; `readString` / `writeString` for NUL-terminated or fixed-length strings (any `Charset`).
- **Pointer chains** — dereference 64-bit *and* 32-bit pointers, chain offsets (`copy()`, `add()`, `indirect64()`, `indirect32()`) to follow multi-level pointer paths typical of game/engine internals.
- **Signature (AOB) scanning** — locate an address inside the target's memory using a byte pattern + mask, e.g. `"xx?xx??x"`. Works on both Windows and Linux.
- **Memory protection & allocation** — wrap `VirtualProtectEx`, `VirtualAllocEx`, `VirtualFreeEx`, `VirtualQueryEx` for code caves and page-permission tricks on Windows. On Linux `queryProtection` is supported via `/proc/<pid>/maps`; `protect`/`allocate`/`free` would require syscall injection and throw `UnsupportedOperationException`.
- **Write to protected pages** — `Pointer.force()` returns a sibling pointer whose writes flip the page to writable, perform the write, then restore the original protection. Works for read-only and executable mappings (e.g. patching `.text`).
- **Privilege check** — refuses to operate unless the JVM is privileged, throwing a `PrivilegeException` (no more `System.exit`).

---

## Requirements

| Component         | Version / Note                                                |
|-------------------|---------------------------------------------------------------|
| Java              | **11 or higher** (uses `ProcessHandle`, available since Java 9; project targets Java 11) |
| Operating system  | **Windows** (`kernel32.dll`, `user32.dll`, `shell32.dll`) **or Linux** (`/proc/<pid>/{maps,mem,comm,exe}` + `libc` for `geteuid`) |
| Architecture      | The JVM bitness **must match** the target process. A 32-bit JVM cannot read/write a 64-bit process and vice versa. Use a 64-bit JDK against 64-bit targets. |
| Privileges        | **Windows:** Administrator (checked via `Shell32.IsUserAnAdmin`). **Linux:** `euid == 0` (root) or the JVM granted `CAP_SYS_PTRACE`. The library throws `PrivilegeException` otherwise. |
| Runtime deps      | `net.java.dev.jna:jna:5.12.1`, `net.java.dev.jna:jna-platform:5.12.1` |

---

## Installation

Mem4J is published through [JitPack](https://jitpack.io), which builds artifacts directly from this GitHub repository on demand.

```xml
<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.christopherproject</groupId>
        <artifactId>Mem4J</artifactId>
        <version>1.0.1</version>
    </dependency>
</dependencies>
```

For Gradle:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.christopherproject:Mem4J:1.0.1'
}
```

You can also pin to a branch (e.g. `master-SNAPSHOT`) or a specific commit hash — see the [JitPack docs](https://docs.jitpack.io/) for details.

---

## Architecture

Platform dispatch is centralised in `it.adrian.code.platform.NativeAccess`. The first call to `NativeAccess.get()` inspects `com.sun.jna.Platform` and reflectively loads exactly one backend, so the unused backend's classes (and its native libraries) are never initialised:

```
NativeAccess (abstract)
 ├── WindowsAccess  → kernel32 / user32 / shell32 via JNA
 └── LinuxAccess    → /proc/<pid>/maps, /proc/<pid>/mem, libc geteuid
```

`Pointer` and `Memory` route all reads, writes, process lookup, and privilege checks through this interface, so the same call sites work on both platforms. The Windows-specific `ProcessUtil.getModule`, `Shell32Util`, `SignatureManager` and `SignatureUtil` remain available unchanged for existing Windows callers.

---

## Quick start

The same code works on Windows and Linux — only the process name differs (Windows wants the `.exe`, Linux wants whatever appears in `/proc/<pid>/comm`).

**Windows** (run as Administrator):

```java
import it.adrian.code.Memory;
import it.adrian.code.memory.Pointer;

public class WindowsExample {
    public static void main(String[] args) {
        // try-with-resources releases the OS handle on exit.
        try (Pointer base = Pointer.getBaseAddress("notepad.exe")) {

            // Read an int 0x1234 bytes past the module base.
            int value = Memory.readMemory(base, 0x1234L, Integer.class);
            System.out.println("Value at notepad.exe+0x1234 = " + value);

            // Write a new int back to the same location.
            Memory.writeMemory(base, 0x1234L, 42, Integer.class);
        }
    }
}
```

**Linux** (run as `root`, or grant the JVM `CAP_SYS_PTRACE` — see [Linux examples](#linux-examples) below):

```java
import it.adrian.code.Memory;
import it.adrian.code.memory.Pointer;

public class LinuxExample {
    public static void main(String[] args) {
        // try-with-resources closes /proc/<pid>/mem on exit.
        try (Pointer base = Pointer.getBaseAddress("firefox")) {

            // Read an int 0x1234 bytes past the main binary's base address.
            int value = Memory.readMemory(base, 0x1234L, Integer.class);
            System.out.println("Value at firefox+0x1234 = " + value);

            // Write a new int back to the same location.
            Memory.writeMemory(base, 0x1234L, 42, Integer.class);
        }
    }
}
```

> **Privileges required.** On Windows the library throws `PrivilegeException` without Administrator rights. On Linux it does the same unless `euid == 0` or the JVM has `CAP_SYS_PTRACE`. The process/module lookup throws `ProcessNotFoundException` / `ModuleNotFoundException`. All of these extend `Mem4JException` (a `RuntimeException`) so a single catch is enough.

---

## Usage

### Attaching to a process

`Pointer.getBaseAddress(processName)` resolves the PID and the main module's base address for the named target. The mechanism is platform-specific:

- **Windows:** opens a handle via `OpenProcess` with `PROCESS_VM_READ | PROCESS_VM_WRITE | PROCESS_VM_OPERATION` (`0x0010 | 0x0020 | 0x0008`) and locates the module through `CreateToolhelp32Snapshot` + `Module32First/NextW`. Match is against `MODULEENTRY32W.szModule` (e.g. `"game.exe"`).
- **Linux:** scans `/proc/*/comm` and the `/proc/*/exe` symlink basename to find the PID, then opens `/proc/<pid>/mem` for r/w. The module base is the lowest start address in `/proc/<pid>/maps` whose pathname basename equals the given name (or whose full path matches it).

```java
Pointer base = Pointer.getBaseAddress("game.exe"); // Windows
// or
Pointer base = Pointer.getBaseAddress("game");     // Linux binary name
```

If no process matches, `ProcessNotFoundException` is thrown. If the process is found but its main module is not visible (e.g. the JVM lacks permission to read its mappings), `ModuleNotFoundException` is thrown. The returned `Pointer` carries an internal `offset` initialised to `0` and **must be closed** (`Pointer` is `AutoCloseable` — use a try-with-resources block).

### Reading and writing typed values

`Memory.readMemory` and `Memory.writeMemory` are the high-level entry points. They take a base `Pointer`, an offset in bytes, and the target type:

```java
int    hp     = Memory.readMemory(base, 0x00ABCDEFL, Integer.class);
long   xp     = Memory.readMemory(base, 0x00ABCDF8L, Long.class);
float  speed  = Memory.readMemory(base, 0x00ABCE00L, Float.class);
double scale  = Memory.readMemory(base, 0x00ABCE10L, Double.class);

Memory.writeMemory(base, 0x00ABCDEFL, 9999,      Integer.class);
Memory.writeMemory(base, 0x00ABCDF8L, 100_000L,  Long.class);
Memory.writeMemory(base, 0x00ABCE00L, 12.5f,     Float.class);
Memory.writeMemory(base, 0x00ABCE10L, 0.75d,     Double.class);
```

Supported types: `Byte.class`, `Short.class`, `Integer.class`, `Long.class`, `Float.class`, `Double.class`. Any other type throws `IllegalArgumentException`. Failed reads throw `MemoryAccessException` (e.g. unmapped page, insufficient page protection).

The `offset` parameter is honoured for its full `long` range — earlier versions silently truncated it to 32 bits. Internally each call does `baseAddr.copy().add(offset)` so the supplied `base` is not mutated between calls.

### Bulk I/O and strings

```java
try (Pointer base = Pointer.getBaseAddress("game.exe")) {
    Pointer p = base.copy().add(0x1000);

    byte[] header = p.readBytes(64);

    String name = p.copy().add(0x100).readString(32);                          // UTF-8, NUL-terminated
    String wide = p.copy().add(0x100).readString(32, StandardCharsets.UTF_16LE);

    p.copy().add(0x200).writeBytes(new byte[]{ 0x48, 0x65, 0x6C, 0x6C, 0x6F });
    p.copy().add(0x200).writeString("Hello");
}
```

### Endianness

By default a `Pointer` decodes little-endian. To target a big-endian process (e.g. ARM), call `withByteOrder` once:

```java
Pointer p = base.copy().add(0x1234).withByteOrder(ByteOrder.BIG_ENDIAN);
int value = p.readInt();
```

### Pointer chains (multi-level pointers)

Real-world targets often expose data through pointer chains like `module.dll+0x123456 → +0x10 → +0x20 → value`. The `Pointer` class lets you express that path:

```java
Pointer base = Pointer.getBaseAddress("game.exe");

Pointer p = base.copy()
                .add(0x123456)   // module+0x123456
                .indirect64()    // dereference the 64-bit pointer
                .add(0x10)       // +0x10
                .indirect64()    // dereference again
                .add(0x20);      // +0x20

int hp = Memory.readMemory(p, 0L, Integer.class);
```

| Method            | Effect                                                          |
|-------------------|-----------------------------------------------------------------|
| `copy()`          | Returns a new `Pointer` with the same handle, base, offset and byte order. Use this before mutating to avoid touching the original. |
| `add(long)`       | Adds bytes to the current offset and returns `this` (mutable, fluent). Accepts the full `long` range. |
| `indirect64()`    | Reads a 64-bit pointer at the current address, replaces the base with that value, and resets the offset to `0`. |
| `indirect32()`    | Same as `indirect64()` but reads a zero-extended 32-bit pointer — use against 32-bit targets. |
| `withByteOrder()` | Switch this pointer's endianness for subsequent reads/writes.  |
| `close()`         | Release the underlying OS handle / file descriptor.            |
| `toString()`      | Pretty-prints as `module[0xBASE]+0xOFFSET => 0xFINAL`.         |

### Signature (AOB) scanning

When offsets shift between builds, byte signatures are more stable. `SignatureManager` scans the target module's address range for a pattern and returns the relative offset of the matched address — **cross-platform**:

```java
import it.adrian.code.memory.Pointer;
import it.adrian.code.signatures.SignatureManager;

byte[] pattern = new byte[] {
    (byte) 0x48, (byte) 0x8B, 0x00, 0x00, (byte) 0x05, 0x00, 0x00, 0x00, (byte) 0xC3
};
String mask = "xx??x???x";

try (Pointer base = Pointer.getBaseAddress("game.exe")) {
    SignatureManager sm = new SignatureManager(base);
    long relativeOffset = sm.getPtrFromSignature(base.getBaseAddressValue(), pattern, mask);
    int value = Memory.readMemory(base, relativeOffset, Integer.class);
}
```

The mask uses `'x'` for "must match exactly" and any other character (typically `'?'`) for "wildcard". `getPtrFromSignature` interprets the matched site as a `mov`/`lea`-style RIP-relative instruction: it reads the 4-byte displacement at `match+3`, then computes `match + displacement + 7`, returning the final address as an offset relative to the module base. Unlike older releases, `SignatureManager` no longer closes the underlying handle — the caller owns the lifecycle (use try-with-resources on the `Pointer`).

### Writing to protected memory

By default `WriteProcessMemory` (Windows) and `/proc/<pid>/mem` (Linux) handle most pages transparently, but writing into a `PAGE_EXECUTE_READ` section on Windows usually fails. Use `Pointer.force()` to bypass that:

```java
try (Pointer base = Pointer.getBaseAddress("game.exe")) {
    // Patch a single instruction (5 bytes) inside the .text section.
    byte[] nopSled = {(byte)0x90,(byte)0x90,(byte)0x90,(byte)0x90,(byte)0x90};
    base.copy().add(0x1234).force().writeBytes(nopSled);
    // On Windows the page protection is restored to its original value after the write.
    // On Linux the call simply forwards to /proc/<pid>/mem (which already ignores protection).
}
```

### Memory protection and allocation *(Windows-only)*

```java
try (Pointer base = Pointer.getBaseAddress("game.exe")) {
    NativeAccess na = NativeAccess.get();

    // Make 4 KiB at base+0x1000 writable+executable for a hook.
    base.copy().add(0x1000).protect(0x1000, MemoryProtection.READ_WRITE_EXECUTE);

    // Query the current protection of a region.
    MemoryProtection prot = na.queryProtection(base.getSession(), base.getBaseAddressValue() + 0x2000);

    // Allocate a remote 4 KiB block for a code cave.
    long cave = na.allocate(base.getSession(), 0x1000, MemoryProtection.READ_WRITE_EXECUTE);
    na.writeMemory(base.getSession(), cave, shellcode, shellcode.length);
    // ...
    na.free(base.getSession(), cave, 0);
}
```

On Linux `protect`, `allocate` and `free` throw `UnsupportedOperationException` — remote `mprotect` / `mmap` would require injecting a syscall via `ptrace`, which is outside this library's scope. `queryProtection` is supported on Linux via `/proc/<pid>/maps`.

### Linux examples

```java
// Read the ELF magic of any running Java process from its own VM.
// Run with: sudo java -cp Mem4J:jna:jna-platform Demo
try (Pointer base = Pointer.getBaseAddress("java")) {
    byte[] magic = base.readBytes(4);
    // → 7F 45 4C 46  ("\x7FELF")

    // Walk every loaded shared library of the process.
    for (ModuleInfo m : ProcessUtil.listModules(base.getSession().pid)) {
        System.out.printf("0x%016x %s%n", m.baseAddress(), m.path());
    }
}
```

```java
// Patch a global variable inside the heap of another process.
// The Linux binary name is whatever appears in /proc/<pid>/comm (no .exe suffix).
try (Pointer base = Pointer.getBaseAddress("my_game")) {
    long hpOffset = 0x00045128L;
    int  current  = Memory.readMemory(base, hpOffset, Integer.class);
    Memory.writeMemory(base, hpOffset, 9999, Integer.class);
}
```

```java
// Follow a 4-level pointer chain in a 64-bit Linux process (Cheat-Engine style).
try (Pointer base = Pointer.getBaseAddress("Hollow_Knight.x86_64")) {
    Pointer hp = base.copy()
            .add(0x01F2C720)
            .indirect64().add(0xB0)
            .indirect64().add(0x28)
            .indirect64().add(0x1C);
    System.out.println("HP = " + hp.readInt());
}
```

Linux notes:
- Run as `root`, or grant the JVM `CAP_SYS_PTRACE` (`sudo setcap cap_sys_ptrace+ep $(realpath $(which java))`). Otherwise `/proc/<pid>/mem` cannot be opened for processes you don't own.
- Many distros set `kernel.yama.ptrace_scope=1`. To attach to a non-child process, either run as root or set `sysctl kernel.yama.ptrace_scope=0`.
- The process name is matched against `/proc/<pid>/comm` (truncated to 15 chars) and the basename of `/proc/<pid>/exe`, in that order. If two processes share the same name, the first match wins.

### Utilities

| Class / method                                  | Platform | Purpose                                                                 |
|-------------------------------------------------|----------|-------------------------------------------------------------------------|
| `NativeAccess.get()`                            | both     | Returns the platform-specific backend (`WindowsAccess` or `LinuxAccess`). |
| `NativeAccess.findPidByName(String)`            | both     | First PID whose executable name matches.                                |
| `NativeAccess.getModuleBaseAddress(pid, name)`  | both     | Base address of a loaded module / mapped binary.                        |
| `NativeAccess.getModuleSize(pid, name)`         | both     | Mapped size of the module (max end − min start across mappings on Linux). |
| `NativeAccess.listModules(int pid)`             | both     | Every loaded module / mapped binary as `List<ModuleInfo>`.              |
| `NativeAccess.protect/allocate/free`            | Windows  | `VirtualProtectEx` / `VirtualAllocEx` / `VirtualFreeEx`. Throws on Linux. |
| `NativeAccess.queryProtection(session, addr)`   | both     | Current page protection at the address; reads `/proc/<pid>/maps` on Linux. |
| `Pointer.force()`                               | both     | Returns a sibling pointer that flips protection around its writes (no-op on Linux). |
| `NativeAccess.isPrivileged()`                   | both     | Admin on Windows, `euid == 0` on Linux.                                 |
| `ProcessUtil.getProcessPidByName(String)`       | both     | Thin wrapper around `NativeAccess.findPidByName`.                       |
| `ProcessUtil.listModules(int pid)`              | both     | Thin wrapper around `NativeAccess.listModules`.                         |
| `ProcessUtil.getModule(int pid, String name)`   | Windows  | *Deprecated.* Returns the `MODULEENTRY32W` for the named module. Throws on Linux. |
| `Shell32Util.isUserWindowsAdmin()`              | Windows  | Returns `true` if the current process has Administrator rights; `false` on Linux. |

---

## API reference (cheat sheet)

```text
Memory
  static <T> T    readMemory(Pointer base, long offset, Class<T> type)
  static <T> void writeMemory(Pointer base, long offset, T value, Class<T> type)
                  // T ∈ { Byte, Short, Integer, Long, Float, Double }

Pointer (implements AutoCloseable)
  static Pointer  getBaseAddress(String processName)
  Pointer         copy()
  Pointer         add(long bytes)
  Pointer         indirect64()
  Pointer         indirect32()
  Pointer         withByteOrder(ByteOrder order)
  byte/short/int/long/float/double  read*()
  boolean         write*(value)
  byte[]          readBytes(int len)         boolean writeBytes(byte[])
  String          readString(int max [, Charset])
  boolean         writeString(String [, Charset])
  boolean         protect(long size, MemoryProtection)
  Pointer         force()                                  // bypass page protection on writes
  void            close()

NativeAccess
  static NativeAccess          get()
  int                          findPidByName(String)
  long                         getModuleBaseAddress(int pid, String name)
  long                         getModuleSize(int pid, String name)
  List<ModuleInfo>             listModules(int pid)
  ProcessSession               openProcess(int pid)
  boolean                      readMemory/writeMemory(session, address, byte[], length)
  boolean                      protect(session, address, size, MemoryProtection)        // Windows
  long                         allocate(session, size, MemoryProtection)                // Windows
  boolean                      free(session, address, size)                             // Windows
  MemoryProtection             queryProtection(session, address)
  void                         closeSession(ProcessSession)
  boolean                      isPrivileged()
  void                         ensurePrivileged()  // throws PrivilegeException

SignatureManager(Pointer)
SignatureManager(ProcessSession, String moduleName)
  long            getPtrFromSignature(long moduleBaseAddress, byte[] sig, String mask)

SignatureUtil
  static long     findSignature(ProcessSession session, long start, long size, byte[] sig, String mask)
  static int      readInt(ProcessSession session, long address)

ProcessUtil
  static int                   getProcessPidByName(String name)
  static List<ModuleInfo>      listModules(int pid)

Exceptions (it.adrian.code.exceptions)
  Mem4JException                       // root, extends RuntimeException
   ├── PrivilegeException
   ├── ProcessNotFoundException
   ├── ModuleNotFoundException
   └── MemoryAccessException
```

---

## Type sizes

The read/write primitives map to fixed-width writes/reads in the target process, following the [Java Language Specification §4.2.1](https://docs.oracle.com/javase/specs/jls/se11/html/jls-4.html#jls-4.2.1):

| Java type | Bytes written/read |
|-----------|--------------------|
| `byte`    | 1                  |
| `short`   | 2                  |
| `int`     | 4                  |
| `long`    | 8                  |
| `float`   | 4                  |
| `double`  | 8                  |

---

## Limitations & caveats

- **macOS is not supported.** Only Windows and Linux backends ship. The factory throws `UnsupportedOperationException` on other platforms.
- **Bitness must match.** A 32-bit JVM cannot operate on a 64-bit target (or vice versa). Use the appropriate JDK distribution.
- **No anti-cheat / kernel bypass.** Memory access goes through documented OS APIs. On Windows, targets protected by anti-tamper drivers or Protected Process Light (PPL) reject `OpenProcess` with `ERROR_ACCESS_DENIED`. On Linux, processes marked non-dumpable or owned by another user with no `CAP_SYS_PTRACE` cannot be opened.
- **Process attachment is by executable name only.** If two processes share the same name, the first match wins.
- **Memory protection and remote allocation are Windows-only.** Implementing them on Linux requires injecting a syscall via `ptrace`, which is out of scope.
- **No test suite yet.** Validation is done via local smoke programs against `/proc/self/mem`. Coverage is planned as a follow-up.

---

## Building from source

```bash
git clone https://github.com/ChristopherProject/Mem4J.git
cd Mem4J
mvn -B package
```

Artifacts land in `target/`: the runtime jar, a sources jar and a Javadoc jar (the last two so IDEs of downstream consumers can show docs and step into Mem4J sources). CI runs the same `mvn -B package` on both `ubuntu-latest` and `windows-latest` for every push and pull request targeting `master` (see [`.github/workflows/maven.yml`](.github/workflows/maven.yml)).

---

## Credits

- **Princekin** — introduced the author to JNA.
- **Foiks** — moral support and early feedback.
- **Backq** — taught the author about memory, offsets, and signatures.

---

## License

[MIT](LICENSE). See [`LICENSE`](LICENSE) for the full text.
