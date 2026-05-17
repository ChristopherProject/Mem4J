package it.adrian.code.platform;

/**
 * Cross-platform description of a loaded module / mapped binary.
 * On Windows {@link #path} is the value of {@code MODULEENTRY32W.szExePath}.
 * On Linux it is the pathname taken from {@code /proc/<pid>/maps}.
 */
public final class ModuleInfo {

    private final String name;
    private final String path;
    private final long baseAddress;
    private final long size;

    public ModuleInfo(String name, String path, long baseAddress, long size) {
        this.name = name;
        this.path = path;
        this.baseAddress = baseAddress;
        this.size = size;
    }

    public String name() { return name; }
    public String path() { return path; }
    public long baseAddress() { return baseAddress; }
    public long size() { return size; }

    @Override
    public String toString() {
        return String.format("%s @ 0x%x (%d bytes) %s", name, baseAddress, size, path);
    }
}
