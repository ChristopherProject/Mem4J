package it.adrian.code;

import it.adrian.code.memory.Pointer;
import it.adrian.code.platform.NativeAccess;

public class Memory {

    /**
     * Reads a value of the specified type from the remote process at
     * {@code baseAddr + offset}.
     *
     * @param baseAddr the base pointer obtained via {@link Pointer#getBaseAddress(String)}.
     * @param offset   byte offset from the base address.
     * @param type     {@code Integer.class}, {@code Long.class}, {@code Float.class} or {@code Double.class}.
     * @return the value read from the remote process.
     * @throws IllegalArgumentException if the type is unsupported.
     */
    public static <T> T readMemory(Pointer baseAddr, long offset, Class<T> type) {
        NativeAccess na = NativeAccess.get();
        if (!na.isPrivileged()) {
            na.abortMissingPrivileges();
        }
        int offsetAsInt = (int) offset;
        Pointer finalPtr = baseAddr.copy().add(offsetAsInt);

        if (type == Integer.class) {
            return type.cast(finalPtr.readInt());
        } else if (type == Long.class) {
            return type.cast(finalPtr.readLong());
        } else if (type == Double.class) {
            return type.cast(finalPtr.readDouble());
        } else if (type == Float.class) {
            return type.cast(finalPtr.readFloat());
        } else {
            throw new IllegalArgumentException("Unsupported data type");
        }
    }

    /**
     * Writes a value of the specified type to the remote process at
     * {@code baseAddr + offset}.
     *
     * @param baseAddr the base pointer obtained via {@link Pointer#getBaseAddress(String)}.
     * @param offset   byte offset from the base address.
     * @param value    value to write.
     * @param type     {@code Integer.class}, {@code Long.class}, {@code Float.class} or {@code Double.class}.
     * @throws IllegalArgumentException if the type is unsupported.
     */
    public static <T> void writeMemory(Pointer baseAddr, long offset, T value, Class<T> type) {
        NativeAccess na = NativeAccess.get();
        if (!na.isPrivileged()) {
            na.abortMissingPrivileges();
        }
        int offsetAsInt = (int) offset;
        Pointer finalPtr = baseAddr.copy().add(offsetAsInt);

        if (type == Integer.class) {
            finalPtr.writeInt((Integer) value);
        } else if (type == Long.class) {
            finalPtr.writeLong((Long) value);
        } else if (type == Float.class) {
            finalPtr.writeFloat((Float) value);
        } else if (type == Double.class) {
            finalPtr.writeDouble((Double) value);
        } else {
            throw new IllegalArgumentException("Unsupported data type");
        }
    }
}
