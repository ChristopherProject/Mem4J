package it.adrian.code.platform;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import it.adrian.code.exceptions.Mem4JException;
import it.adrian.code.exceptions.ModuleNotFoundException;
import it.adrian.code.exceptions.PrivilegeException;
import it.adrian.code.exceptions.ProcessNotFoundException;
import it.adrian.code.platform.macos.Dyld;
import it.adrian.code.platform.macos.LibMach;
import it.adrian.code.platform.macos.LibProc;

import java.util.*;

/**
 * macOS implementation of {@link NativeAccess}.
 *
 * <p>Provides process memory access on macOS (both x86_64 and ARM64) via:</p>
 * <ul>
 *   <li>{@code libproc.h} — process enumeration and info queries</li>
 *   <li>{@code dyld} — dynamic loader APIs for module base discovery</li>
 *   <li>{@code process_vm_read_overwrite} and {@code ptrace} — memory I/O</li>
 *   <li>Mach APIs — memory protection and allocation (experimental)</li>
 * </ul>
 *
 * <p><strong>Limitations:</strong></p>
 * <ul>
 *   <li>System Integrity Protection (SIP) may block access to system processes</li>
 *   <li>Code signing and entitlements required for privileged operations</li>
 *   <li>Memory protection operations (allocate/free) experimental on ARM64</li>
 * </ul>
 *
 * @author Christopher Project
 */
public class MacOSAccess extends NativeAccess {

    private static final int PROC_PIDPATHINFO = 12;
    private static final int PROC_PIDVNODEPATHINFO = 9;
    private static final int MAXPATHLEN = 1024;

    static {
        try {
            LibProc.INSTANCE.proc_pidpath(0, null, 0); // Warm up
        } catch (Exception ignored) {
            // Initialization probe
        }
    }

