package it.adrian.code.platform.linux;

import com.sun.jna.Library;
import com.sun.jna.Native;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class LinuxAccess extends NativeAccess {

    public interface LibC extends Library {
        int geteuid();
    }

    private static final class LibCHolder {
        static final LibC INSTANCE = Native.load("c", LibC.class);
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
    public boolean protect(ProcessSession session, long address, long size, MemoryProtection protection) {
        throw new UnsupportedOperationException(
                "Memory protection of a remote process is not implemented on Linux. " +
                        "It would require injecting an mprotect(2) syscall via ptrace.");
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
    public long allocate(ProcessSession session, long size, MemoryProtection protection) {
        throw new UnsupportedOperationException(
                "Remote memory allocation is not implemented on Linux. " +
                        "It would require injecting an mmap(2) syscall via ptrace.");
    }

    @Override
    public boolean free(ProcessSession session, long address, long size) {
        throw new UnsupportedOperationException(
                "Remote memory free is not implemented on Linux. " +
                        "It would require injecting a munmap(2) syscall via ptrace.");
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
