# Mem4J Examples

This directory contains runnable examples demonstrating the core Mem4J API across all supported platforms (Windows, Linux, macOS).

## Prerequisites

- **Java 11+** installed and in PATH
- **Maven** installed (to build Mem4J)
- **Privileges:**
  - **Windows:** Run as Administrator
  - **Linux:** Run as root, or grant JVM `CAP_SYS_PTRACE`
  - **macOS:** Run as root, or obtain `task_for_pid` entitlements

## Building

```bash
cd /path/to/Mem4J
mvn -B package
```

This produces `target/Mem4J-1.0.0.jar` and other artifacts.

## Running Examples

All examples are designed to be compiled and run together with the Mem4J library.

### 1. BasicReadWrite

Reads and writes typed values (int, long, float) from a remote process.

```bash
# Compile
javac -cp "target/Mem4J-1.0.0.jar:target/dependency/*" examples/BasicReadWrite.java

# Run (target the current JVM, or any running process)
java -cp "target/Mem4J-1.0.0.jar:target/dependency/*:examples" BasicReadWrite java 0x1000
```

**Output:**
```
======================================================================
Mem4J Basic Read/Write Example
======================================================================

Target process: java
Target offset:  0x1000

[*] Attaching to process...
[+] Attached! Base address: 0x100000000

[*] Reading int32 at offset 0x1000...
[+] Read int32: 0xfeedbeef (4277009135)

[*] Reading int64 at offset 0x1008...
[+] Read int64: 0x1234567890abcdef (1311768467463495151)

[*] Reading float at offset 0x1010...
[+] Read float: 3.141593

[*] Write example (skipped for safety):
    Memory.writeMemory(base, 0x1000, 42, Integer.class);
    Memory.writeMemory(base, 0x1008, 0x1234567890ABCDEFL, Long.class);

[+] Done!
```

### 2. ProcessEnumeration

Lists all processes and their loaded modules (DLLs, dylibs, .so files).

```bash
# Compile
javac -cp "target/Mem4J-1.0.0.jar:target/dependency/*" examples/ProcessEnumeration.java

# Run
java -cp "target/Mem4J-1.0.0.jar:target/dependency/*:examples" ProcessEnumeration java
```

**Output:**
```
======================================================================
Mem4J Process Enumeration Example
======================================================================

[+] Backend: MacOSAccess
[+] Privileged: Yes

[*] Searching for process: "java"...
[+] Found process! PID: 12345

[*] Enumerating modules in PID 12345...
[+] Found 42 module(s):

  # |    Base Address |     Size | Name                  | Path
--- | 0x0000000100000000 | 0x1000000 | java                  | /usr/libexec/java_home/bin/java
  2 | 0x0000000102000000 | 0x0800000 | libjvm.dylib          | /usr/libexec/java_home/lib/libjvm.dylib
  3 | 0x0000000102800000 | 0x0100000 | libc++.1.dylib        | /usr/lib/libc++.1.dylib
 ...
--- | Total modules: 42

[+] Done!
```

### 3. PointerChaining

Follows multi-level pointers (base pointer -> level1 -> level2 -> value). Common in game modding and reverse engineering.

```bash
# Compile
javac -cp "target/Mem4J-1.0.0.jar:target/dependency/*" examples/PointerChaining.java

# Run (with custom offsets)
java -cp "target/Mem4J-1.0.0.jar:target/dependency/*:examples" PointerChaining java 0x10000 0x20 0x30
```

**Output:**
```
======================================================================
Mem4J Pointer Chaining Example
======================================================================

Target process:   java
Base offset:      0x10000
Level1 offset:    0x20
Level2 offset:    0x30

[*] Attaching to process...
[+] Attached! Base: 0x100000000

[*] Building pointer chain...
    base + 0x10000
    -> deref (64-bit pointer)
    -> + 0x20
    -> deref (64-bit pointer)
    -> + 0x30

[+] Chain built successfully.
[+] Final address: java[0x100000000]+0x10000 => 0x108765430

[*] Reading values at final address...
[+] int32 value: 0x00000042 (66)
[+] int64 value: 0x0000000000001234 (4660)

[+] Done!
```

