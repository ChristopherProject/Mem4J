# Windows Example: Modifying Notepad Text

This example shows how to attach to Notepad and modify its memory (advanced reverse engineering).

## Prerequisites

- Windows 10 or later
- Administrator privileges
- Notepad process running with some text in it

## Concept

Modern Notepad stores its text in memory. By:
1. Finding the Notepad process
2. Locating its text buffer via signature scanning or known offsets
3. Writing new bytes to that buffer

we can change what's displayed on screen.

## Code

```java
import it.adrian.code.Memory;
import it.adrian.code.memory.Pointer;
import it.adrian.code.platform.NativeAccess;
import java.nio.charset.StandardCharsets;

public class NotepadExample {
    public static void main(String[] args) {
        try {
            NativeAccess na = NativeAccess.get();
            System.out.println("Backend: " + na.getClass().getSimpleName());
            
            // Attach to Notepad
            try (Pointer base = Pointer.getBaseAddress("notepad.exe")) {
                System.out.println("Attached to notepad.exe at 0x" + Long.toHexString(base.getBaseAddressValue()));
                
                // Find Notepad's HWND via WinAPI introspection (not shown)
                // or use a known offset pattern if you've reverse-engineered it.
                
                // Example: Write UTF-16LE string to a known buffer offset
                long textBufferOffset = 0x12345; // Hypothetical offset
                String newText = "Hacked by Mem4J!";
                
                base.copy().add(textBufferOffset).writeString(newText, StandardCharsets.UTF_16LE);
                System.out.println("Text updated!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## Notes

- Modern Notepad offsets may differ per Windows version
- Some versions use UWP (Universal Windows Platform) APIs that are harder to patch
- Use tools like Cheat Engine to discover offsets for your specific Notepad version
- SIP-style restrictions don't apply on Windows, but anticheat software may block access
