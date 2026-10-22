package com.vladyslav.industrialmaintenancetracker.exception;

public class TechnicianHasOpenRequestsException extends RuntimeException {

    public TechnicianHasOpenRequestsException() {
        super("Reassign, close or cancel the technician's open repair requests before deactivating the account or changing its role.");
    }
}
