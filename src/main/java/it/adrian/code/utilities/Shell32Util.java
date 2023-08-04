package it.adrian.code.utilities;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.win32.StdCallLibrary;

public class Shell32Util {

    public static final Shell32 INSTANCE = Platform.isWindows() ? Native.loadLibrary("shell32", Shell32.class) : null;

    public static boolean isUserWindowsAdmin() {
        return INSTANCE != null && INSTANCE.IsUserAnAdmin();
    }

    public interface Shell32 extends StdCallLibrary {
        boolean IsUserAnAdmin() throws LastErrorException;
    }
}