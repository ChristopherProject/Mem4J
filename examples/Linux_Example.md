# Linux Example: Reading /proc/<pid>/maps Equivalent

This example shows how to use Mem4J to read memory from a running process on Linux, mirroring what you'd see in `/proc/<pid>/maps`.

## Prerequisites

- Linux system
- Java process running (e.g., `java -version` in a separate terminal)
- Root privileges OR `CAP_SYS_PTRACE` granted to the JVM

## Setup CAP_SYS_PTRACE (optional, one-time)

```bash
sudo setcap cap_sys_ptrace+ep "$(which java)"
```

This allows the JVM to trace other processes without sudo.

## Code

```java
import it.adrian.code.Memory;
import it.adrian.code.memory.Pointer;
import it.adrian.code.platform.NativeAccess;
import java.util.Scanner;

public class LinuxVMemExample {
    public static void main(String[] args) throws Exception {
        NativeAccess na = NativeAccess.get();
        System.out.println("Backend: " + na.getClass().getSimpleName());
        
        // Find the Java process
        int pid = na.findPidByName("java");
        System.out.println("Found java process: PID " + pid);
        
        // List its modules
        var modules = na.listModules(pid);
        System.out.println("\nLoaded modules:");
        for (var m : modules) {
            System.out.printf("0x%016x - 0x%016x  %s\n",
                m.baseAddress(),
                m.baseAddress() + m.size(),
                m.path());
        }
        
        // Read ELF magic from main binary
        try (Pointer base = Pointer.getBaseAddress("java")) {
            byte[] magic = base.readBytes(4);
            System.out.println("\nELF magic bytes: ");
            for (byte b : magic) {
                System.out.printf("%02x ", b & 0xFF);
            }
            System.out.println();
            if (magic[0] == 0x7f && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F') {
                System.out.println("✓ Valid ELF binary");
            }
        }
    }
}
```

## Run

```bash
# If CAP_SYS_PTRACE is set:
java -cp "target/Mem4J-1.0.0.jar:target/dependency/*:examples" LinuxVMemExample

# Or with sudo:
sudo java -cp "target/Mem4J-1.0.0.jar:target/dependency/*:examples" LinuxVMemExample
```

## Output

```
Backend: LinuxAccess
Found java process: PID 12345

Loaded modules:
0x0000555555554000 - 0x0000555555654000  /usr/bin/java
0x00007ffff7000000 - 0x00007ffff7100000  /lib64/libc-2.31.so
0x00007ffff7200000 - 0x00007ffff7300000  /lib64/libm-2.31.so
...

ELF magic bytes:
7f 45 4c 46
✓ Valid ELF binary
```
