package it.adrian.code.platform.windows;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import it.adrian.code.interfaces.Kernel32;
import it.adrian.code.interfaces.User32;
import it.adrian.code.platform.NativeAccess;
import it.adrian.code.platform.ProcessSession;
import it.adrian.code.utilities.Shell32Util;

public class WindowsAccess extends NativeAccess {

    private static final int PROCESS_VM_OPERATION = 0x0008;
    private static final int PROCESS_VM_READ = 0x0010;
    private static final int PROCESS_VM_WRITE = 0x0020;

    @Override
    public int findPidByName(String processName) {
        Tlhelp32.PROCESSENTRY32.ByReference entry = new Tlhelp32.PROCESSENTRY32.ByReference();
        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPALL, new WinDef.DWORD(0));
        try {
            while (Kernel32.INSTANCE.Process32NextW(snapshot, entry)) {
                if (processName.equals(Native.toString(entry.szExeFile))) {
                    return entry.th32ProcessID.intValue();
                }
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        return 0;
    }

    @Override
    public long getModuleBaseAddress(int pid, String moduleName) {
        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                Kernel32.TH32CS_SNAPMODULE, new WinDef.DWORD(pid));
        try {
            Tlhelp32.MODULEENTRY32W module = new Tlhelp32.MODULEENTRY32W();
            if (Kernel32.INSTANCE.Module32FirstW(snapshot, module)) {
                do {
                    if (moduleName.equals(module.szModule())) {
                        return com.sun.jna.Pointer.nativeValue(module.modBaseAddr);
                    }
                } while (Kernel32.INSTANCE.Module32NextW(snapshot, module));
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        return 0L;
    }

    @Override
    public long getModuleSize(int pid, String moduleName) {
        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                Kernel32.TH32CS_SNAPMODULE, new WinDef.DWORD(pid));
        try {
            Tlhelp32.MODULEENTRY32W module = new Tlhelp32.MODULEENTRY32W();
            if (Kernel32.INSTANCE.Module32FirstW(snapshot, module)) {
                do {
                    if (moduleName.equals(module.szModule())) {
                        return module.modBaseSize.longValue();
                    }
                } while (Kernel32.INSTANCE.Module32NextW(snapshot, module));
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        return 0L;
    }

    @Override
    public ProcessSession openProcess(int pid) {
        WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(
                PROCESS_VM_READ | PROCESS_VM_WRITE | PROCESS_VM_OPERATION, false, pid);
        return new WindowsProcessSession(pid, handle);
    }

    @Override
    public boolean readMemory(ProcessSession session, long address, byte[] buffer, int length) {
        WinNT.HANDLE handle = ((WindowsProcessSession) session).handle;
        Memory mem = new Memory(length);
        if (!Kernel32.INSTANCE.ReadProcessMemory(handle, new com.sun.jna.Pointer(address), mem, length, null)) {
            return false;
        }
        mem.read(0, buffer, 0, length);
        return true;
    }

    @Override
    public boolean writeMemory(ProcessSession session, long address, byte[] buffer, int length) {
        WinNT.HANDLE handle = ((WindowsProcessSession) session).handle;
        Memory mem = new Memory(length);
        mem.write(0, buffer, 0, length);
        IntByReference written = new IntByReference();
        return Kernel32.INSTANCE.WriteProcessMemory(handle, new com.sun.jna.Pointer(address), mem, length, written);
    }

    @Override
    public void closeSession(ProcessSession session) {
        Kernel32.INSTANCE.CloseHandle(((WindowsProcessSession) session).handle);
    }

    @Override
    public boolean isPrivileged() {
        return Shell32Util.isUserWindowsAdmin();
    }

    @Override
    public void abortMissingPrivileges() {
        try {
            User32.INSTANCE.MessageBox(null, "THIS REQUIRES ADMINISTRATION PERMISSIONS", "Warning",
                    User32.MB_OK | User32.MB_ICONWARNING);
        } catch (Throwable ignored) {
        }
        System.exit(-1);
    }

    @Override
    public void abortProcessNotFound(String processName) {
        try {
            User32.INSTANCE.MessageBox(null, "PROCESS TO ATTACH NOT FOUND: " + processName, "Warning",
                    User32.MB_OK | User32.MB_ICONWARNING);
        } catch (Throwable ignored) {
        }
        System.exit(-1);
    }
}
