package it.adrian.code.memory;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.WinNT;
import it.adrian.code.exceptions.MemoryAccessException;
import it.adrian.code.exceptions.ModuleNotFoundException;
import it.adrian.code.exceptions.ProcessNotFoundException;
import it.adrian.code.platform.MemoryProtection;
import it.adrian.code.platform.NativeAccess;
import it.adrian.code.platform.ProcessSession;
import it.adrian.code.platform.windows.WindowsProcessSession;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Pointer implements AutoCloseable {

    private final ProcessSession session;
    public String processName;
    public String moduleName;
    private long baseAddress;
    private long offset;
    private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    private boolean forceWrite;

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

    /**
     * Attach to the named process and resolve the base address of its main module.
     *
     * @throws ProcessNotFoundException if no process matches the given name.
     * @throws ModuleNotFoundException  if the process is found but its main module cannot be resolved.
     */
    public static Pointer getBaseAddress(String processName) {
        NativeAccess na = NativeAccess.get();
        int pid = na.findPidByName(processName);
        if (pid == 0) {
            throw new ProcessNotFoundException(processName);
        }
        ProcessSession session = na.openProcess(pid);
        long base = na.getModuleBaseAddress(pid, processName);
        if (base == 0L) {
            na.closeSession(session);
            throw new ModuleNotFoundException(processName);
        }
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

    public Pointer withByteOrder(ByteOrder order) {
        this.byteOrder = order;
        return this;
    }

    public ByteOrder byteOrder() {
        return byteOrder;
    }

    /**
     * Returns a sibling {@code Pointer} whose writes go through the
     * protect&rarr;write&rarr;restore dance, so they succeed even against
     * read-only or executable pages (e.g. patching {@code .text}).
     * <p>
     * On Windows this temporarily flips the affected pages to
     * {@code PAGE_EXECUTE_READWRITE} and restores their previous protection
     * after the write. On Linux {@code /proc/<pid>/mem} already ignores
     * page protection when the JVM has {@code CAP_SYS_PTRACE}, so this
     * call is a no-op.
     */
    public Pointer force() {
        Pointer p = copy();
        p.forceWrite = true;
        return p;
    }

    public Pointer add(long val) {
        offset += val;
        return this;
    }

    public Pointer add(int val) {
        return add((long) val);
    }

    public long readLong() {
        return ByteBuffer.wrap(read(8)).order(byteOrder).getLong();
    }

    public double readDouble() {
        return ByteBuffer.wrap(read(8)).order(byteOrder).getDouble();
    }

    public float readFloat() {
        return ByteBuffer.wrap(read(4)).order(byteOrder).getFloat();
    }

    public int readInt() {
        return ByteBuffer.wrap(read(4)).order(byteOrder).getInt();
    }

    public short readShort() {
        return ByteBuffer.wrap(read(2)).order(byteOrder).getShort();
    }

    public byte readByte() {
        return read(1)[0];
    }

    public byte[] readBytes(int length) {
        return read(length);
    }

    /**
     * Reads up to {@code maxBytes} bytes and decodes them as a string in the
     * given charset, stopping at the first NUL terminator if any.
     */
    public String readString(int maxBytes, Charset charset) {
        byte[] raw = read(maxBytes);
        int end = raw.length;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0) {
                end = i;
                break;
            }
        }
        return new String(raw, 0, end, charset);
    }

    public String readString(int maxBytes) {
        return readString(maxBytes, StandardCharsets.UTF_8);
    }

    private byte[] read(int length) {
        byte[] buffer = new byte[length];
        if (!NativeAccess.get().readMemory(session, baseAddress + offset, buffer, length)) {
            throw new MemoryAccessException(
                    "Read of " + length + " bytes at 0x" + Long.toHexString(baseAddress + offset) + " failed");
        }
        return buffer;
    }

    public Memory getMemory(int size) {
        byte[] buffer = read(size);
        Memory mem = new Memory(size);
        mem.write(0, buffer, 0, size);
        return mem;
    }

    public boolean writeFloat(float value) {
        return write(ByteBuffer.allocate(4).order(byteOrder).putFloat(value).array());
    }

    public boolean writeDouble(double value) {
        return write(ByteBuffer.allocate(8).order(byteOrder).putDouble(value).array());
    }

    public boolean writeLong(long value) {
        return write(ByteBuffer.allocate(8).order(byteOrder).putLong(value).array());
    }

    public boolean writeInt(int value) {
        return write(ByteBuffer.allocate(4).order(byteOrder).putInt(value).array());
    }

    public boolean writeShort(short value) {
        return write(ByteBuffer.allocate(2).order(byteOrder).putShort(value).array());
    }

    public boolean writeByte(byte value) {
        return write(new byte[]{value});
    }

    public boolean writeBytes(byte[] data) {
        return write(data);
    }

    public boolean writeString(String value, Charset charset) {
        return write(value.getBytes(charset));
    }

    public boolean writeString(String value) {
        return writeString(value, StandardCharsets.UTF_8);
    }

    private boolean write(byte[] data) {
        NativeAccess na = NativeAccess.get();
        long address = baseAddress + offset;
        if (!forceWrite || !com.sun.jna.Platform.isWindows()) {
            return na.writeMemory(session, address, data, data.length);
        }
        MemoryProtection original = na.queryProtection(session, address);
        boolean flipped = na.protect(session, address, data.length, MemoryProtection.READ_WRITE_EXECUTE);
        try {
            return na.writeMemory(session, address, data, data.length);
        } finally {
            if (flipped && original != null) {
                na.protect(session, address, data.length, original);
            }
        }
    }

    /**
     * Change the protection of {@code size} bytes starting at the current address.
     * <p>
     * Windows-only — Linux throws {@link UnsupportedOperationException}.
     */
    public boolean protect(long size, MemoryProtection protection) {
        return NativeAccess.get().protect(session, baseAddress + offset, size, protection);
    }

    public Pointer copy() {
        Pointer ptr = new Pointer(session, baseAddress);
        ptr.offset = offset;
        ptr.moduleName = moduleName;
        ptr.processName = processName;
        ptr.byteOrder = byteOrder;
        ptr.forceWrite = forceWrite;
        return ptr;
    }

    public Pointer indirect64() {
        baseAddress = readLong();
        offset = 0;
        return this;
    }

    /**
     * Dereference a 32-bit pointer at the current address (useful when attached
     * to a 32-bit target). The value is zero-extended to 64 bits.
     */
    public Pointer indirect32() {
        baseAddress = ((long) readInt()) & 0xFFFFFFFFL;
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

    /**
     * Release the underlying OS handle ({@code CloseHandle} on Windows,
     * closing of {@code /proc/<pid>/mem} on Linux).
     */
    @Override
    public void close() {
        NativeAccess.get().closeSession(session);
    }

    @Override
    public String toString() {
        return moduleName + "[" + String.format("%#08x", baseAddress) + "]+0x"
                + Long.toHexString(offset) + " => 0x" + Long.toHexString(baseAddress + offset);
    }
}
