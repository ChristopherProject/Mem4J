package it.adrian.code;

import it.adrian.code.memory.Pointer;
import it.adrian.code.platform.NativeAccess;

/**
 * Convenience facade for typed reads and writes against a remote process.
 * Equivalent to {@code baseAddr.copy().add(offset).read*()} / {@code .write*()}.
 */
public class Memory {

    /**
     * Reads a value of the specified type from the remote process at
     * {@code baseAddr + offset}.
     *
     * @param baseAddr base pointer obtained via {@link Pointer#getBaseAddress(String)}.
     * @param offset   byte offset from the base address. The full {@code long} range is honoured.
     * @param type     {@code Integer.class}, {@code Long.class}, {@code Float.class} or {@code Double.class}.
     * @return the value read from the remote process.
     * @throws IllegalArgumentException if the type is unsupported.
     * @throws it.adrian.code.exceptions.PrivilegeException    if the JVM lacks the required privileges.
     * @throws it.adrian.code.exceptions.MemoryAccessException if the underlying read failed.
     */
    public static <T> T readMemory(Pointer baseAddr, long offset, Class<T> type) {
        NativeAccess.get().ensurePrivileged();
        Pointer finalPtr = baseAddr.copy().add(offset);

        if (type == Integer.class) {
            return type.cast(finalPtr.readInt());
        } else if (type == Long.class) {
            return type.cast(finalPtr.readLong());
        } else if (type == Double.class) {
            return type.cast(finalPtr.readDouble());
        } else if (type == Float.class) {
            return type.cast(finalPtr.readFloat());
        } else if (type == Short.class) {
            return type.cast(finalPtr.readShort());
        } else if (type == Byte.class) {
            return type.cast(finalPtr.readByte());
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type);
        }
    }

    /**
     * Writes a value of the specified type to the remote process at
     * {@code baseAddr + offset}.
     *
     * @param baseAddr base pointer obtained via {@link Pointer#getBaseAddress(String)}.
     * @param offset   byte offset from the base address. The full {@code long} range is honoured.
     * @param value    value to write.
     * @param type     {@code Integer.class}, {@code Long.class}, {@code Float.class} or {@code Double.class}.
     * @throws IllegalArgumentException if the type is unsupported.
     * @throws it.adrian.code.exceptions.PrivilegeException    if the JVM lacks the required privileges.
     */
    public static <T> void writeMemory(Pointer baseAddr, long offset, T value, Class<T> type) {
        NativeAccess.get().ensurePrivileged();
        Pointer finalPtr = baseAddr.copy().add(offset);

        if (type == Integer.class) {
            finalPtr.writeInt((Integer) value);
        } else if (type == Long.class) {
            finalPtr.writeLong((Long) value);
        } else if (type == Float.class) {
            finalPtr.writeFloat((Float) value);
        } else if (type == Double.class) {
            finalPtr.writeDouble((Double) value);
        } else if (type == Short.class) {
            finalPtr.writeShort((Short) value);
        } else if (type == Byte.class) {
            finalPtr.writeByte((Byte) value);
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type);
        }
    }
}
