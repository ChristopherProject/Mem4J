package examples;

import it.adrian.code.platform.NativeAccess;
import it.adrian.code.platform.ModuleInfo;
import it.adrian.code.exceptions.Mem4JException;
import java.util.List;

/**
 * Process enumeration example: List all processes and modules.
 *
 * <p>Demonstrates:</p>
 * <ul>
 *   <li>Finding a process by name (case-sensitive)</li>
 *   <li>Listing all loaded modules in a process</li>
 *   <li>Printing module metadata (name, path, base, size)</li>
 * </ul>
 *
 * <p><strong>Platform usage:</strong></p>
 * <ul>
 *   <li><strong>Windows:</strong> Run as Administrator</li>
 *   <li><strong>Linux:</strong> Run as root (optional; restricted modules may not be visible)</li>
 *   <li><strong>macOS:</strong> Run as root (optional; system processes may be blocked by SIP)</li>
 * </ul>
 *
 * @author Christopher Project
 */
public class ProcessEnumeration {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java ProcessEnumeration <process-name>");
            System.err.println("Example: java ProcessEnumeration java");
            System.exit(1);
        }

        String processName = args[0];

        System.out.println("=".repeat(70));
        System.out.println("Mem4J Process Enumeration Example");
        System.out.println("=".repeat(70));
        System.out.println();

        try {
            NativeAccess na = NativeAccess.get();
            System.out.printf("[+] Backend: %s%n", na.getClass().getSimpleName());
            System.out.printf("[+] Privileged: %s%n", na.isPrivileged() ? "Yes" : "No (some info may be restricted)");
            System.out.println();

            // Find the process
            System.out.printf("[*] Searching for process: \"%s\"%n", processName);
            int pid = na.findPidByName(processName);
            System.out.printf("[+] Found process! PID: %d%n", pid);
            System.out.println();

            // List modules
            System.out.printf("[*] Enumerating modules in PID %d...%n", pid);
            List<ModuleInfo> modules = na.listModules(pid);
            System.out.printf("[+] Found %d module(s):%n", modules.size());
            System.out.println();

            // Print table header
            printTableHeader();
            System.out.println("-".repeat(135));

            // Print each module
            for (int i = 0; i < modules.size(); i++) {
                ModuleInfo m = modules.get(i);
                System.out.printf(
                        "%3d | 0x%016x | 0x%08x | %-25s | %s%n",
                        i + 1,
                        m.baseAddress(),
                        m.size(),
                        truncate(m.name(), 25),
                        truncate(m.path(), 50)
                );
            }

            System.out.println("-".repeat(135));
            System.out.printf("[+] Total modules: %d%n", modules.size());
            System.out.println();
            System.out.println("[+] Done!");
        } catch (Mem4JException e) {
            System.err.printf("[-] Error: %s%n", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printTableHeader() {
        System.out.printf(
                "%-3s | %-16s | %-8s | %-25s | %s%n",
                "#",
                "Base Address",
                "Size",
                "Name",
                "Path"
        );
    }

    private static String truncate(String s, int len) {
        if (s == null) return "(null)";
        if (s.length() <= len) return s;
        return s.substring(s.length() - len);
    }
}
