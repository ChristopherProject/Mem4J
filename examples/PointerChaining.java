package examples;

import it.adrian.code.Memory;
import it.adrian.code.memory.Pointer;
import it.adrian.code.exceptions.Mem4JException;

/**
 * Pointer chaining example: Follow multi-level indirection (game modding style).
 *
 * <p>Demonstrates:</p>
 * <ul>
 *   <li>Creating a Pointer and copying it</li>
 *   <li>Adding offsets with `add()`</li>
 *   <li>Dereferencing pointers with `indirect64()` and `indirect32()`</li>
 *   <li>Chaining multiple levels of indirection fluently</li>
 *   <li>Reading typed values at the final address</li>
 * </ul>
 *
 * <p>This is a common pattern in reverse engineering and game hacking.
 * Example scenario: following base_ptr -> player_struct -> health_value</p>
 *
 * <p><strong>Platform usage:</strong></p>
 * <ul>
 *   <li><strong>All platforms:</strong> Adjust offsets as needed for your target process</li>
 * </ul>
 *
 * @author Christopher Project
 */
public class PointerChaining {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java PointerChaining <process-name> <base-offset> [level1-offset] [level2-offset]");
            System.err.println("Example: java PointerChaining java 0x10000 0x20 0x30");
            System.err.println();
            System.err.println("This example demonstrates:");
            System.err.println("  1. Attaching to a process");
            System.err.println("  2. Reading a pointer at base+offset");
            System.err.println("  3. Dereferencing that pointer (level1)");
            System.err.println("  4. Dereferencing again (level2)");
            System.err.println("  5. Reading the final value (int)");
            System.exit(1);
        }

        String processName = args[0];
        long baseOffset = Long.decode(args[1]);
        long level1Offset = args.length > 2 ? Long.decode(args[2]) : 0x20L;
        long level2Offset = args.length > 3 ? Long.decode(args[3]) : 0x30L;

        System.out.println("=".repeat(70));
        System.out.println("Mem4J Pointer Chaining Example");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.printf("Target process:   %s%n", processName);
        System.out.printf("Base offset:      0x%x%n", baseOffset);
        System.out.printf("Level1 offset:    0x%x%n", level1Offset);
        System.out.printf("Level2 offset:    0x%x%n", level2Offset);
        System.out.println();

        try {
            // Attach to the process
            System.out.println("[*] Attaching to process...");
            try (Pointer base = Pointer.getBaseAddress(processName)) {
                System.out.printf("[+] Attached! Base: 0x%x%n", base.getBaseAddressValue());
                System.out.println();

                // Build the pointer chain
                System.out.println("[*] Building pointer chain...");
                System.out.printf("    base + 0x%x%n", baseOffset);
                System.out.printf("    -> deref (64-bit pointer)%n");
                System.out.printf("    -> + 0x%x%n", level1Offset);
                System.out.printf("    -> deref (64-bit pointer)%n");
                System.out.printf("    -> + 0x%x%n", level2Offset);
                System.out.println();

                try {
                    Pointer p = base.copy()
                            .add(baseOffset)
                            .indirect64()
                            .add(level1Offset)
                            .indirect64()
                            .add(level2Offset);

                    System.out.printf("[+] Chain built successfully.%n");
                    System.out.printf("[+] Final address: %s%n", p.toString());
                    System.out.println();

                    // Read values at the final address
                    System.out.println("[*] Reading values at final address...");
                    try {
                        int valueInt = Memory.readMemory(p, 0L, Integer.class);
                        System.out.printf("[+] int32 value: 0x%08x (%d)%n", valueInt, valueInt);
                    } catch (Exception e) {
                        System.out.printf("[-] Failed to read int32: %s%n", e.getMessage());
                    }

                    try {
                        long valueLong = Memory.readMemory(p, 8L, Long.class);
                        System.out.printf("[+] int64 value: 0x%016x (%d)%n", valueLong, valueLong);
                    } catch (Exception e) {
                        System.out.printf("[-] Failed to read int64: %s%n", e.getMessage());
                    }

                } catch (Exception e) {
                    System.err.printf("[-] Error during pointer chaining: %s%n", e.getMessage());
                    e.printStackTrace();
                }

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
