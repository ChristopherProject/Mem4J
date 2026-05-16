package it.adrian.code.memory;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.WinNT;
import it.adrian.code.platform.NativeAccess;
import it.adrian.code.platform.ProcessSession;
import it.adrian.code.platform.windows.WindowsProcessSession;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Pointer {

    private final ProcessSession session;
    public String processName;
    public String moduleName;
    private long baseAddress;
    private long offset;

    public Pointer(ProcessSession session, long baseAddress) {
        this.session = session;
        this.baseAddress = baseAddress;
        this.offset = 0L;
    }

    /**
     * Backward-compatible constructor for Windows callers that already have a
     * {@code WinNT.HANDLE} and a JNA pointer.
     */
    public Pointer(WinNT.HANDLE handle, com.sun.jna.Pointer baseAddress) {
        this(new WindowsProcessSession(0, handle),
                baseAddress == null ? 0L : com.sun.jna.Pointer.nativeValue(baseAddress));
    }

    public static Pointer getBaseAddress(String processName) {
        NativeAccess na = NativeAccess.get();
        int pid = na.findPidByName(processName);
        if (pid == 0) {
            na.abortProcessNotFound(processName);
            return null;
        }
        ProcessSession session = na.openProcess(pid);
        long base = na.getModuleBaseAddress(pid, processName);
        Pointer ptr = new Pointer(session, base);
        ptr.processName = processName;
        ptr.moduleName = processName;
        return ptr;
    }

    /**
     * @deprecated Use {@link #getBaseAddress(String)}; this Windows-only helper
     * is preserved for backward compatibility.
     */
    @Deprecated
    public static com.sun.jna.Pointer getModuleBaseAddress(int pid, String moduleName) {
        long addr = NativeAccess.get().getModuleBaseAddress(pid, moduleName);
        return addr == 0L ? null : new com.sun.jna.Pointer(addr);
    }

    public Pointer add(int val) {
        offset += val;
        return this;
    }

    public long readLong() {
        return ByteBuffer.wrap(read(8)).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    public double readDouble() {
        return ByteBuffer.wrap(read(8)).order(ByteOrder.LITTLE_ENDIAN).getDouble();
    }

    public float readFloat() {
        return ByteBuffer.wrap(read(4)).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    public int readInt() {
        return ByteBuffer.wrap(read(4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private byte[] read(int length) {
        byte[] buffer = new byte[length];
        NativeAccess.get().readMemory(session, baseAddress + offset, buffer, length);
        return buffer;
    }

    public Memory getMemory(int size) {
        byte[] buffer = read(size);
        Memory mem = new Memory(size);
        mem.write(0, buffer, 0, size);
        return mem;
    }

    public boolean writeFloat(float value) {
        byte[] b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
        return NativeAccess.get().writeMemory(session, baseAddress + offset, b, 4);
    }

    public boolean writeDouble(double value) {
        byte[] b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array();
        return NativeAccess.get().writeMemory(session, baseAddress + offset, b, 8);
    }

    public boolean writeLong(long value) {
        byte[] b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
        return NativeAccess.get().writeMemory(session, baseAddress + offset, b, 8);
    }

    public boolean writeInt(int value) {
        byte[] b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
        return NativeAccess.get().writeMemory(session, baseAddress + offset, b, 4);
    }

    public Pointer copy() {
        Pointer ptr = new Pointer(session, baseAddress);
        ptr.offset = offset;
        ptr.moduleName = moduleName;
        ptr.processName = processName;
        return ptr;
    }

    public Pointer indirect64() {
        baseAddress = readLong();
        offset = 0;
        return this;
    }

    public ProcessSession getSession() {
        return session;
    }

    public long getBaseAddressValue() {
        return baseAddress;
    }

    public long getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return moduleName + "[" + String.format("%#08x", baseAddress) + "]+0x"
                + Long.toHexString(offset) + " => 0x" + Long.toHexString(baseAddress + offset);
    }
}
