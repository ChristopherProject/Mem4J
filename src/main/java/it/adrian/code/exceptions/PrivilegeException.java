package it.adrian.code.exceptions;

/**
 * Thrown when an operation requires privileges the JVM does not have:
 * Administrator on Windows, {@code euid == 0} or {@code CAP_SYS_PTRACE} on Linux.
 */
public class PrivilegeException extends Mem4JException {

    public PrivilegeException(String message) {
        super(message);
    }
}
