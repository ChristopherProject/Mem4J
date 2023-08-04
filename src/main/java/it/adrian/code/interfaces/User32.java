package it.adrian.code.interfaces;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.W32APIOptions;

public interface User32 extends com.sun.jna.platform.win32.User32 {

    int MB_OK = 0x00000000, MB_ICONINFORMATION = 0x00000040, MB_ICONWARNING = 0x00000030;

    User32 INSTANCE = Native.loadLibrary("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

    int MessageBox(WinDef.HWND hWnd, String lpText, String lpCaption, int uType);
}