package it.adrian.code.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA bindings for {@code dyld} (macOS dynamic linker).
 *
 * <p>Provides access to the runtime state of loaded dynamic libraries and executables.
 * These functions are typically only callable from within the process; reading from
 * another process requires ptrace or Mach-level introspection.</p>
 *
 * @author Christopher Project
 */
public interface Dyld extends Library {
    // Not loaded by default; only available in-process.
    // For cross-process inspection, use Mach APIs or ptrace.

    /**
     * Returns the number of currently loaded images.
     *
     * <pre>
     *   uint32_t dyld_image_count(void);
     * </pre>
     *
     * @return Count of loaded images
     */
    int dyld_image_count();

    /**
     * Returns the Mach header of the image at the given index.
     *
     * <pre>
     *   const struct mach_header* dyld_get_image_header(uint32_t image_index);
     * </pre>
     *
     * @param imageIndex Index of the image
     * @return Pointer to the Mach header, or null if out of bounds
     */
    Pointer dyld_get_image_header(int imageIndex);

    /**
     * Returns the slide (ASLR offset) of the image at the given index.
     *
     * <pre>
     *   intptr_t dyld_get_image_vmaddr_slide(uint32_t image_index);
     * </pre>
     *
     * @param imageIndex Index of the image
     * @return ASLR slide in bytes
     */
    long dyld_get_image_vmaddr_slide(int imageIndex);

    /**
     * Returns the name (file path) of the image at the given index.
     *
     * <pre>
     *   const char* dyld_get_image_name(uint32_t image_index);
     * </pre>
     *
     * @param imageIndex Index of the image
     * @return C string (file path) of the image, or null if out of bounds
     */
    String dyld_get_image_name(int imageIndex);
}
