package ru.spb.reshenie.chekerstatus.nsi.client;

public class NsiClientException extends RuntimeException {

    public NsiClientException(String message) {
        super(message);
    }

    public NsiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
