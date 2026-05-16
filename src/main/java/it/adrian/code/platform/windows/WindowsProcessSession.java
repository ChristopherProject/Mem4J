package it.adrian.code.platform.windows;

import com.sun.jna.platform.win32.WinNT;
import it.adrian.code.platform.ProcessSession;

public class WindowsProcessSession extends ProcessSession {

    public final WinNT.HANDLE handle;

    public WindowsProcessSession(int pid, WinNT.HANDLE handle) {
        super(pid);
        this.handle = handle;
    }
}
