package examples;

import it.adrian.code.Memory;
import it.adrian.code.memory.Pointer;
import it.adrian.code.exceptions.Mem4JException;

/**
 * Basic example: Read and write typed values from a remote process.
 *
 * <p>This example demonstrates the fundamental API:</p>
 * <ul>
 *   <li>Attach to a process by name</li>
 *   <li>Read 32-bit and 64-bit integers</li>
 *   <li>Write back modified values</li>
 * </ul>
 *
 * <p><strong>Platform usage:</strong></p>
 * <ul>
 *   <li><strong>Windows:</strong> Run as Administrator. Target can be any user process (e.g., "notepad.exe")</li>
 *   <li><strong>Linux:</strong> Run as root or with CAP_SYS_PTRACE. Target can be any process (e.g., "java")</li>
 *   <li><strong>macOS:</strong> Run as root or with task_for_pid entitlements. Target can be any user process (e.g., "java")</li>
 * </ul>
 *
 * @author Christopher Project
 */
public class BasicReadWrite {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java BasicReadWrite <process-name> [offset]");
            System.err.println("Example: java BasicReadWrite java 0x1000");
            System.exit(1);
        }

        String processName = args[0];
        long offset = args.length > 1 ? Long.decode(args[1]) : 0x1000L;

        System.out.println("=".repeat(70));
        System.out.println("Mem4J Basic Read/Write Example");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.printf("Target process: %s%n", processName);
        System.out.printf("Target offset:  0x%x%n", offset);
        System.out.println();

        try {
            // Attach to the process by name
            System.out.println("[*] Attaching to process...");
            try (Pointer base = Pointer.getBaseAddress(processName)) {
                System.out.printf("[+] Attached! Base address: 0x%x%n", base.getBaseAddressValue());
                System.out.println();

                // Read a 32-bit integer
                System.out.printf("[*] Reading int32 at offset 0x%x...%n", offset);
                try {
                    int value32 = Memory.readMemory(base, offset, Integer.class);
                    System.out.printf("[+] Read int32: 0x%08x (%d)%n", value32, value32);
                } catch (Exception e) {
                    System.out.printf("[-] Failed to read int32: %s%n", e.getMessage());
                }

                System.out.println();

                // Read a 64-bit integer
                System.out.printf("[*] Reading int64 at offset 0x%x...%n", offset + 8);
                try {
                    long value64 = Memory.readMemory(base, offset + 8, Long.class);
                    System.out.printf("[+] Read int64: 0x%016x (%d)%n", value64, value64);
                } catch (Exception e) {
                    System.out.printf("[-] Failed to read int64: %s%n", e.getMessage());
                }

                System.out.println();

                // Read a float
                System.out.printf("[*] Reading float at offset 0x%x...%n", offset + 16);
                try {
                    float valueFloat = Memory.readMemory(base, offset + 16, Float.class);
                    System.out.printf("[+] Read float: %f%n", valueFloat);
                } catch (Exception e) {
                    System.out.printf("[-] Failed to read float: %s%n", e.getMessage());
                }

                System.out.println();

                // Demonstrate write (commented out for safety)
                System.out.println("[*] Write example (skipped for safety):");
                System.out.printf("    Memory.writeMemory(base, 0x%x, 42, Integer.class);%n", offset);
                System.out.printf("    Memory.writeMemory(base, 0x%x, 0x1234567890ABCDEFL, Long.class);%n", offset + 8);
                System.out.println();

                System.out.println("[+] Done!");
            }
        } catch (Mem4JException e) {
            System.err.printf("[-] Mem4J Error: %s%n", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
