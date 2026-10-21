package com.vladyslav.industrialmaintenancetracker.exception;

public class RepairRequestEditConflictException extends RuntimeException {

    public RepairRequestEditConflictException() {
        super("The request has changed or editing is no longer available. "
                + "Your changes were not saved. Reload the request before editing again.");
    }
}
