package it.adrian.code.signatures;

import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinNT;
import it.adrian.code.exceptions.ModuleNotFoundException;
import it.adrian.code.memory.Pointer;
import it.adrian.code.platform.ModuleInfo;
import it.adrian.code.platform.NativeAccess;
import it.adrian.code.platform.ProcessSession;
import it.adrian.code.platform.windows.WindowsProcessSession;
import it.adrian.code.utilities.ProcessUtil;

/**
 * Cross-platform AOB scanner. Decodes the matched site as a RIP-relative
 * {@code mov}/{@code lea}-style instruction (4-byte displacement at {@code match+3},
 * resolving to {@code match + displacement + 7}) and returns the result
 * relative to the supplied module base.
 * <p>
 * Unlike the original Windows-only constructor, this version does not close
 * the underlying handle when the scan completes — the caller controls the
 * lifecycle of the {@link Pointer}/{@link ProcessSession}.
 */
public class SignatureManager {

    private final ProcessSession session;
    private final String moduleName;
    private final int pid;

    public SignatureManager(Pointer pointer) {
        this(pointer.getSession(), pointer.moduleName);
    }

    public SignatureManager(ProcessSession session, String moduleName) {
        this.session = session;
        this.moduleName = moduleName;
        this.pid = session.pid;
    }

    /**
     * @deprecated Use {@link #SignatureManager(ProcessSession, String)} or
     * {@link #SignatureManager(Pointer)} instead. This constructor wraps the
     * given handle in a {@link WindowsProcessSession} and is Windows-only.
     */
    @Deprecated
    public SignatureManager(WinNT.HANDLE pHandle, String processName, int pid) {
        this(new WindowsProcessSession(pid, pHandle), processName);
    }

    /**
     * Scan the module identified at construction for {@code signature}/{@code mask}
     * and return the offset of the resolved address relative to the module base.
     */
    public long getPtrFromSignature(long moduleBaseAddress, byte[] signaturePtr, String signatureMask) {
        long moduleSize = resolveModuleSize();
        long tempPtr = SignatureUtil.findSignature(session, moduleBaseAddress, moduleSize, signaturePtr, signatureMask);
        if (tempPtr == 0) {
            return 0;
        }
        int displacement = SignatureUtil.readInt(session, tempPtr + 3);
        long ptr = tempPtr + displacement + 7;
        return ptr - moduleBaseAddress;
    }

    /**
     * @deprecated Use {@link #getPtrFromSignature(long, byte[], String)}.
     */
    @Deprecated
    public long getPtrFromSignature(com.sun.jna.Pointer baseAddress, byte[] signaturePtr, String signatureMask) {
        if (baseAddress == null) return 0;
        return getPtrFromSignature(com.sun.jna.Pointer.nativeValue(baseAddress), signaturePtr, signatureMask);
    }

    private long resolveModuleSize() {
        if (com.sun.jna.Platform.isWindows()) {
            Tlhelp32.MODULEENTRY32W mod = ProcessUtil.getModule(pid, moduleName);
            if (mod != null) return mod.modBaseSize.longValue();
        }
        for (ModuleInfo m : NativeAccess.get().listModules(pid)) {
            if (m.name().equals(moduleName) || m.path().equals(moduleName)) {
                return m.size();
            }
        }
        throw new ModuleNotFoundException(moduleName);
    }
}
