package it.adrian.code;

import it.adrian.code.interfaces.User32;
import it.adrian.code.memory.Pointer;
import it.adrian.code.utilities.Shell32Util;

public class Memory {

    /**
     * Legge un valore di tipo specificato dalla memoria del processo remoto all'indirizzo ottenuto sommando l'offset specificato all'indirizzo base.
     *
     * @param baseAddr l'indirizzo base a cui aggiungere l'offset per ottenere l'indirizzo finale di lettura.
     * @param offset   l'offset da sommare all'indirizzo base per ottenere l'indirizzo finale di lettura.
     * @param type     il tipo di dato da leggere (Integer, Long o Float).
     * @return il valore letto dalla memoria del processo remoto di tipo specificato.
     * @throws IllegalArgumentException se il tipo di dato specificato non è supportato.
     */
    public static <T> T readMemory(Pointer baseAddr, long offset, Class<T> type) {
        if (!Shell32Util.isUserWindowsAdmin()) {
            User32.INSTANCE.MessageBox(null, "THIS REQUIRE ADMINISTRATION PERMISSIONS", "Warining!?!", User32.MB_OK | User32.MB_ICONWARNING);
            System.exit(-1);
        }
        int offsetAsInt = (int) offset;
        Pointer finalPtr = baseAddr.copy().add(offsetAsInt);

        if (type == Integer.class) {
            return type.cast(finalPtr.readInt());
        } else if (type == Long.class) {
            return type.cast(finalPtr.readLong());
        }else if (type == Double.class) {
            return type.cast(finalPtr.readDouble());
        }
        else if (type == Float.class) {
            return type.cast(finalPtr.readFloat());
        } else {
            throw new IllegalArgumentException("Unsupported data type");
        }
    }

    /**
     * Scrive un valore di tipo specificato nella memoria del processo remoto all'indirizzo ottenuto sommando l'offset specificato all'indirizzo base.
     *
     * @param baseAddr l'indirizzo base a cui aggiungere l'offset per ottenere l'indirizzo finale di scrittura.
     * @param offset   l'offset da sommare all'indirizzo base per ottenere l'indirizzo finale di scrittura.
     * @param value    il valore da scrivere nella memoria del processo remoto.
     * @param type     il tipo di dato del valore da scrivere (Integer, Long, Float o Double).
     * @throws IllegalArgumentException se il tipo di dato specificato non è supportato.
     */
    public static <T> void writeMemory(Pointer baseAddr, long offset, T value, Class<T> type) {
        if (!Shell32Util.isUserWindowsAdmin()) {
            User32.INSTANCE.MessageBox(null, "THIS REQUIRE ADMINISTRATION PERMISSIONS", "Warining!?!", User32.MB_OK | User32.MB_ICONWARNING);
            System.exit(-1);
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