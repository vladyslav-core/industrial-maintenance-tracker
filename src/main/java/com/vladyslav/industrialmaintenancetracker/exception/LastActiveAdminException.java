package com.vladyslav.industrialmaintenancetracker.exception;

public class LastActiveAdminException extends RuntimeException {

    public LastActiveAdminException() {
        super("The last active administrator cannot be deactivated");
    }
}