## Platform-Specific Examples

- **[Windows_Example.md](Windows_Example.md)** — Modifying Notepad text via memory patching
- **[Linux_Example.md](Linux_Example.md)** — Reading ELF headers and /proc/<pid>/maps equivalent
- **[macOS_Example.md](macOS_Example.md)** — Reading Mach-O headers and handling SIP

## Platform-Specific Setup

### Windows

```bash
# Compile
javac -cp "target\Mem4J-1.0.0.jar;target\dependency\*" examples\BasicReadWrite.java

# Run as Administrator (elevated prompt)
java -cp "target\Mem4J-1.0.0.jar;target\dependency\*;examples" BasicReadWrite notepad.exe 0x1000
```

### Linux

```bash
# Grant CAP_SYS_PTRACE to JVM (one-time)
sudo setcap cap_sys_ptrace+ep "$(which java)"

# Compile
javac -cp "target/Mem4J-1.0.0.jar:target/dependency/*" examples/BasicReadWrite.java

# Run (as regular user if CAP set, or with sudo)
java -cp "target/Mem4J-1.0.0.jar:target/dependency/*:examples" BasicReadWrite java 0x1000
```

### macOS

```bash
# Run as root
sudo java -cp "target/Mem4J-1.0.0.jar:target/dependency/*:examples" BasicReadWrite java 0x1000

# Or obtain entitlements (requires Developer ID certificate)
codesign -s - --entitlements entitlements.plist /path/to/java
java -cp "target/Mem4J-1.0.0.jar:target/dependency/*:examples" BasicReadWrite java 0x1000
```

## Troubleshooting

### "Process not found" or "Module not found"

- Verify the process name is correct (case-sensitive on Linux/macOS)
- On Linux/macOS, check `/proc/<pid>/comm` or use `ps` to see exact names
- Example: "java" finds Java processes; "notepad" on Linux won't work (use full command name)

### "Permission denied" or "Privilege Exception"

- **Windows:** Right-click command prompt, "Run as administrator"
- **Linux:** Run `sudo java ...` or grant CAP_SYS_PTRACE: `sudo setcap cap_sys_ptrace+ep $(which java)`
- **macOS:** Run `sudo java ...` or code sign with entitlements

### "Access denied" (Windows) or "Operation not permitted" (macOS)

- System processes (kernel, drivers) or sandboxed apps may be unreachable
- On macOS, SIP may block access even to some user processes
- Try attaching to processes you own (e.g., your own Java process)

### ClassNotFoundException or NoSuchMethodError

- Ensure all JNA JAR files are in the classpath: `-cp "target/Mem4J-1.0.0.jar:target/dependency/*"`
- Check that you're using the correct version of JNA (5.12.1)

## Writing Your Own Examples

Basic template:

```java
import it.adrian.code.Memory;
import it.adrian.code.memory.Pointer;
import it.adrian.code.platform.NativeAccess;

public class MyExample {
    public static void main(String[] args) throws Exception {
        NativeAccess na = NativeAccess.get();
        System.out.println("Using backend: " + na.getClass().getSimpleName());
        
        // Attach to a process
        try (Pointer base = Pointer.getBaseAddress("java")) {
            // Read a value
            int value = Memory.readMemory(base, 0x1000L, Integer.class);
            System.out.println("Value: " + value);
            
            // Write a value (be careful!)
            // Memory.writeMemory(base, 0x1000L, 42, Integer.class);
        }
    }
}
```

## References

- [Mem4J README](../README.md) — Full API documentation
- [JNA Documentation](https://github.com/java-native-access/jna/wiki)
- Game modding tutorials often demonstrate similar pointer-chasing patterns
