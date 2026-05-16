package it.adrian.code.platform;

import com.sun.jna.Platform;

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

    public abstract ProcessSession openProcess(int pid);

    public abstract boolean readMemory(ProcessSession session, long address, byte[] buffer, int length);

    public abstract boolean writeMemory(ProcessSession session, long address, byte[] buffer, int length);

    public abstract void closeSession(ProcessSession session);

    public abstract boolean isPrivileged();

    public abstract void abortMissingPrivileges();

    public abstract void abortProcessNotFound(String processName);
}
