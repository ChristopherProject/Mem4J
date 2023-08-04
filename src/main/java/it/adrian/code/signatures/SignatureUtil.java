package it.adrian.code.signatures;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

public class SignatureUtil {
    public static long findSignature(WinNT.HANDLE pHandle, long start, long size, byte[] sig, String mask) {
        Memory data = new Memory(size);
        IntByReference bytesRead = new IntByReference();

        if (!Kernel32.INSTANCE.ReadProcessMemory(pHandle, new Pointer(start), data, (int) size, bytesRead)) {
            return 0L;
        }

        for (long i = 0; i < size; i++) {
            byte[] buffer = new byte[sig.length];
            data.read(i, buffer, 0, sig.length);
            if (memoryCompare(buffer, sig, mask)) {
                return start + i;
            }
        }
        return 0L;
    }

    private static boolean memoryCompare(byte[] data, byte[] sig, String mask) {
        if (data.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if (mask.charAt(i) == 'x' && data[i] != sig[i]) {
                return false;
            }
        }
        return true;
    }

    public static int readInt(WinNT.HANDLE pHandle, long address) {
        IntByReference intValue = new IntByReference();
        Kernel32.INSTANCE.ReadProcessMemory(pHandle, new Pointer(address), intValue.getPointer(), Integer.SIZE / 8, null);
        return intValue.getValue();
    }
}