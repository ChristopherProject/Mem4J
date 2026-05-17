package it.adrian.code.exceptions;

/**
 * Base unchecked exception for every error raised by Mem4J.
 * Callers that only want a single catch site can catch this one.
 */
public class Mem4JException extends RuntimeException {

    public Mem4JException(String message) {
        super(message);
    }

    public Mem4JException(String message, Throwable cause) {
        super(message, cause);
    }
}
