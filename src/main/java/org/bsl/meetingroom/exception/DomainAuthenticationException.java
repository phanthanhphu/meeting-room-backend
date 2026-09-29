package org.bsl.meetingroom.exception;

public class DomainAuthenticationException extends RuntimeException {

    public enum Reason {
        INVALID_CREDENTIALS,
        SERVICE_UNAVAILABLE,
        EMAIL_NOT_FOUND,
        USER_NOT_FOUND,
        DISABLED,
        CONFIGURATION_ERROR
    }

    private final Reason reason;

    public DomainAuthenticationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public DomainAuthenticationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
