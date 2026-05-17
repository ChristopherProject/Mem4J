package it.adrian.code.exceptions;

/**
 * Thrown when the underlying OS rejects a memory read, write, protect or
 * allocation request — typically because the target address is not mapped,
 * the page lacks the required permissions, or the kernel returned an error.
 */
public class MemoryAccessException extends Mem4JException {

    public MemoryAccessException(String message) {
        super(message);
    }

    public MemoryAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
