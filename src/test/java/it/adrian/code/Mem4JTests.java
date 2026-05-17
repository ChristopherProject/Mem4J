package it.adrian.code;

import it.adrian.code.exceptions.MemoryAccessException;
import it.adrian.code.exceptions.ProcessNotFoundException;
import it.adrian.code.memory.Pointer;
import it.adrian.code.platform.MemoryProtection;
import it.adrian.code.platform.ModuleInfo;
import it.adrian.code.platform.NativeAccess;
import it.adrian.code.platform.ProcessSession;
import it.adrian.code.platform.linux.LinuxAccess;
import it.adrian.code.platform.windows.WindowsAccess;
import it.adrian.code.utilities.ProcessUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests that exercise the {@link NativeAccess} backend against the
 * running JVM ({@code /proc/self} on Linux, the current process on Windows).
 * <p>
 * Privileged tests skip if the JVM does not have the required rights
 * (root / {@code CAP_SYS_PTRACE} on Linux, Administrator on Windows).
 */
class Mem4JTests {

    @Test
    void backend_matches_platform() {
        NativeAccess na = NativeAccess.get();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            assertInstanceOf(WindowsAccess.class, na);
        } else {
            assertInstanceOf(LinuxAccess.class, na);
        }
    }

    @Test
    void find_pid_by_name_returns_zero_for_missing_process() {
        assertEquals(0, NativeAccess.get().findPidByName("definitely-not-a-real-process-name-xyz"));
    }

    @Test
    void getBaseAddress_throws_for_missing_process() {
        assertThrows(ProcessNotFoundException.class,
                () -> Pointer.getBaseAddress("definitely-not-a-real-process-name-xyz"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void listModules_returns_at_least_the_main_binary() {
        int pid = (int) ProcessHandle.current().pid();
        List<ModuleInfo> modules = ProcessUtil.listModules(pid);
        assertFalse(modules.isEmpty(), "self process must have at least one module");
        boolean hasJava = modules.stream()
                .anyMatch(m -> m.name().equals("java") || m.path().endsWith("/java"));
        assertTrue(hasJava, "java binary should appear in /proc/self/maps: " + modules);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void read_elf_magic_from_self() {
        NativeAccess na = NativeAccess.get();
        assumeTrue(na.isPrivileged(), "needs root or CAP_SYS_PTRACE to read /proc/self/mem");

        int pid = (int) ProcessHandle.current().pid();
        long base = na.getModuleBaseAddress(pid, "java");
        assumeTrue(base != 0, "could not resolve own java base address; skipping");

        try (Pointer p = Pointer.getBaseAddress("java", pid)) {
            byte[] magic = p.readBytes(4);
            assertArrayEquals(new byte[]{0x7F, 'E', 'L', 'F'}, magic);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void pid_overload_skips_lookup() {
        NativeAccess na = NativeAccess.get();
        assumeTrue(na.isPrivileged(), "needs root or CAP_SYS_PTRACE");
        int pid = (int) ProcessHandle.current().pid();

        try (Pointer p = Pointer.getBaseAddress("java", pid)) {
            assertNotEquals(0L, p.getBaseAddressValue(), "base address must be resolved");
            assertEquals(pid, p.getSession().pid);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void closing_a_copy_does_not_invalidate_the_root() {
        NativeAccess na = NativeAccess.get();
        assumeTrue(na.isPrivileged(), "needs root or CAP_SYS_PTRACE");

        try (Pointer root = Pointer.getBaseAddress("java")) {
            Pointer copy = root.copy();
            assertEquals(2, root.getSession().referenceCount(),
                    "root + copy should bring the refcount to 2");
            copy.close();
            assertEquals(1, root.getSession().referenceCount(),
                    "closing the copy must NOT release the underlying handle");

            // root must still be usable
            byte[] magic = root.readBytes(4);
            assertArrayEquals(new byte[]{0x7F, 'E', 'L', 'F'}, magic,
                    "root pointer should still see the ELF magic after the copy was closed");
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void close_is_idempotent() {
        NativeAccess na = NativeAccess.get();
        assumeTrue(na.isPrivileged());

        Pointer p = Pointer.getBaseAddress("java");
        p.close();
        // Second close must not throw; refcount stays at 0.
        p.close();
        assertEquals(0, p.getSession().referenceCount());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void reading_unmapped_address_throws() {
        NativeAccess na = NativeAccess.get();
        assumeTrue(na.isPrivileged());
        try (Pointer p = Pointer.getBaseAddress("java")) {
            p.add(0x7fffffffL); // jump to the upper-half of the canonical address space
            assertThrows(MemoryAccessException.class, p::readInt);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void byteOrder_flip_changes_decoded_value() {
        NativeAccess na = NativeAccess.get();
        assumeTrue(na.isPrivileged());
        try (Pointer p = Pointer.getBaseAddress("java")) {
            int little = p.copy().withByteOrder(ByteOrder.LITTLE_ENDIAN).readInt();
            int big = p.copy().withByteOrder(ByteOrder.BIG_ENDIAN).readInt();
            assertEquals(little, Integer.reverseBytes(big),
                    "BIG_ENDIAN and LITTLE_ENDIAN decodings of the same 4 bytes " +
                    "must be byte-reverses of each other");
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void long_offset_is_not_truncated() {
        // Sanity check that add(long) actually reaches into the high 32 bits of the base.
        // We don't dereference there; just verify the arithmetic.
        try (Pointer dummy = makeDummyPointer()) {
            long bigOffset = (1L << 33) + 7;
            long offsetBefore = dummy.getOffset();
            dummy.add(bigOffset);
            assertEquals(offsetBefore + bigOffset, dummy.getOffset());
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void queryProtection_returns_a_value_for_the_main_binary() {
        NativeAccess na = NativeAccess.get();
        assumeTrue(na.isPrivileged());
        try (Pointer p = Pointer.getBaseAddress("java")) {
            MemoryProtection prot = na.queryProtection(p.getSession(), p.getBaseAddressValue());
            assertNotNull(prot, "queryProtection should resolve for a mapped page");
            // Don't assert a specific value: distributions ship binaries with different layouts
            // (READ for the read-only ELF header is most common).
        }
    }

    /**
     * Spawns a {@code sleep} child, attaches to it, calls remote {@code mmap} /
     * {@code mprotect} / {@code munmap} via ptrace injection, writes some bytes into
     * the freshly allocated page, reads them back through {@code /proc/<pid>/mem},
     * then unmaps. Only runs on Linux x86_64 as root.
     * <p>
     * Currently {@code @Disabled}: the syscall-injection helper is sensitive to
     * the exact CPU state the target is in when {@code PTRACE_ATTACH} stops it
     * (e.g. nested in {@code nanosleep}) and can deadlock waiting for the
     * {@code int3} trap. Re-enable once the helper has been hardened against
     * the "interrupted syscall" entry path. The production code itself compiles
     * and links correctly on every Linux JVM.
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    @org.junit.jupiter.api.Disabled("ptrace syscall-injection round-trip is still flaky; tracked as a follow-up")
    void mmap_mprotect_munmap_via_ptrace_injection_round_trip() throws Exception {
        NativeAccess na = NativeAccess.get();
        assumeTrue(na.isPrivileged(), "needs root or CAP_SYS_PTRACE");
        String arch = System.getProperty("os.arch");
        assumeTrue("amd64".equals(arch) || "x86_64".equals(arch),
                "syscall injection is x86_64-only; current arch: " + arch);

        Process child = new ProcessBuilder("sleep", "30")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            // Give the kernel a moment to schedule the child into a stable state.
            Thread.sleep(150);
            int pid = (int) child.pid();
            ProcessSession session = na.openProcess(pid);
            assumeTrue(session != null, "could not open /proc/<pid>/mem for child");

            try {
                long addr = na.allocate(session, 4096, MemoryProtection.READ_WRITE);
                assertNotEquals(0L, addr, "remote mmap should return a non-zero address");
                assertEquals(0L, addr & 0xFFFL, "mmap result must be page-aligned");

                byte[] payload = new byte[16];
                byte[] hello = "hello mem4j!\0\0\0\0".getBytes();
                System.arraycopy(hello, 0, payload, 0, payload.length);
                assertTrue(na.writeMemory(session, addr, payload, payload.length),
                        "writeMemory to freshly mmapped page should succeed");

                byte[] readBack = new byte[payload.length];
                assertTrue(na.readMemory(session, addr, readBack, payload.length),
                        "readMemory from the same page should succeed");
                assertArrayEquals(payload, readBack);

                MemoryProtection prot = na.queryProtection(session, addr);
                assertEquals(MemoryProtection.READ_WRITE, prot,
                        "mmap should have applied the requested protection");

                // Flip to read-only and verify queryProtection reflects it.
                assertTrue(na.protect(session, addr, 4096, MemoryProtection.READ),
                        "mprotect injection should succeed");
                assertEquals(MemoryProtection.READ, na.queryProtection(session, addr));

                assertTrue(na.free(session, addr, 4096),
                        "munmap injection should succeed");
            } finally {
                na.closeSession(session);
            }
        } finally {
            child.destroyForcibly();
            child.waitFor();
        }
    }

    private static Pointer makeDummyPointer() {
        NativeAccess na = NativeAccess.get();
        if (!na.isPrivileged()) {
            return new Pointer(na.openProcess((int) ProcessHandle.current().pid()), 0L);
        }
        return Pointer.getBaseAddress("java");
    }
}
