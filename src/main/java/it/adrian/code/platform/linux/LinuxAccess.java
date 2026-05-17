package it.adrian.code.platform.linux;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import it.adrian.code.exceptions.MemoryAccessException;
import it.adrian.code.platform.MemoryProtection;
import it.adrian.code.platform.ModuleInfo;
import it.adrian.code.platform.NativeAccess;
import it.adrian.code.platform.ProcessSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class LinuxAccess extends NativeAccess {

    public interface LibC extends Library {
        int geteuid();

        NativeLong ptrace(NativeLong request, int pid, NativeLong addr, NativeLong data);

        int waitpid(int pid, IntByReference wstatus, int options);
    }

    private static final class LibCHolder {
        static final LibC INSTANCE = Native.load("c", LibC.class);
    }

    /** x86_64 {@code user_regs_struct} layout. */
    public static class UserRegs64 extends Structure {
        public long r15, r14, r13, r12, rbp, rbx, r11, r10, r9, r8;
        public long rax, rcx, rdx, rsi, rdi, orig_rax, rip, cs, eflags, rsp, ss;
        public long fs_base, gs_base, ds, es, fs, gs;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "r15", "r14", "r13", "r12", "rbp", "rbx", "r11", "r10", "r9", "r8",
                    "rax", "rcx", "rdx", "rsi", "rdi", "orig_rax", "rip", "cs", "eflags", "rsp", "ss",
                    "fs_base", "gs_base", "ds", "es", "fs", "gs");
        }
    }

    private static final NativeLong PTRACE_PEEKDATA = new NativeLong(2);
    private static final NativeLong PTRACE_POKEDATA = new NativeLong(5);
    private static final NativeLong PTRACE_CONT = new NativeLong(7);
    private static final NativeLong PTRACE_GETREGS = new NativeLong(12);
    private static final NativeLong PTRACE_SETREGS = new NativeLong(13);
    private static final NativeLong PTRACE_ATTACH = new NativeLong(16);
    private static final NativeLong PTRACE_DETACH = new NativeLong(17);
    private static final NativeLong ZERO = new NativeLong(0);

    // x86_64 syscall numbers
    private static final long SYS_MMAP = 9;
    private static final long SYS_MPROTECT = 10;
    private static final long SYS_MUNMAP = 11;

    // mmap/mprotect prot flags
    private static final int PROT_READ = 1;
    private static final int PROT_WRITE = 2;
    private static final int PROT_EXEC = 4;

    // mmap flags
    private static final int MAP_PRIVATE = 0x02;
    private static final int MAP_ANONYMOUS = 0x20;

    private static int toLinuxProt(MemoryProtection protection) {
        switch (protection) {
            case NONE: return 0;
            case READ: return PROT_READ;
            case READ_WRITE: return PROT_READ | PROT_WRITE;
            case READ_EXECUTE: return PROT_READ | PROT_EXEC;
            case READ_WRITE_EXECUTE: return PROT_READ | PROT_WRITE | PROT_EXEC;
            default: throw new IllegalArgumentException("Unknown protection: " + protection);
        }
    }

    @Override
    public int findPidByName(String processName) {
        Path procRoot = Paths.get("/proc");
        if (!Files.isDirectory(procRoot)) {
            return 0;
        }
        try (Stream<Path> entries = Files.list(procRoot)) {
            return entries
                    .filter(LinuxAccess::isPidDir)
                    .filter(dir -> matches(dir, processName))
                    .mapToInt(dir -> Integer.parseInt(dir.getFileName().toString()))
                    .findFirst()
                    .orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }

    private static boolean isPidDir(Path p) {
        String name = p.getFileName().toString();
        if (name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) return false;
        }
        return true;
    }

    private static boolean matches(Path procDir, String target) {
        try {
            Path comm = procDir.resolve("comm");
            if (Files.isReadable(comm)) {
                String value = new String(Files.readAllBytes(comm)).trim();
                if (value.equals(target)) return true;
            }
        } catch (IOException ignored) {
        }
        try {
            Path exe = procDir.resolve("exe");
            if (Files.isSymbolicLink(exe)) {
                Path linkTarget = Files.readSymbolicLink(exe);
                Path basename = linkTarget.getFileName();
                if (basename != null && basename.toString().equals(target)) return true;
            }
        } catch (IOException | SecurityException ignored) {
        }
        return false;
    }

    @Override
    public long getModuleBaseAddress(int pid, String moduleName) {
        long min = Long.MAX_VALUE;
        try (BufferedReader reader = Files.newBufferedReader(Paths.get("/proc/" + pid + "/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                MapEntry entry = parseMapLine(line);
                if (entry == null || entry.path == null) continue;
                if (matchesModule(entry.path, moduleName)) {
                    if (entry.start < min) min = entry.start;
                }
            }
        } catch (IOException ignored) {
            return 0L;
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    @Override
    public long getModuleSize(int pid, String moduleName) {
        long min = Long.MAX_VALUE;
        long max = 0L;
        try (BufferedReader reader = Files.newBufferedReader(Paths.get("/proc/" + pid + "/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                MapEntry entry = parseMapLine(line);
                if (entry == null || entry.path == null) continue;
                if (matchesModule(entry.path, moduleName)) {
                    if (entry.start < min) min = entry.start;
                    if (entry.end > max) max = entry.end;
                }
            }
        } catch (IOException ignored) {
            return 0L;
        }
        return min == Long.MAX_VALUE ? 0L : max - min;
    }

    @Override
    public List<ModuleInfo> listModules(int pid) {
        Map<String, long[]> aggregate = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get("/proc/" + pid + "/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                MapEntry entry = parseMapLine(line);
                if (entry == null || entry.path == null) continue;
                long[] range = aggregate.computeIfAbsent(entry.path, k -> new long[]{Long.MAX_VALUE, 0L});
                if (entry.start < range[0]) range[0] = entry.start;
                if (entry.end > range[1]) range[1] = entry.end;
            }
        } catch (IOException ignored) {
            return new ArrayList<>();
        }
        List<ModuleInfo> result = new ArrayList<>(aggregate.size());
        for (Map.Entry<String, long[]> e : aggregate.entrySet()) {
            String fullPath = e.getKey();
            int slash = fullPath.lastIndexOf('/');
            String name = slash < 0 ? fullPath : fullPath.substring(slash + 1);
            long base = e.getValue()[0];
            long size = e.getValue()[1] - base;
            result.add(new ModuleInfo(name, fullPath, base, size));
        }
        return result;
    }

    private static boolean matchesModule(String fullPath, String moduleName) {
        if (fullPath.equals(moduleName)) return true;
        int slash = fullPath.lastIndexOf('/');
        String basename = slash < 0 ? fullPath : fullPath.substring(slash + 1);
        return basename.equals(moduleName);
    }

    private static MapEntry parseMapLine(String line) {
        int dash = line.indexOf('-');
        int space = line.indexOf(' ');
        if (dash <= 0 || space <= dash) return null;
        try {
            long start = Long.parseUnsignedLong(line.substring(0, dash), 16);
            long end = Long.parseUnsignedLong(line.substring(dash + 1, space), 16);
            int pathStart = line.indexOf('/');
            String path = pathStart < 0 ? null : line.substring(pathStart).trim();
            if (path != null && path.isEmpty()) path = null;
            return new MapEntry(start, end, path);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class MapEntry {
        final long start;
        final long end;
        final String path;

        MapEntry(long start, long end, String path) {
            this.start = start;
            this.end = end;
            this.path = path;
        }
    }

    @Override
    public ProcessSession openProcess(int pid) {
        try {
            return new LinuxProcessSession(pid);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean readMemory(ProcessSession session, long address, byte[] buffer, int length) {
        LinuxProcessSession ls = (LinuxProcessSession) session;
        if (ls == null) return false;
        try {
            synchronized (ls.mem) {
                ls.mem.seek(address);
                ls.mem.readFully(buffer, 0, length);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean writeMemory(ProcessSession session, long address, byte[] buffer, int length) {
        LinuxProcessSession ls = (LinuxProcessSession) session;
        if (ls == null) return false;
        try {
            synchronized (ls.mem) {
                ls.mem.seek(address);
                ls.mem.write(buffer, 0, length);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public MemoryProtection queryProtection(ProcessSession session, long address) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get("/proc/" + session.pid + "/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                MapEntry entry = parseMapLine(line);
                if (entry == null) continue;
                if (Long.compareUnsigned(address, entry.start) >= 0 &&
                        Long.compareUnsigned(address, entry.end) < 0) {
                    return parseProtFlags(line);
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static MemoryProtection parseProtFlags(String line) {
        int space = line.indexOf(' ');
        if (space < 0 || line.length() < space + 5) return null;
        char r = line.charAt(space + 1);
        char w = line.charAt(space + 2);
        char x = line.charAt(space + 3);
        boolean readable = (r == 'r');
        boolean writable = (w == 'w');
        boolean executable = (x == 'x');
        if (!readable && !writable && !executable) return MemoryProtection.NONE;
        if (readable && writable && executable) return MemoryProtection.READ_WRITE_EXECUTE;
        if (readable && writable) return MemoryProtection.READ_WRITE;
        if (readable && executable) return MemoryProtection.READ_EXECUTE;
        if (readable) return MemoryProtection.READ;
        return null;
    }

    @Override
    public boolean protect(ProcessSession session, long address, long size, MemoryProtection protection) {
        long result = injectSyscall(session.pid, SYS_MPROTECT, address, size, toLinuxProt(protection), 0, 0, 0);
        return result == 0;
    }

    @Override
    public long allocate(ProcessSession session, long size, MemoryProtection protection) {
        long result = injectSyscall(session.pid, SYS_MMAP,
                0L, size, toLinuxProt(protection), MAP_PRIVATE | MAP_ANONYMOUS, -1L, 0L);
        // mmap returns -errno (in the range [-4095, -1]) on failure
        if (result >= -4095L && result < 0L) {
            throw new MemoryAccessException("mmap injection returned errno " + (-result));
        }
        return result;
    }

    @Override
    public boolean free(ProcessSession session, long address, long size) {
        long result = injectSyscall(session.pid, SYS_MUNMAP, address, size, 0, 0, 0, 0);
        return result == 0;
    }

    /**
     * Inject a single {@code syscall} instruction into the target via {@code ptrace},
     * letting the kernel execute it on the target's behalf, then restore the original
     * instruction bytes and registers and detach.
     * <p>
     * x86_64 only. The target is briefly stopped (SIGSTOP from {@code PTRACE_ATTACH})
     * for the duration of the call.
     */
    private long injectSyscall(int pid, long sysno, long a1, long a2, long a3, long a4, long a5, long a6) {
        String arch = System.getProperty("os.arch");
        if (!"amd64".equals(arch) && !"x86_64".equals(arch)) {
            throw new UnsupportedOperationException(
                    "Linux syscall injection is only implemented for x86_64; current arch: " + arch);
        }

        LibC libc = LibCHolder.INSTANCE;
        IntByReference status = new IntByReference();

        if (libc.ptrace(PTRACE_ATTACH, pid, ZERO, ZERO).longValue() == -1) {
            throw new MemoryAccessException("ptrace(PTRACE_ATTACH) failed for pid " + pid);
        }

        try {
            libc.waitpid(pid, status, 0);

            UserRegs64 saved = new UserRegs64();
            libc.ptrace(PTRACE_GETREGS, pid, ZERO,
                    new NativeLong(Pointer.nativeValue(saved.getPointer())));
            saved.read();

            long injAddr = saved.rip;
            long originalBytes = libc.ptrace(PTRACE_PEEKDATA, pid,
                    new NativeLong(injAddr), ZERO).longValue();
            long injBytes = (originalBytes & 0xFFFFFFFFFF000000L) | 0xCC050FL; // syscall ; int3 ; <orig...>
            libc.ptrace(PTRACE_POKEDATA, pid, new NativeLong(injAddr),
                    new NativeLong(injBytes));

            try {
                UserRegs64 modified = new UserRegs64();
                // Mirror the native bytes from saved → modified before tweaking individual fields.
                byte[] snapshot = saved.getPointer().getByteArray(0, saved.size());
                modified.getPointer().write(0, snapshot, 0, snapshot.length);
                modified.read();

                modified.rax = sysno;
                modified.rdi = a1;
                modified.rsi = a2;
                modified.rdx = a3;
                modified.r10 = a4;
                modified.r8 = a5;
                modified.r9 = a6;
                modified.rip = injAddr;
                modified.orig_rax = -1L; // suppress any pending syscall restart
                modified.write();

                libc.ptrace(PTRACE_SETREGS, pid, ZERO,
                        new NativeLong(Pointer.nativeValue(modified.getPointer())));
                libc.ptrace(PTRACE_CONT, pid, ZERO, ZERO);
                libc.waitpid(pid, status, 0);

                UserRegs64 after = new UserRegs64();
                libc.ptrace(PTRACE_GETREGS, pid, ZERO,
                        new NativeLong(Pointer.nativeValue(after.getPointer())));
                after.read();
                return after.rax;
            } finally {
                libc.ptrace(PTRACE_POKEDATA, pid, new NativeLong(injAddr),
                        new NativeLong(originalBytes));
                libc.ptrace(PTRACE_SETREGS, pid, ZERO,
                        new NativeLong(Pointer.nativeValue(saved.getPointer())));
            }
        } finally {
            libc.ptrace(PTRACE_DETACH, pid, ZERO, ZERO);
        }
    }

    @Override
    public void closeSession(ProcessSession session) {
        if (session == null) return;
        try {
            ((LinuxProcessSession) session).close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public boolean isPrivileged() {
        try {
            return LibCHolder.INSTANCE.geteuid() == 0;
        } catch (Throwable t) {
            return "root".equals(System.getProperty("user.name"));
        }
    }

    @Override
    protected String privilegeErrorMessage() {
        return "Mem4J: this operation requires root or CAP_SYS_PTRACE on Linux.";
    }
}
