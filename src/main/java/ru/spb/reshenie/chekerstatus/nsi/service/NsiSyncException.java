package ru.spb.reshenie.chekerstatus.nsi.service;

public class NsiSyncException extends RuntimeException {

    public NsiSyncException(String message) {
        super(message);
    }

    public NsiSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
