package it.adrian.code.signatures;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinNT;
import it.adrian.code.interfaces.Kernel32;
import it.adrian.code.utilities.ProcessUtil;

public class SignatureManager {

    public WinNT.HANDLE pHandle;
    public String processName;
    public int pid;

    public SignatureManager(WinNT.HANDLE pHandle, String processName, int pid) {
        this.pHandle = pHandle;
        this.processName = processName;
        this.pid = pid;
    }

    public long getPtrFromSignature(Pointer baseAddress, byte[] signaturePtr, String signatureMask) {
        try {
            Tlhelp32.MODULEENTRY32W mod = ProcessUtil.getModule(pid, processName);
            long tempPtr = SignatureUtil.findSignature(pHandle, Pointer.nativeValue(baseAddress), mod.modBaseSize.longValue(), signaturePtr, signatureMask);
            if (tempPtr != 0) {
                int value = SignatureUtil.readInt(pHandle, tempPtr + 3);
                long ptr = tempPtr + value + 7;
                return ptr - Pointer.nativeValue(baseAddress);
            } else {
                System.out.println("Signature not found.");
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(pHandle);
        }
        return 0;
    }
}