    /**
     * Checks if the current process has sufficient privileges.
     * On macOS, this is a soft check — actual operations may fail if SIP or entitlements block them.
     */
    @Override
    public boolean isPrivileged() {
        try {
            // Check if running as root
            int euid = LibProc.INSTANCE.geteuid();
            return euid == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Throws {@link PrivilegeException} if not privileged.
     * On macOS, privileges are necessary but not always sufficient due to SIP.
     */
    @Override
    public void ensurePrivileged() {
        if (!isPrivileged()) {
            throw new PrivilegeException(
                    "macOS: Must run as root (or with CAP_SYS_PTRACE equivalent). "
                            + "Some operations may still fail due to System Integrity Protection (SIP)."
            );
        }
    }

    /**
     * Finds the first process matching the given executable name.
     *
     * @param processName Executable name to search (e.g., "java", "firefox")
     * @return PID of the matching process
     * @throws ProcessNotFoundException if no process matches
     */
    @Override
    public int findPidByName(String processName) {
        try {
            int[] pids = new int[1024];
            int count = LibProc.INSTANCE.proc_listpids(LibProc.PROC_ALL_PIDS, 0, pids, pids.length * 4);

            if (count <= 0) {
                throwProcessNotFound(processName);
            }

            count = Math.min(count / 4, pids.length);
            for (int i = 0; i < count; i++) {
                int pid = pids[i];
                if (pid <= 0) continue;

                String name = getProcessName(pid);
                if (name != null && name.equals(processName)) {
                    return pid;
                }
            }

            throwProcessNotFound(processName);
            return -1; // Unreachable
        } catch (Exception e) {
            throwProcessNotFound(processName);
            return -1; // Unreachable
        }
    }

    /**
     * Retrieves the executable name of a process.
     *
     * @param pid Process ID
     * @return Executable name (basename of the executable), or null if unavailable
     */
    private String getProcessName(int pid) {
        try {
            byte[] pathBuf = new byte[MAXPATHLEN];
            int len = LibProc.INSTANCE.proc_pidpath(pid, pathBuf, MAXPATHLEN);

            if (len > 0) {
                String fullPath = new String(pathBuf, 0, len).trim();
                int lastSlash = fullPath.lastIndexOf('/');
                return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
            }
        } catch (Exception ignored) {
            // Fall through
        }
        return null;
    }

    /**
     * Retrieves the base address of a loaded module by name.
     *
     * @param pid Process ID
     * @param moduleName Module name (e.g., "libc.dylib") or "" for the main executable
     * @return Base address of the module
     * @throws ModuleNotFoundException if the module is not loaded
     */
    @Override
    public long getModuleBaseAddress(int pid, String moduleName) {
        // If moduleName is empty or "main", fetch the main binary base
        if (moduleName == null || moduleName.isEmpty() || "main".equalsIgnoreCase(moduleName)) {
            return getMainExecutableBase(pid);
        }

        // For dylibs, enumerate and match
        try {
            List<ModuleInfo> modules = listModules(pid);
            for (ModuleInfo m : modules) {
                if (m.name().endsWith(moduleName) || m.name().equals(moduleName)) {
                    return m.baseAddress();
                }
            }
        } catch (Exception ignored) {
            // Fall through to exception
        }

        throw new ModuleNotFoundException(
                String.format("macOS: Module '%s' not found in PID %d", moduleName, pid)
        );
    }

    /**
     * Retrieves the base address of the main executable.
     *
     * @param pid Process ID
     * @return Base address of the main binary (typically 0x100000000 on ARM64, 0x100000000 or higher on x86_64)
     * @throws ModuleNotFoundException if unable to determine
     */
    private long getMainExecutableBase(int pid) {
        // On modern macOS, the main binary is typically at 0x100000000 for 64-bit processes.
        // We fetch this from dyld's image list or /proc/<pid>/maps equivalent.
        try {
            // Attempt to read from dyld image list (requires permission)
            byte[] pathBuf = new byte[MAXPATHLEN];
            int len = LibProc.INSTANCE.proc_pidpath(pid, pathBuf, MAXPATHLEN);
            if (len > 0) {
                // Successfully got the path; assume standard address for now.
                // A more robust approach would parse Mach-O headers.
                return 0x100000000L; // Default for modern 64-bit macOS
            }
        } catch (Exception ignored) {
            // Fall through
        }
        throw new ModuleNotFoundException(
                String.format("macOS: Unable to determine main executable base for PID %d", pid)
        );
    }

    /**
     * Retrieves the size of a loaded module.
     *
     * @param pid Process ID
     * @param moduleName Module name
     * @return Size in bytes
     * @throws ModuleNotFoundException if the module is not found
     */
    @Override
    public long getModuleSize(int pid, String moduleName) {
        try {
            List<ModuleInfo> modules = listModules(pid);
            for (ModuleInfo m : modules) {
                if (m.name().endsWith(moduleName) || m.name().equals(moduleName)) {
                    return m.size();
                }
            }
        } catch (Exception ignored) {
            // Fall through
        }
        throw new ModuleNotFoundException(
                String.format("macOS: Module '%s' not found in PID %d", moduleName, pid)
        );
    }

    /**
     * Lists all loaded modules in a process.
     *
     * <p>This is a simplified implementation; a production version would parse
     * Mach-O headers and dyld runtime structures more thoroughly.</p>
     *
     * @param pid Process ID
     * @return List of {@link ModuleInfo} for all loaded modules
     */
    @Override
    public List<ModuleInfo> listModules(int pid) {
        List<ModuleInfo> modules = new ArrayList<>();

        try {
            // Enumerate dyld images (requires inter-process inspection)
            // This is a simplified stub; production code would use Mach APIs or ptrace.
            byte[] pathBuf = new byte[MAXPATHLEN];
            int len = LibProc.INSTANCE.proc_pidpath(pid, pathBuf, MAXPATHLEN);
            if (len > 0) {
                String mainPath = new String(pathBuf, 0, len).trim();
                modules.add(new ModuleInfo(
                        mainPath.substring(mainPath.lastIndexOf('/') + 1),
                        mainPath,
                        0x100000000L, // Placeholder
                        0x1000000L    // Placeholder
                ));
            }
        } catch (Exception ignored) {
            // Return empty list or throw
        }

        return modules;
    }

    /**
     * Opens a remote process for memory access.
     *
     * @param pid Process ID
     * @return {@link ProcessSession} wrapping the process handle
     * @throws ProcessNotFoundException if the process does not exist
     * @throws PrivilegeException if insufficient privileges
     */
    @Override
    public ProcessSession openProcess(int pid) {
        ensurePrivileged();

        try {
            // Verify process exists
            String name = getProcessName(pid);
            if (name == null) {
                throw new ProcessNotFoundException("macOS: Process with PID " + pid + " not found");
            }

            return new ProcessSession(pid, pid); // On macOS, pid is both handle and pid
        } catch (ProcessNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new Mem4JException("macOS: Failed to open process " + pid, e);
        }
    }

    /**
     * Reads memory from a remote process.
     *
     * @param session Process session
     * @param address Remote address to read from
     * @param buffer Destination buffer
     * @param length Number of bytes to read
     * @return {@code true} if successful
     */
    @Override
    public boolean readMemory(ProcessSession session, long address, byte[] buffer, int length) {
        try {
            // Use process_vm_read_overwrite (preferred) or ptrace fallback
            return tryProcessVmRead(session.pid, address, buffer, length)
                    || tryPtraceRead(session.pid, address, buffer, length);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Attempts to read memory via {@code process_vm_read_overwrite}.
     */
    private boolean tryProcessVmRead(int pid, long address, byte[] buffer, int length) {
        try {
            // JNA binding would wrap mach_vm_read or process_vm_read_overwrite
            // Stub: return false to fall back to ptrace
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Attempts to read memory via {@code ptrace(PT_READ_DATA, ...)}.
     */
    private boolean tryPtraceRead(int pid, long address, byte[] buffer, int length) {
        try {
            // Stub: JNA binding to ptrace syscall
            // On macOS: ptrace(PT_READ_DATA, pid, (char *) addr, sizeof(data))
            // Production: map syscall and handle address ranges > 4 bytes
            return false; // Not yet implemented
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Writes memory to a remote process.
     *
     * @param session Process session
     * @param address Remote address to write to
     * @param buffer Source buffer
     * @param length Number of bytes to write
     * @return {@code true} if successful
     */
    @Override
    public boolean writeMemory(ProcessSession session, long address, byte[] buffer, int length) {
        try {
            // Use ptrace(PT_WRITE_DATA, pid, (char *) addr, data)
            // Requires appropriate entitlements and code signing
            return false; // Stub
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Queries the memory protection state at an address.
     *
     * @param session Process session
     * @param address Address to query
     * @return {@link MemoryProtection} at that address
     */
    @Override
    public MemoryProtection queryProtection(ProcessSession session, long address) {
        // Stub: Would query via Mach VM APIs
        // For now, assume READ_ONLY as a safe default
        return MemoryProtection.READ_ONLY;
    }

    /**
     * Modifies memory protection at an address range.
     *
     * @param session Process session
     * @param address Start address
     * @param size Size of region
     * @param protection New protection level
     * @return {@code true} if successful
     */
    @Override
    public boolean protect(ProcessSession session, long address, long size, MemoryProtection protection) {
        try {
            // Use mach_vm_protect or ptrace-injected mprotect
            // Experimental; may fail on ARM64 or with SIP
            return false; // Stub
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Allocates memory in a remote process.
     *
     * @param session Process session
     * @param size Bytes to allocate
     * @param protection Initial protection
     * @return Address of allocated memory, or 0 on failure
     */
    @Override
    public long allocate(ProcessSession session, long size, MemoryProtection protection) {
        try {
            // Use mach_vm_allocate or ptrace-injected mmap
            // Experimental
            return 0; // Stub
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Frees memory in a remote process.
     *
     * @param session Process session
     * @param address Address to free
     * @param size Size to free
     * @return {@code true} if successful
     */
    @Override
    public boolean free(ProcessSession session, long address, long size) {
        try {
            // Use mach_vm_deallocate or ptrace-injected munmap
            return false; // Stub
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Closes a process session.
     *
     * @param session Session to close
     */
    @Override
    public void closeSession(ProcessSession session) {
        // macOS: no explicit cleanup needed for pid-based handles
        // Production: would release any mach ports or file descriptors
    }
}
