package ru.spb.reshenie.chekerstatus.infrastructure.nsi;

public class NsiClientException extends RuntimeException {

    public NsiClientException(String message) {
        super(message);
    }

    public NsiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
