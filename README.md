# Mem4J — Memory Manipulation Library for Java

Mem4J is a Java library that exposes process memory primitives — attaching to a running process, resolving module base addresses, following pointer chains, reading and writing typed values, and locating addresses by byte signatures — entirely from Java, without writing C++ or maintaining a JNI bridge.

It runs on **both Windows and Linux** behind the same `Pointer` / `Memory` API. The platform-specific layer is selected at runtime via a `NativeAccess` abstraction:

- On **Windows** it wraps the Win32 APIs `OpenProcess`, `ReadProcessMemory`, `WriteProcessMemory`, `CreateToolhelp32Snapshot`, `Module32First/NextW`, and `Process32NextW` through [JNA](https://github.com/java-native-access/jna).
- On **Linux** it uses `/proc/<pid>/maps` for module discovery and `/proc/<pid>/mem` for memory I/O. Process lookup is performed via `/proc/<pid>/comm` and the `/proc/<pid>/exe` symlink.

---

## Features

- **Process attachment** — open a handle to a target process by its executable name (`Pointer.getBaseAddress(String)`).
- **Module base resolution** — locate the in-memory base address of a loaded module (PE image) via Tool Help snapshots.
- **Typed read/write** — read and write `int`, `long`, `float`, and `double` directly at an absolute or offset-based address.
- **Pointer chains** — dereference 64-bit pointers and chain offsets (`copy()`, `add()`, `indirect64()`) to follow multi-level pointer paths typical of game/engine internals.
- **Signature (AOB) scanning** — locate an address inside the target's memory using a byte pattern + mask, e.g. `"xx?xx??x"`.
- **Privilege check** — refuses to operate unless the JVM is running with Administrator rights, surfacing a `MessageBox` warning instead of silently failing.

---

## Requirements

| Component         | Version / Note                                                |
|-------------------|---------------------------------------------------------------|
| Java              | **11 or higher** (uses `ProcessHandle`, available since Java 9; project targets Java 11) |
| Operating system  | **Windows** (`kernel32.dll`, `user32.dll`, `shell32.dll`) **or Linux** (`/proc/<pid>/{maps,mem,comm,exe}` + `libc` for `geteuid`) |
| Architecture      | The JVM bitness **must match** the target process. A 32-bit JVM cannot read/write a 64-bit process and vice versa. Use a 64-bit JDK against 64-bit targets. |
| Privileges        | **Windows:** Administrator (checked via `Shell32.IsUserAnAdmin`). **Linux:** `euid == 0` (root) or the JVM granted `CAP_SYS_PTRACE`. The library aborts otherwise. |
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
    implementation 'com.github.christopherproject:Mem4J:1.0.0'
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

```java
import it.adrian.code.Memory;
import it.adrian.code.memory.Pointer;

public class Example {
    public static void main(String[] args) {
        // 1. Attach to the target process by executable name.
        //    Windows: "notepad.exe"; Linux: the binary name as in /proc/<pid>/comm (e.g. "firefox").
        Pointer base = Pointer.getBaseAddress("notepad.exe");

        // 2. Read an int 0x1234 bytes past the module base.
        int value = Memory.readMemory(base, 0x1234L, Integer.class);
        System.out.println("Value at +0x1234 = " + value);

        // 3. Write a new int back to the same location.
        Memory.writeMemory(base, 0x1234L, 42, Integer.class);
    }
}
```

> **Privileges required.** On Windows the library aborts via `MessageBox` and `System.exit(-1)` without Administrator rights. On Linux it prints to stderr and exits unless `euid == 0` or the JVM has `CAP_SYS_PTRACE`.

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

If the process cannot be found the library aborts (MessageBox on Windows, stderr on Linux) and calls `System.exit(-1)`. The returned `Pointer` carries an internal `offset` initialised to `0`.

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

Supported types: `Integer.class`, `Long.class`, `Float.class`, `Double.class`. Any other type throws `IllegalArgumentException`.

Internally each call does `baseAddr.copy().add((int) offset)` so the supplied `base` is not mutated between calls.

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

| Method        | Effect                                                          |
|---------------|-----------------------------------------------------------------|
| `copy()`      | Returns a new `Pointer` with the same handle, base, and offset. Use this before mutating to avoid touching the original. |
| `add(int)`    | Adds bytes to the current offset and returns `this` (mutable, fluent). |
| `indirect64()`| Reads a 64-bit pointer at the current address, replaces the base with that value, and resets the offset to `0`. |
| `toString()`  | Pretty-prints as `module[0xBASE]+0xOFFSET => 0xFINAL`.          |

### Signature (AOB) scanning

> ⚠️ **Windows-only.** `SignatureManager` and `SignatureUtil` are coupled to `WinNT.HANDLE`/`Kernel32.ReadProcessMemory`. The cross-platform `Pointer`/`Memory` APIs above work on Linux; AOB scanning currently does not.

When offsets shift between builds, byte signatures are more stable. `SignatureManager` scans the target module's address range for a pattern and returns the relative offset of the matched address:

```java
import com.sun.jna.platform.win32.WinNT;
import it.adrian.code.interfaces.Kernel32;
import it.adrian.code.signatures.SignatureManager;
import it.adrian.code.utilities.ProcessUtil;

int pid = ProcessUtil.getProcessPidByName("game.exe");
WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(0x0010 | 0x0020 | 0x0008, false, pid);

SignatureManager sm = new SignatureManager(handle, "game.exe", pid);

byte[] pattern = new byte[] {
    (byte) 0x48, (byte) 0x8B, 0x00, 0x00, (byte) 0x05, 0x00, 0x00, 0x00, (byte) 0xC3
};
String mask = "xx??x???x";

Pointer base = Pointer.getBaseAddress("game.exe");
long relativeOffset = sm.getPtrFromSignature(/* JNA pointer to base */ null /* see note */,
                                             pattern, mask);
```

The mask uses `'x'` for "must match exactly" and any other character (typically `'?'`) for "wildcard". `getPtrFromSignature` interprets the matched site as a `mov`/`lea`-style RIP-relative instruction: it reads the 4-byte displacement at `match+3`, then computes `match + displacement + 7`, returning the final address as an offset relative to the module base. The handle is closed at the end of the call.

> ⚠️ The current `SignatureManager` API takes a `com.sun.jna.Pointer` (not the Mem4J `Pointer`) for the module base. You can obtain one from `ProcessUtil.getModule(pid, name).modBaseAddr`.

### Utilities

| Class / method                                  | Platform | Purpose                                                                 |
|-------------------------------------------------|----------|-------------------------------------------------------------------------|
| `NativeAccess.get()`                            | both     | Returns the platform-specific backend (`WindowsAccess` or `LinuxAccess`). |
| `NativeAccess.findPidByName(String)`            | both     | First PID whose executable name matches.                                |
| `NativeAccess.getModuleBaseAddress(pid, name)`  | both     | Base address of a loaded module / mapped binary.                        |
| `NativeAccess.getModuleSize(pid, name)`         | both     | Mapped size of the module (max end − min start across mappings on Linux). |
| `NativeAccess.isPrivileged()`                   | both     | Admin on Windows, `euid == 0` on Linux.                                 |
| `ProcessUtil.getProcessPidByName(String)`       | both     | Thin wrapper around `NativeAccess.findPidByName`.                       |
| `ProcessUtil.getModule(int pid, String name)`   | Windows  | Returns the `MODULEENTRY32W` for the named module (case-insensitive). Throws on Linux. |
| `Shell32Util.isUserWindowsAdmin()`              | Windows  | Returns `true` if the current process has Administrator rights; `false` on Linux. |

---

## API reference (cheat sheet)

```text
Memory
  static <T> T    readMemory(Pointer base, long offset, Class<T> type)
  static <T> void writeMemory(Pointer base, long offset, T value, Class<T> type)

Pointer
  static Pointer  getBaseAddress(String processName)
  static Pointer  getModuleBaseAddress(int pid, String moduleName)   // returns com.sun.jna.Pointer
  Pointer         copy()
  Pointer         add(int bytes)
  Pointer         indirect64()
  int             readInt()        boolean writeInt(int)
  long            readLong()       boolean writeLong(long)
  float           readFloat()      boolean writeFloat(float)
  double          readDouble()     boolean writeDouble(double)

SignatureManager(WinNT.HANDLE pHandle, String processName, int pid)
  long            getPtrFromSignature(com.sun.jna.Pointer base, byte[] sig, String mask)

SignatureUtil
  static long     findSignature(WinNT.HANDLE handle, long start, long size, byte[] sig, String mask)
  static int      readInt(WinNT.HANDLE handle, long address)

ProcessUtil
  static int                   getProcessPidByName(String name)
  static MODULEENTRY32W        getModule(int pid, String name)

Shell32Util
  static boolean               isUserWindowsAdmin()
```

---

## Type sizes

The read/write primitives map to fixed-width writes/reads in the target process, following the [Java Language Specification §4.2.1](https://docs.oracle.com/javase/specs/jls/se11/html/jls-4.html#jls-4.2.1):

| Java type | Bytes written/read |
|-----------|--------------------|
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
- **`indirect64()` assumes a 64-bit pointer.** There is no `indirect32()` variant; on 32-bit targets you would need to extend the API.
- **AOB scanning is Windows-only.** `SignatureManager` / `SignatureUtil` use `WinNT.HANDLE` directly. A cross-platform implementation on top of `NativeAccess` is on the roadmap.
- **The library calls `System.exit(-1)`** on missing privileges or missing process. This is intentional for the typical "trainer" use case but may be inconvenient when embedding Mem4J inside a larger application.

---

## Building from source

```bash
git clone https://github.com/ChristopherProject/Mem4J.git
cd Mem4J
mvn -B package
```

Artifacts land in `target/`. CI runs the same `mvn -B package` on every push to `master` (see [`.github/workflows/maven.yml`](.github/workflows/maven.yml)).

---

## Credits

- **Princekin** — introduced the author to JNA.
- **Foiks** — moral support and early feedback.
- **Backq** — taught the author about memory, offsets, and signatures.

---

## License

No license file is currently bundled with the repository. Until one is added, treat the code as "all rights reserved" by the repository owner. Open an issue if you need a clarification on permitted use.
