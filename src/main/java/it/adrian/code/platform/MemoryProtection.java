package it.adrian.code.platform;

/**
 * Page-level memory protection flags, abstracting over Windows
 * {@code PAGE_*} constants and POSIX {@code PROT_*}.
 * <p>
 * Memory protection and allocation are supported on Windows. The Linux
 * backend rejects them with {@link UnsupportedOperationException} because
 * implementing them requires injecting a syscall in the target process,
 * which is outside the scope of this library.
 */
public enum MemoryProtection {

    NONE,
    READ,
    READ_WRITE,
    READ_EXECUTE,
    READ_WRITE_EXECUTE
}
