# macOS Example: Reading Mach-O Headers

This example demonstrates reading process memory on macOS, including Mach-O binary headers and module enumeration.

## Prerequisites

- macOS 10.13+
- x86_64 or ARM64 (Apple Silicon)
- Root privileges OR `task_for_pid` entitlements
- Java process running

## Setup Entitlements (Optional)

To avoid needing `sudo`, create an entitlements file:

```xml
<!-- entitlements.plist -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.task_for_pid-allow</key>
    <true/>
</dict>
</plist>
```

Then code sign the Java binary:

```bash
codesign -s - --entitlements entitlements.plist /usr/libexec/java_home/bin/java
```

(Note: This requires disabling System Integrity Protection or using a self-signed certificate.)

## Code

```java
import it.adrian.code.Memory;
import it.adrian.code.memory.Pointer;
import it.adrian.code.platform.NativeAccess;
import java.nio.ByteOrder;

public class macOSMachoExample {
    public static void main(String[] args) throws Exception {
        NativeAccess na = NativeAccess.get();
        System.out.println("Backend: " + na.getClass().getSimpleName());
        System.out.println("Privileged: " + (na.isPrivileged() ? "Yes" : "No"));
        System.out.println();
        
        // Find and attach to Java process
        int pid = na.findPidByName("java");
        System.out.println("Found java process: PID " + pid);
        
        try (Pointer base = Pointer.getBaseAddress("java")) {
            System.out.println("Attached at: 0x" + Long.toHexString(base.getBaseAddressValue()));
            System.out.println();
            
            // Read Mach-O header (first 32 bytes)
            System.out.println("Reading Mach-O header...");
            byte[] header = base.readBytes(32);
            
            // Parse Mach-O magic
            int magic = ByteOrder.LITTLE_ENDIAN.equals(ByteOrder.nativeOrder())
                ? (header[3] << 24) | (header[2] << 16) | (header[1] << 8) | header[0]
                : (header[0] << 24) | (header[1] << 16) | (header[2] << 8) | header[3];
            
            System.out.printf("Magic: 0x%08x", magic);
            if (magic == 0xfeedfacf) {
                System.out.println(" (Mach-O 64-bit little-endian)");
            } else if (magic == 0xcffaedfe) {
                System.out.println(" (Mach-O 64-bit big-endian)");
            } else if (magic == 0xfeedfacb) {
                System.out.println(" (Mach-O 32-bit little-endian)");
            } else {
                System.out.println(" (Unknown)");
            }
            
            // List modules
            System.out.println();
            System.out.println("Loaded modules:");
            var modules = na.listModules(pid);
            for (var m : modules) {
                System.out.printf("  0x%016x - 0x%016x  %s%n",
                    m.baseAddress(),
                    m.baseAddress() + m.size(),
                    m.path());
            }
        }
    }
}
```

## Run

```bash
# With sudo:
sudo java -cp "target/Mem4J-1.0.0.jar:target/dependency/*:examples" macOSMachoExample

# Or with entitlements (no sudo needed):
java -cp "target/Mem4J-1.0.0.jar:target/dependency/*:examples" macOSMachoExample
```

## Output

```
Backend: MacOSAccess
Privileged: Yes

Found java process: PID 12345
Attached at: 0x100000000

Reading Mach-O header...
Magic: 0xfeedfacf (Mach-O 64-bit little-endian)

Loaded modules:
  0x0000000100000000 - 0x0000000105000000  /usr/libexec/java_home/bin/java
  0x0000000105000000 - 0x0000000105800000  /usr/lib/system/libSystem.dylib
  0x0000000105800000 - 0x0000000105900000  /usr/lib/libobjc.A.dylib
  ...
```

## System Integrity Protection (SIP)

If you get "Permission denied" errors even as root:

1. SIP may be blocking access to certain processes
2. Disable SIP for testing:
   ```bash
   # Boot into Recovery Mode (Cmd+R on startup)
   # In Terminal: csrutil disable
   # Reboot normally
   ```
3. Re-enable after testing:
   ```bash
   # Boot into Recovery Mode again
   # In Terminal: csrutil enable
   ```

⚠️ Disabling SIP reduces security. Only do this for development/testing.
