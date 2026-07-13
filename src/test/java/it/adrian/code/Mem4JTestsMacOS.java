package it.adrian.code;

import com.sun.jna.Platform;
import it.adrian.code.memory.Pointer;
import it.adrian.code.platform.NativeAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.EnabledOnOs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

/**
 * macOS-specific integration tests for Mem4J.
 *
 * <p>Tests macOS memory access, process enumeration, and module discovery.
 * All tests are gated with {@code @EnabledOnOs(OS.MAC)} and will be skipped
 * on non-macOS platforms.</p>
 *
 * @author Christopher Project
 */
@DisplayName("Mem4J macOS Tests")
@EnabledOnOs(OS.MAC)
public class Mem4JTestsMacOS {

    private static NativeAccess nativeAccess;

    @BeforeAll
    static void setUpOnce() {
        assertTrue(Platform.isMac(), "Tests require macOS platform");
        nativeAccess = NativeAccess.get();
        assertNotNull(nativeAccess, "NativeAccess should be initialized");
    }

    @Test
    @DisplayName("Backend is MacOSAccess")
    void testBackendIsMacOS() {
        String backendClass = nativeAccess.getClass().getName();
        assertTrue(
                backendClass.contains("MacOS"),
                "Expected MacOSAccess backend, got " + backendClass
        );
    }

    @Test
    @DisplayName("Can find java process by name")
    void testFindJavaProcess() {
        // This test finds the JVM running the tests
        int pid = nativeAccess.findPidByName("java");
        assertTrue(pid > 0, "Should find java process");
    }

    @Test
    @DisplayName("Privilege check (informational)")
    void testPrivilegeStatus() {
        boolean privileged = nativeAccess.isPrivileged();
        System.out.println("Running as privileged: " + privileged);
        // Test just reports status; doesn't fail on unprivileged
    }

    @Test
    @DisplayName("Can open current process")
    void testOpenCurrentProcess() {
        int currentPid = ProcessHandle.current().pid();
        var session = nativeAccess.openProcess((int) currentPid);
        assertNotNull(session, "Should open current process");
        // Session auto-closes (would need try-with-resources if closeable)
    }

    @Test
    @DisplayName("Can list modules in current process")
    void testListModules() {
        int currentPid = (int) ProcessHandle.current().pid();
        var modules = nativeAccess.listModules(currentPid);
        assertNotNull(modules, "Module list should not be null");
        assertTrue(modules.size() >= 1, "Should have at least the main executable");
    }

    @Test
    @DisplayName("Module info contains expected fields")
    void testModuleInfoStructure() {
        int currentPid = (int) ProcessHandle.current().pid();
        var modules = nativeAccess.listModules(currentPid);
        if (!modules.isEmpty()) {
            var main = modules.get(0);
            assertNotNull(main.name(), "Module name should not be null");
            assertNotNull(main.path(), "Module path should not be null");
            assertTrue(main.baseAddress() > 0, "Base address should be positive");
            assertTrue(main.size() > 0, "Module size should be positive");
        }
    }
}
