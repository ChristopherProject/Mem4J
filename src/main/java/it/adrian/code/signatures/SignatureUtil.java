package it.adrian.code.signatures;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import it.adrian.code.platform.NativeAccess;
import it.adrian.code.platform.ProcessSession;

public class SignatureUtil {

    /**
     * Scan a contiguous region of the target process for {@code sig}/{@code mask},
     * returning the absolute address of the first match, or 0 if none found.
     * The region is read in chunks to handle ranges larger than what fits in a single buffer.
     */
    public static long findSignature(ProcessSession session, long start, long size, byte[] sig, String mask) {
        if (sig.length == 0 || mask.length() != sig.length) {
            return 0L;
        }
        NativeAccess na = NativeAccess.get();
        final int chunk = 1 << 16;
        byte[] buffer = new byte[chunk + sig.length - 1];
        long remaining = size;
        long cursor = start;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining + sig.length - 1);
            if (toRead < sig.length) break;
            if (!na.readMemory(session, cursor, buffer, toRead)) {
                cursor += chunk;
                remaining -= chunk;
                continue;
            }
            int searchLength = toRead - sig.length + 1;
            for (int i = 0; i < searchLength; i++) {
                if (matches(buffer, i, sig, mask)) {
                    return cursor + i;
                }
            }
            cursor += chunk;
            remaining -= chunk;
        }
        return 0L;
    }

    /**
     * @deprecated Use {@link #findSignature(ProcessSession, long, long, byte[], String)}.
     */
    @Deprecated
    public static long findSignature(WinNT.HANDLE pHandle, long start, long size, byte[] sig, String mask) {
        Memory data = new Memory(size);
        IntByReference bytesRead = new IntByReference();
        if (!Kernel32.INSTANCE.ReadProcessMemory(pHandle, new Pointer(start), data, (int) size, bytesRead)) {
            return 0L;
        }
        byte[] window = new byte[sig.length];
        for (long i = 0; i < size; i++) {
            data.read(i, window, 0, sig.length);
            if (matches(window, 0, sig, mask)) {
                return start + i;
            }
        }
        return 0L;
    }

    private static boolean matches(byte[] data, int offset, byte[] sig, String mask) {
        if (data.length - offset < sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if (mask.charAt(i) == 'x' && data[offset + i] != sig[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads a little-endian 32-bit integer at the given absolute address.
     */
    public static int readInt(ProcessSession session, long address) {
        byte[] buf = new byte[4];
        if (!NativeAccess.get().readMemory(session, address, buf, 4)) {
            return 0;
        }
        return ((buf[0] & 0xFF))
                | ((buf[1] & 0xFF) << 8)
                | ((buf[2] & 0xFF) << 16)
                | ((buf[3] & 0xFF) << 24);
    }

    /**
     * @deprecated Use {@link #readInt(ProcessSession, long)}.
     */
    @Deprecated
    public static int readInt(WinNT.HANDLE pHandle, long address) {
        IntByReference intValue = new IntByReference();
        Kernel32.INSTANCE.ReadProcessMemory(pHandle, new Pointer(address), intValue.getPointer(), Integer.SIZE / 8, null);
        return intValue.getValue();
    }
}
