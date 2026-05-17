# Mem4J — Memory Manipulation Library for Java

Mem4J is a Java library that exposes process memory primitives — attaching to a running process, resolving module base addresses, following pointer chains, reading and writing typed values, scanning byte signatures, querying and changing page protection — entirely from Java, without writing C++ or maintaining a JNI bridge.

It runs on **both Windows and Linux** behind the same `Pointer` / `Memory` API. The platform-specific layer is selected at runtime by a `NativeAccess` abstraction:

- On **Windows** it wraps `OpenProcess`, `ReadProcessMemory`, `WriteProcessMemory`, `CreateToolhelp32Snapshot`, `Module32First/NextW`, `Process32NextW`, `VirtualProtectEx`, `VirtualAllocEx`, `VirtualFreeEx`, `VirtualQueryEx` through [JNA](https://github.com/java-native-access/jna).
- On **Linux** it uses `/proc/<pid>/maps` for module discovery, `/proc/<pid>/mem` for memory I/O, `/proc/<pid>/comm` and the `/proc/<pid>/exe` symlink for process lookup, and `libc geteuid()` for the privilege check.

---

## Features

- **Process attachment** — open a remote handle / file descriptor by executable name (`Pointer.getBaseAddress(String)`), or by name + PID for ambiguous matches (`Pointer.getBaseAddress(String, int)`). `Pointer` implements `AutoCloseable`, so the handle is released on `close()`.
- **Module base resolution** — locate the in-memory base address of a loaded module / mapped binary.
- **Module enumeration** — `ProcessUtil.listModules(pid)` returns every loaded module with name, full path, base address and size (cross-platform).
- **Typed read/write** — `byte`, `short`, `int`, `long`, `float`, `double`. Endianness is configurable per `Pointer` via `withByteOrder(ByteOrder)`.
- **Bulk I/O & strings** — `readBytes` / `writeBytes` for raw buffers; `readString` / `writeString` for NUL-terminated or fixed-length strings in any `Charset`.
- **Pointer chains** — dereference 64-bit *and* 32-bit pointers, chain offsets fluently with `copy()`, `add()`, `indirect64()`, `indirect32()`.
- **Signature (AOB) scanning** — locate an address inside the target's memory using a byte pattern + mask (`"xx?xx??x"`). **Cross-platform**: same API on Windows and Linux.
- **Write into protected memory** — `Pointer.force()` returns a sibling pointer whose writes bypass page protection. On Windows the dance flips the affected pages to `PAGE_EXECUTE_READWRITE`, performs the write, then restores the original protection (e.g. patching `.text`). On Linux it is a no-op because `/proc/<pid>/mem` already ignores page protection for `CAP_SYS_PTRACE` callers.
- **Memory protection & allocation** — wrappers for `VirtualProtectEx`, `VirtualAllocEx`, `VirtualFreeEx`, `VirtualQueryEx` on Windows (production-ready). On **Linux x86_64** the same operations are emulated by injecting an `mprotect`/`mmap`/`munmap` syscall into the target via `ptrace` — **experimental**; see the [dedicated section](#memory-protection-and-allocation) for caveats. `queryProtection` works reliably on both backends without injection (reads `/proc/<pid>/maps` on Linux).
- **Embedding-friendly error handling** — every failure raises an exception from the `Mem4JException` hierarchy. No more `System.exit(-1)` or `MessageBox` pop-ups.

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
 │                    (Virtual{Protect,Alloc,Free,Query}Ex for memory protection)
 └── LinuxAccess    → /proc/<pid>/{maps,mem,comm,exe}, libc geteuid
                      (ptrace syscall injection for mprotect / mmap / munmap on x86_64)
```

`Pointer`, `Memory`, `ProcessUtil.listModules`, `SignatureManager` and `SignatureUtil` route every read, write, lookup, privilege check, AOB scan, protection query and allocation through this interface, so the same call sites work on both platforms. The remaining Windows-specific helpers (`ProcessUtil.getModule`, `Shell32Util`, and the legacy `WinNT.HANDLE`-based overloads of `SignatureManager` / `SignatureUtil` / `Pointer`'s constructor) are kept as `@Deprecated` shims for existing Windows callers.

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

**Linux** (run as `root`, or grant the JVM `CAP_SYS_PTRACE` — see [Linux notes](#linux-notes) below):

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

All the snippets below show only the body that goes inside

```java
try (Pointer base = Pointer.getBaseAddress(/* "game.exe" on Windows, "game" on Linux */)) {
    // …snippet here…
}
```

so the OS handle is always released when you leave the block.

### Attaching to a process

`Pointer.getBaseAddress(processName)` resolves the PID and the main module's base address. The mechanism is platform-specific:

- **Windows:** opens a handle via `OpenProcess` with `PROCESS_VM_READ | PROCESS_VM_WRITE | PROCESS_VM_OPERATION` (`0x0010 | 0x0020 | 0x0008`) and locates the module through `CreateToolhelp32Snapshot` + `Module32First/NextW`. Match is against `MODULEENTRY32W.szModule` (e.g. `"game.exe"`).
- **Linux:** scans `/proc/*/comm` and the `/proc/*/exe` symlink basename to find the PID, then opens `/proc/<pid>/mem` for r/w. The module base is the lowest start address in `/proc/<pid>/maps` whose pathname basename equals the given name (or whose full path matches it).

If no process matches, `ProcessNotFoundException` is thrown. If the process is found but its main module is not visible (e.g. the JVM lacks permission to read its mappings), `ModuleNotFoundException` is thrown.

When several processes share the same executable name, attach by PID directly:

```java
int pid = pickRightInstance(); // your own disambiguation logic
try (Pointer base = Pointer.getBaseAddress("game.exe", pid)) {
    // …
}
```

The single-argument overload (name only) is preserved for the common single-instance case and resolves the PID via the OS process list.

### Reading and writing typed values

`Memory.readMemory` / `Memory.writeMemory` are the high-level entry points. They take a base `Pointer`, an offset in bytes, and the target type:

```java
int    hp     = Memory.readMemory(base, 0x00ABCDEFL, Integer.class);
long   xp     = Memory.readMemory(base, 0x00ABCDF8L, Long.class);
float  speed  = Memory.readMemory(base, 0x00ABCE00L, Float.class);
double scale  = Memory.readMemory(base, 0x00ABCE10L, Double.class);

Memory.writeMemory(base, 0x00ABCDEFL, 9999,     Integer.class);
Memory.writeMemory(base, 0x00ABCDF8L, 100_000L, Long.class);
Memory.writeMemory(base, 0x00ABCE00L, 12.5f,    Float.class);
Memory.writeMemory(base, 0x00ABCE10L, 0.75d,    Double.class);
```

Supported types: `Byte.class`, `Short.class`, `Integer.class`, `Long.class`, `Float.class`, `Double.class`. Any other type throws `IllegalArgumentException`. Failed reads throw `MemoryAccessException` (e.g. unmapped page, insufficient page protection).

The `offset` parameter is honoured for its full `long` range — earlier versions silently truncated it to 32 bits. Internally each call does `baseAddr.copy().add(offset)` so the supplied `base` is not mutated between calls.

### Bulk I/O and strings

```java
Pointer p = base.copy().add(0x1000);

byte[] header = p.readBytes(64);

String name = p.copy().add(0x100).readString(32);                            // UTF-8, NUL-terminated
String wide = p.copy().add(0x100).readString(32, StandardCharsets.UTF_16LE);

p.copy().add(0x200).writeBytes(new byte[]{ 0x48, 0x65, 0x6C, 0x6C, 0x6F });
p.copy().add(0x200).writeString("Hello");
```

### Endianness

By default a `Pointer` decodes little-endian. To target a big-endian process (e.g. ARM), call `withByteOrder` once:

```java
int value = base.copy().add(0x1234).withByteOrder(ByteOrder.BIG_ENDIAN).readInt();
```

### Pointer chains (multi-level pointers)

Real-world targets often expose data through pointer chains like `module+0x123456 → +0x10 → +0x20 → value`. `Pointer` lets you express that path:

```java
Pointer p = base.copy()
                .add(0x123456)   // module+0x123456
                .indirect64()    // dereference the 64-bit pointer
                .add(0x10)       // +0x10
                .indirect64()    // dereference again
                .add(0x20);      // +0x20

int hp = Memory.readMemory(p, 0L, Integer.class);
```

| Method            | Effect                                                                                                         |
|-------------------|----------------------------------------------------------------------------------------------------------------|
| `copy()`          | Returns a new `Pointer` sharing handle, base, offset and byte order. Use this before mutating the original.   |
| `add(long)`       | Adds bytes to the current offset and returns `this` (mutable, fluent). Accepts the full `long` range.         |
| `indirect64()`    | Reads a 64-bit pointer at the current address, replaces the base with that value, and resets the offset to 0. |
| `indirect32()`    | Same as `indirect64()` but reads a zero-extended 32-bit pointer — use against 32-bit targets.                 |
| `withByteOrder()` | Switch this pointer's endianness for subsequent reads/writes.                                                  |
| `force()`         | Returns a sibling whose writes bypass page protection (see below).                                             |
| `close()`         | Release the underlying OS handle / file descriptor.                                                            |
| `toString()`      | Pretty-prints as `module[0xBASE]+0xOFFSET => 0xFINAL`.                                                         |

### Signature (AOB) scanning

When offsets shift between builds, byte signatures are more stable. `SignatureManager` scans the target module's address range for a pattern and returns the offset of the resolved address relative to the module base — **cross-platform**:

```java
byte[] pattern = new byte[] {
    (byte) 0x48, (byte) 0x8B, 0x00, 0x00, (byte) 0x05, 0x00, 0x00, 0x00, (byte) 0xC3
};
String mask = "xx??x???x";

SignatureManager sm = new SignatureManager(base);
long relativeOffset = sm.getPtrFromSignature(base.getBaseAddressValue(), pattern, mask);
int value = Memory.readMemory(base, relativeOffset, Integer.class);
```

The mask uses `'x'` for "must match exactly" and any other character (typically `'?'`) for "wildcard". `getPtrFromSignature` interprets the matched site as a `mov`/`lea`-style RIP-relative instruction: it reads the 4-byte displacement at `match+3`, then computes `match + displacement + 7`, returning the final address as an offset relative to the module base. Unlike older releases, `SignatureManager` no longer closes the underlying handle — the caller owns the lifecycle through the `Pointer`.

### Writing into protected memory

By default `WriteProcessMemory` (Windows) and `/proc/<pid>/mem` (Linux) handle most pages transparently, but writing into a `PAGE_EXECUTE_READ` section on Windows usually fails. Use `Pointer.force()` to bypass that:

```java
byte[] nopSled = { (byte)0x90, (byte)0x90, (byte)0x90, (byte)0x90, (byte)0x90 };
base.copy().add(0x1234).force().writeBytes(nopSled);
// Windows: pages are temporarily flipped to PAGE_EXECUTE_READWRITE, then restored.
// Linux:   /proc/<pid>/mem already ignores page protection for CAP_SYS_PTRACE callers,
//          so force() is a no-op.
```

### Memory protection and allocation

```java
NativeAccess na = NativeAccess.get();

// Make 4 KiB at base+0x1000 writable+executable for a hook.
base.copy().add(0x1000).protect(0x1000, MemoryProtection.READ_WRITE_EXECUTE);

// Query the current protection of any address.
MemoryProtection prot = na.queryProtection(base.getSession(), base.getBaseAddressValue() + 0x2000);

// Allocate a remote 4 KiB block for a code cave.
long cave = na.allocate(base.getSession(), 0x1000, MemoryProtection.READ_WRITE_EXECUTE);
na.writeMemory(base.getSession(), cave, shellcode, shellcode.length);
// …
na.free(base.getSession(), cave, 0);
```

Implementation:

- **Windows:** thin wrappers over `VirtualProtectEx`, `VirtualAllocEx`, `VirtualFreeEx`, `VirtualQueryEx`. Production-ready.
- **Linux x86_64 — *experimental*:** `protect` / `allocate` / `free` are implemented by **ptrace syscall injection**: the library `PTRACE_ATTACH`es the target, saves its registers and the instruction bytes at the current `RIP`, patches in `syscall; int3`, sets up `RAX` and the SysV ABI registers for `mprotect(2)` / `mmap(2)` / `munmap(2)`, runs to the breakpoint, reads the return value out of `RAX`, restores everything and detaches. The end-to-end round-trip is **not yet covered by automated tests** (the integration test is marked `@Disabled` because the injection helper is currently sensitive to the CPU state the target is in when `PTRACE_ATTACH` stops it — e.g. nested in an interrupted `nanosleep` — and can deadlock waiting for the `int3` trap). Treat this code path as experimental until a hardened version lands. `queryProtection` does **not** need injection and works reliably.
- **Other Linux architectures (ARM64, etc.):** `protect` / `allocate` / `free` throw `UnsupportedOperationException`. Pull requests welcome.

> ⚠️ ptrace injection requires `CAP_SYS_PTRACE` (or root) and the same Yama `ptrace_scope` constraints already documented in [Platform notes — Linux](#linux-notes).

### Utilities

| Class / method                                  | Platform | Purpose                                                                                                                |
|-------------------------------------------------|----------|------------------------------------------------------------------------------------------------------------------------|
| `NativeAccess.get()`                            | both     | Returns the platform-specific backend (`WindowsAccess` or `LinuxAccess`).                                              |
| `Pointer.getBaseAddress(String)`                | both     | Attach by executable name; first match wins (`ProcessNotFoundException` on miss).                                      |
| `Pointer.getBaseAddress(String, int pid)`       | both     | Attach by name + explicit PID; skips process-list lookup. Use it when multiple processes share the same executable name. |
| `NativeAccess.findPidByName(String)`            | both     | First PID whose executable name matches.                                                                               |
| `NativeAccess.getModuleBaseAddress(pid, name)`  | both     | Base address of a loaded module / mapped binary.                                                                       |
| `NativeAccess.getModuleSize(pid, name)`         | both     | Mapped size of the module (max end − min start across mappings on Linux).                                              |
| `NativeAccess.listModules(int pid)`             | both     | Every loaded module / mapped binary as `List<ModuleInfo>`.                                                             |
| `NativeAccess.queryProtection(session, addr)`   | both     | Current page protection at the address; reads `/proc/<pid>/maps` on Linux.                                             |
| `NativeAccess.protect / allocate / free`        | both     | `VirtualProtectEx` / `VirtualAllocEx` / `VirtualFreeEx` on Windows; `mprotect(2)` / `mmap(2)` / `munmap(2)` injected via `ptrace` on Linux x86_64. |
| `NativeAccess.isPrivileged()`                   | both     | Admin on Windows, `euid == 0` on Linux.                                                                                |
| `Pointer.force()`                               | both     | Returns a sibling pointer whose writes flip protection around them on Windows; no-op on Linux (`/proc/<pid>/mem` already bypasses protection). |
| `ProcessUtil.getProcessPidByName(String)`       | both     | Thin wrapper around `NativeAccess.findPidByName`.                                                                      |
| `ProcessUtil.listModules(int pid)`              | both     | Thin wrapper around `NativeAccess.listModules`.                                                                        |
| `ProcessUtil.getModule(int pid, String name)`   | Windows  | *Deprecated.* Returns the raw `MODULEENTRY32W`. Throws on Linux.                                                       |
| `Shell32Util.isUserWindowsAdmin()`              | Windows  | Returns `true` if the current process has Administrator rights; `false` on Linux.                                      |

---

## Platform notes

### Linux notes

- Run as `root`, or grant the JVM `CAP_SYS_PTRACE`:

  ```bash
  sudo setcap cap_sys_ptrace+ep "$(realpath "$(which java)")"
  ```

  Otherwise `/proc/<pid>/mem` cannot be opened for processes you don't own and you get a `PrivilegeException`.

- Many distros set `kernel.yama.ptrace_scope = 1`. To attach to a non-child process, either run as root or temporarily lower it:

  ```bash
  sudo sysctl kernel.yama.ptrace_scope=0
  ```

- The process name is matched against `/proc/<pid>/comm` (truncated to 15 chars) first, then against the basename of `/proc/<pid>/exe`. If two processes share the same name, the first match wins.

- A self-contained recipe — reading the ELF magic of *any* running `java` process (a sanity check that the Linux backend actually works on your box):

  ```java
  try (Pointer base = Pointer.getBaseAddress("java")) {
      byte[] magic = base.readBytes(4); // → 7F 45 4C 46  ("\x7FELF")
      for (ModuleInfo m : ProcessUtil.listModules(base.getSession().pid)) {
          System.out.printf("0x%016x %s%n", m.baseAddress(), m.path());
      }
  }
  ```

- Following a 4-level pointer chain inside a 64-bit Linux target (Cheat-Engine style):

  ```java
  try (Pointer base = Pointer.getBaseAddress("Hollow_Knight.x86_64")) {
      Pointer hp = base.copy()
              .add(0x01F2C720)
              .indirect64().add(0xB0)
              .indirect64().add(0x28)
              .indirect64().add(0x1C);
      System.out.println("HP = " + hp.readInt());
  }
  ```

### Windows notes

- Run the JVM elevated ("Run as administrator"). Without it `Shell32.IsUserAnAdmin()` returns false and Mem4J throws `PrivilegeException`.
- Targets protected by anti-tamper drivers, Protected Process Light (PPL), or third-party anticheat will reject `OpenProcess` with `ERROR_ACCESS_DENIED` — Mem4J cannot bypass that.
- `Pointer.force()` is the only safe way to write into a read-only or executable mapping on Windows; the dance flips protection to `PAGE_EXECUTE_READWRITE`, performs the write, then restores the original protection.

---

## API reference (cheat sheet)

```text
Memory
  static <T> T    readMemory(Pointer base, long offset, Class<T> type)
  static <T> void writeMemory(Pointer base, long offset, T value, Class<T> type)
                  // T ∈ { Byte, Short, Integer, Long, Float, Double }

Pointer (implements AutoCloseable)
  static Pointer  getBaseAddress(String processName)                  // PID resolved automatically
  static Pointer  getBaseAddress(String processName, int pid)         // disambiguate by PID
  Pointer         copy()                                              // sibling, shares the OS handle
  Pointer         add(long bytes)                                     // fluent, mutates this
  Pointer         indirect64()                                        // deref 64-bit pointer
  Pointer         indirect32()                                        // deref 32-bit pointer (zero-extended)
  Pointer         withByteOrder(ByteOrder order)
  Pointer         force()                                             // bypass page protection on writes
  byte / short / int / long / float / double  read*()
  boolean         write*(value)
  byte[]          readBytes(int len)                                  boolean writeBytes(byte[])
  String          readString(int max [, Charset])                     boolean writeString(String [, Charset])
  com.sun.jna.Memory getMemory(int size)                              // raw JNA buffer copy
  boolean         protect(long size, MemoryProtection)                // delegates to NativeAccess
  ProcessSession  getSession()
  long            getBaseAddressValue()
  long            getOffset()
  void            close()                                             // releases the session

NativeAccess
  static NativeAccess  get()                                            // lazy, picks one backend
  int                  findPidByName(String)
  long                 getModuleBaseAddress(int pid, String name)
  long                 getModuleSize(int pid, String name)
  List<ModuleInfo>     listModules(int pid)
  ProcessSession       openProcess(int pid)
  boolean              readMemory / writeMemory(session, address, byte[], length)
  MemoryProtection     queryProtection(session, address)                // /proc/<pid>/maps on Linux
  boolean              protect(session, address, size, MemoryProtection)  // ptrace inject on Linux x86_64
  long                 allocate(session, size, MemoryProtection)          // ptrace inject on Linux x86_64
  boolean              free(session, address, size)                       // ptrace inject on Linux x86_64
  void                 closeSession(ProcessSession)
  boolean              isPrivileged()
  void                 ensurePrivileged()                                 // throws PrivilegeException
  void                 throwProcessNotFound(String name)                  // helper for backends

SignatureManager(Pointer)                                              // cross-platform
SignatureManager(ProcessSession, String moduleName)                    // cross-platform
SignatureManager(WinNT.HANDLE, String, int)                            // @Deprecated, Windows shim
  long  getPtrFromSignature(long moduleBaseAddress, byte[] sig, String mask)

SignatureUtil
  static long  findSignature(ProcessSession session, long start, long size, byte[] sig, String mask)
  static int   readInt(ProcessSession session, long address)
  // @Deprecated WinNT.HANDLE-based overloads kept for Windows callers

ProcessUtil
  static int               getProcessPidByName(String name)
  static List<ModuleInfo>  listModules(int pid)
  static MODULEENTRY32W    getModule(int pid, String name)             // @Deprecated, Windows-only

Shell32Util
  static boolean           isUserWindowsAdmin()                        // false on Linux

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

- **macOS is not supported.** Only Windows and Linux backends ship. `NativeAccess.get()` throws `UnsupportedOperationException` on other platforms.
- **Bitness must match.** A 32-bit JVM cannot operate on a 64-bit target (or vice versa). Use the appropriate JDK distribution.
- **No anti-cheat / kernel bypass.** Memory access goes through documented OS APIs. On Windows, anti-tamper drivers and Protected Process Light (PPL) reject `OpenProcess` with `ERROR_ACCESS_DENIED`. On Linux, processes marked non-dumpable or owned by another user without `CAP_SYS_PTRACE` cannot be opened.
- **Process attachment defaults to executable name; PID overload available.** `Pointer.getBaseAddress(String)` matches the first process whose name matches — use `Pointer.getBaseAddress(String, int pid)` to disambiguate when several processes share the same executable name.
- **Linux `protect` / `allocate` / `free` are experimental.** On Windows they are thin wrappers over the `Virtual*Ex` family and are production-ready. On Linux x86_64 they are emulated via ptrace syscall injection (`PTRACE_ATTACH` → patch `syscall; int3` at the target's `RIP` → run → restore → `PTRACE_DETACH`). The round-trip integration test ships `@Disabled` because the helper currently deadlocks when the target is attached mid-`nanosleep` and the `int3` trap never fires — treat this path as experimental until the helper is hardened. `queryProtection` does **not** use injection and is reliable on both backends.
- **Non-x86_64 Linux has no remote memory protection / allocation.** On ARM64, RISC-V and other Linux architectures `protect` / `allocate` / `free` throw `UnsupportedOperationException`; only x86_64 has the syscall-injection helper.
- **`Pointer` shares its session across copies.** `copy()` produces a new `Pointer` that points at the same underlying handle / file descriptor as the original. Calling `close()` on any one of them closes the session for **all** sibling pointers — design around a single "root" `Pointer` whose lifetime spans every read/write, and copy freely from it inside a try-with-resources block.
- **`Pointer` is not thread-safe.** `add()`, `indirect64()`, `withByteOrder()` and the `force()` flag mutate the receiver. Per-thread `copy()` is cheap; share copies, not the originals.
- **AOB scanning reads in 64 KiB chunks.** Unmapped or unreadable pages inside the scan range are skipped silently — a match in such a region cannot be found. Use `queryProtection` if you need to assert the range is fully resident first.
- **No anti-debug / stealth.** Targets that watch for ptrace, anomalous `OpenProcess` calls, or unexpected handle counts can detect Mem4J. The library does not try to hide.

---

## Building from source

```bash
git clone https://github.com/ChristopherProject/Mem4J.git
cd Mem4J
mvn -B package
```

Artifacts land in `target/`: the runtime jar, a sources jar and a Javadoc jar (the last two so IDEs of downstream consumers can show docs and step into Mem4J sources). CI runs the same `mvn -B package` on both `ubuntu-latest` and `windows-latest` for every push and pull request targeting `master` (see [`.github/workflows/maven.yml`](.github/workflows/maven.yml)).

---

## Testing

Mem4J ships a JUnit 5 integration test suite under `src/test/java/it/adrian/code/Mem4JTests.java`. Tests exercise the active backend against the running JVM (and a short-lived `sleep` child for the ptrace injection round-trip) — there is no mock layer, every assertion is end-to-end against real kernel memory.

```bash
mvn -B test
```

The suite is privilege-aware:

- Tests that need `/proc/<pid>/mem` or ptrace use JUnit's `Assumptions.assumeTrue` to **skip cleanly** when the JVM is not privileged. They never `fail` on an unprivileged machine.
- Windows-specific tests are gated with `@EnabledOnOs(OS.WINDOWS)`; Linux-specific tests with `@EnabledOnOs(OS.LINUX)`.
- The `mmap`/`mprotect`/`munmap` round-trip via ptrace injection is currently marked `@Disabled` because the helper deadlocks against targets attached mid-`nanosleep` — see [Memory protection and allocation](#memory-protection-and-allocation) for context. The test body also asserts `amd64`/`x86_64` via `assumeTrue`, so it will additionally skip on other Linux architectures once the `@Disabled` is removed.

Counted today: **11 tests, 1 skipped** (the ptrace round-trip). All other Linux integration tests pass on a privileged JVM.

For local development on Linux:

```bash
sudo mvn -B test
# or, without sudo, after granting CAP_SYS_PTRACE to the JVM once
sudo setcap cap_sys_ptrace+ep "$(realpath "$(which java)")"
mvn -B test
```

CI runs `mvn -B test` on both `ubuntu-latest` and `windows-latest`, so the matrix exercises whichever backend matches the runner.

---

## Credits

- **Princekin** — introduced the author to JNA.
- **Foiks** — moral support and early feedback.
- **Backq** — taught the author about memory, offsets, and signatures.

---

## License

[MIT](LICENSE). See [`LICENSE`](LICENSE) for the full text.
