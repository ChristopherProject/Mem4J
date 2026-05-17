package it.adrian.code.platform.windows;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import it.adrian.code.exceptions.MemoryAccessException;
import it.adrian.code.interfaces.Kernel32;
import it.adrian.code.platform.MemoryProtection;
import it.adrian.code.platform.ModuleInfo;
import it.adrian.code.platform.NativeAccess;
import it.adrian.code.platform.ProcessSession;
import it.adrian.code.utilities.Shell32Util;

import java.util.ArrayList;
import java.util.List;

public class WindowsAccess extends NativeAccess {

    private static final int PROCESS_VM_OPERATION = 0x0008;
    private static final int PROCESS_VM_READ = 0x0010;
    private static final int PROCESS_VM_WRITE = 0x0020;

    private static final int PAGE_NOACCESS = 0x01;
    private static final int PAGE_READONLY = 0x02;
    private static final int PAGE_READWRITE = 0x04;
    private static final int PAGE_EXECUTE_READ = 0x20;
    private static final int PAGE_EXECUTE_READWRITE = 0x40;

    private static final int MEM_COMMIT = 0x1000;
    private static final int MEM_RESERVE = 0x2000;
    private static final int MEM_RELEASE = 0x8000;

    private static int toWin32(MemoryProtection protection) {
        switch (protection) {
            case NONE: return PAGE_NOACCESS;
            case READ: return PAGE_READONLY;
            case READ_WRITE: return PAGE_READWRITE;
            case READ_EXECUTE: return PAGE_EXECUTE_READ;
            case READ_WRITE_EXECUTE: return PAGE_EXECUTE_READWRITE;
            default: throw new IllegalArgumentException("Unknown protection: " + protection);
        }
    }

    private static MemoryProtection fromWin32(int protect) {
        // Strip page modifiers (PAGE_GUARD = 0x100, PAGE_NOCACHE = 0x200, PAGE_WRITECOMBINE = 0x400).
        int base = protect & 0xFF;
        switch (base) {
            case PAGE_NOACCESS: return MemoryProtection.NONE;
            case PAGE_READONLY: return MemoryProtection.READ;
            case PAGE_READWRITE: return MemoryProtection.READ_WRITE;
            case PAGE_EXECUTE_READ: return MemoryProtection.READ_EXECUTE;
            case PAGE_EXECUTE_READWRITE: return MemoryProtection.READ_WRITE_EXECUTE;
            default: return null;
        }
    }

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
    public List<ModuleInfo> listModules(int pid) {
        List<ModuleInfo> result = new ArrayList<>();
        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                Kernel32.TH32CS_SNAPMODULE, new WinDef.DWORD(pid));
        try {
            Tlhelp32.MODULEENTRY32W module = new Tlhelp32.MODULEENTRY32W();
            if (Kernel32.INSTANCE.Module32FirstW(snapshot, module)) {
                do {
                    result.add(new ModuleInfo(
                            module.szModule(),
                            module.szExePath(),
                            com.sun.jna.Pointer.nativeValue(module.modBaseAddr),
                            module.modBaseSize.longValue()));
                } while (Kernel32.INSTANCE.Module32NextW(snapshot, module));
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        return result;
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
    public boolean protect(ProcessSession session, long address, long size, MemoryProtection protection) {
        WinNT.HANDLE handle = ((WindowsProcessSession) session).handle;
        IntByReference old = new IntByReference();
        return Kernel32.INSTANCE.VirtualProtectEx(
                handle, new com.sun.jna.Pointer(address), new BaseTSD.SIZE_T(size),
                toWin32(protection), old);
    }

    @Override
    public MemoryProtection queryProtection(ProcessSession session, long address) {
        WinNT.HANDLE handle = ((WindowsProcessSession) session).handle;
        WinNT.MEMORY_BASIC_INFORMATION mbi = new WinNT.MEMORY_BASIC_INFORMATION();
        BaseTSD.SIZE_T written = Kernel32.INSTANCE.VirtualQueryEx(
                handle, new com.sun.jna.Pointer(address), mbi, new BaseTSD.SIZE_T(mbi.size()));
        if (written == null || written.longValue() == 0) return null;
        return fromWin32(mbi.protect.intValue());
    }

    @Override
    public long allocate(ProcessSession session, long size, MemoryProtection protection) {
        WinNT.HANDLE handle = ((WindowsProcessSession) session).handle;
        com.sun.jna.Pointer p = Kernel32.INSTANCE.VirtualAllocEx(
                handle, null, new BaseTSD.SIZE_T(size), MEM_COMMIT | MEM_RESERVE, toWin32(protection));
        if (p == null) {
            throw new MemoryAccessException("VirtualAllocEx failed (size=" + size + ")");
        }
        return com.sun.jna.Pointer.nativeValue(p);
    }

    @Override
    public boolean free(ProcessSession session, long address, long size) {
        WinNT.HANDLE handle = ((WindowsProcessSession) session).handle;
        return Kernel32.INSTANCE.VirtualFreeEx(
                handle, new com.sun.jna.Pointer(address), new BaseTSD.SIZE_T(0), MEM_RELEASE);
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
    protected String privilegeErrorMessage() {
        return "Mem4J: this operation requires Administrator privileges on Windows.";
    }
}
