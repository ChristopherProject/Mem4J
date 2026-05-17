package it.adrian.code.platform;

import com.sun.jna.Platform;
import it.adrian.code.exceptions.PrivilegeException;
import it.adrian.code.exceptions.ProcessNotFoundException;

import java.util.List;

public abstract class NativeAccess {

    private static volatile NativeAccess instance;

    public static NativeAccess get() {
        NativeAccess local = instance;
        if (local == null) {
            synchronized (NativeAccess.class) {
                local = instance;
                if (local == null) {
                    local = create();
                    instance = local;
                }
            }
        }
        return local;
    }

    private static NativeAccess create() {
        String backend;
        if (Platform.isWindows()) {
            backend = "it.adrian.code.platform.windows.WindowsAccess";
        } else if (Platform.isLinux()) {
            backend = "it.adrian.code.platform.linux.LinuxAccess";
        } else {
            throw new UnsupportedOperationException(
                    "Mem4J does not support OS: " + System.getProperty("os.name"));
        }
        try {
            return (NativeAccess) Class.forName(backend).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to load Mem4J backend " + backend, e);
        }
    }

    public abstract int findPidByName(String processName);

    public abstract long getModuleBaseAddress(int pid, String moduleName);

    public abstract long getModuleSize(int pid, String moduleName);

    public abstract List<ModuleInfo> listModules(int pid);

    public abstract ProcessSession openProcess(int pid);

    public abstract boolean readMemory(ProcessSession session, long address, byte[] buffer, int length);

    public abstract boolean writeMemory(ProcessSession session, long address, byte[] buffer, int length);

    public abstract boolean protect(ProcessSession session, long address, long size, MemoryProtection protection);

    /**
     * Best-effort query of the current protection at {@code address}.
     * Returns {@code null} if the platform cannot answer.
     */
    public abstract MemoryProtection queryProtection(ProcessSession session, long address);

    public abstract long allocate(ProcessSession session, long size, MemoryProtection protection);

    public abstract boolean free(ProcessSession session, long address, long size);

    public abstract void closeSession(ProcessSession session);

    public abstract boolean isPrivileged();

    /**
     * Throws {@link PrivilegeException} if the JVM does not have the required
     * privileges for read/write/protect/allocate operations. No-op otherwise.
     */
    public void ensurePrivileged() {
        if (!isPrivileged()) {
            throw new PrivilegeException(privilegeErrorMessage());
        }
    }

    protected abstract String privilegeErrorMessage();

    /**
     * Throws {@link ProcessNotFoundException} with the supplied name.
     * Kept as a hook so backends can decorate the exception with extra context.
     */
    public void throwProcessNotFound(String processName) {
        throw new ProcessNotFoundException(processName);
    }
}
