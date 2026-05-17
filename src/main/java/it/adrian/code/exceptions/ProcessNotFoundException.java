package it.adrian.code.exceptions;

/**
 * Thrown when no running process matches the executable name supplied
 * to {@code Pointer.getBaseAddress}.
 */
public class ProcessNotFoundException extends Mem4JException {

    public ProcessNotFoundException(String processName) {
        super("Process not found: " + processName);
    }
}
