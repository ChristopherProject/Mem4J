package it.adrian.code.memory;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import it.adrian.code.interfaces.Kernel32;
import it.adrian.code.interfaces.User32;

import java.util.Optional;

public class Pointer {

    private final WinNT.HANDLE handle;
    public String processName;
    public String moduleName;
    private com.sun.jna.Pointer baseAddress;
    private long offset;

    public Pointer(WinNT.HANDLE handle, com.sun.jna.Pointer baseAddress) {
        this.handle = handle;
        this.baseAddress = baseAddress;
        this.offset = 0L;
    }

    public static Pointer getBaseAddress(String processName) {
        Optional<ProcessHandle> processHandle = ProcessHandle.allProcesses().filter(p -> p.info().command().orElse("").endsWith(processName)).findFirst();
        Optional<Long> pidOptional = processHandle.map(ProcessHandle::pid);
        int pid = 0;
        if (pidOptional.isPresent()) {
             pid = Math.toIntExact(pidOptional.get());
        }
        else {
            try {
                User32.INSTANCE.MessageBox(null, "PROCESS TO ATTACH NOT FOUND", "Warning!?!", User32.MB_OK | User32.MB_ICONWARNING);
                System.exit(-1);
            } catch (Throwable e) {
                //e.printStackTrace();
            }
        }
        int accessRight = 0x0010 | 0x0020 | 0x0008;
        WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(accessRight, false, pid);
        com.sun.jna.Pointer baseAddress = getModuleBaseAddress(pid, processName);
        Pointer ptr = new Pointer(handle, baseAddress);
        ptr.processName = processName;
        ptr.moduleName = processName;
        return ptr;
    }

    public static com.sun.jna.Pointer getModuleBaseAddress(int pid, String moduleName) {
        com.sun.jna.Pointer baseAddress = null;
        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPMODULE, new WinDef.DWORD(pid));
        try {
            Tlhelp32.MODULEENTRY32W module = new Tlhelp32.MODULEENTRY32W();
            if (Kernel32.INSTANCE.Module32FirstW(snapshot, module)) {
                do {
                    if (moduleName.equals(module.szModule())) {
                        baseAddress = module.modBaseAddr;
                        break;
                    }
                } while (Kernel32.INSTANCE.Module32NextW(snapshot, module));
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }

        return baseAddress;
    }

    public Pointer add(int val) {
        offset += val;
        return this;
    }

    public long readLong() {
        Memory memory = getMemory(8);
        return memory.getLong(0);
    }

    public double readDouble() {
        Memory memory = getMemory(8);
        return memory.getDouble(0);
    }


    public float readFloat() {
        Memory memory = getMemory(8);
        return memory.getFloat(0);
    }

    public int readInt() {
        Memory memory = getMemory(4);
        return memory.getInt(0);
    }

    public Memory getMemory(int size) {
        Memory memory = new Memory(size);
        com.sun.jna.Pointer src = baseAddress.share(offset);
        Kernel32.INSTANCE.ReadProcessMemory(handle, src, memory, size, null);
        return memory;
    }

    public boolean writeFloat(float value) {
        Memory memory = new Memory(8);
        memory.setFloat(0, value);
        com.sun.jna.Pointer src = baseAddress.share(offset);
        return Kernel32.INSTANCE.WriteProcessMemory(handle, src, memory, 8, null);
    }

    public boolean writeDouble(double value) {
        Memory memory = new Memory(8);
        memory.setDouble(0, value);
        com.sun.jna.Pointer src = baseAddress.share(offset);
        return Kernel32.INSTANCE.WriteProcessMemory(handle, src, memory, 8, null);
    }

    public boolean writeLong(long value) {
        Memory memory = new Memory(8);
        memory.setLong(0, value);
        com.sun.jna.Pointer src = baseAddress.share(offset);
        IntByReference intRef = new IntByReference();
        boolean res = Kernel32.INSTANCE.WriteProcessMemory(handle, src, memory, 8, intRef);
        return res;
    }

    public boolean writeInt(int value) {
        Memory memory = new Memory(4);
        memory.setInt(0, value);
        com.sun.jna.Pointer src = baseAddress.share(offset);
        IntByReference intRef = new IntByReference();
        boolean res = Kernel32.INSTANCE.WriteProcessMemory(handle, src, memory, 4, intRef);
        return res;
    }

    public Pointer copy() {
        Pointer ptr = new Pointer(handle, baseAddress);
        ptr.offset = offset;
        ptr.moduleName = moduleName;
        ptr.processName = processName;
        return ptr;
    }

    public Pointer indirect64() {
        baseAddress = new com.sun.jna.Pointer(readLong());
        offset = 0;
        return this;
    }

    @Override
    public String toString() {
        return moduleName + "[" + String.format("%#08x", com.sun.jna.Pointer.nativeValue(baseAddress)) + "]+0x" + Long.toHexString(offset) + " => 0x" + Long.toHexString(com.sun.jna.Pointer.nativeValue(baseAddress) + offset);
    }
}