package com.vladyslav.industrialmaintenancetracker.exception;

public class RepairRequestWorkConflictException extends RuntimeException {

    public RepairRequestWorkConflictException() {
        super("The request has changed or is no longer in progress. Your changes were not saved. "
                + "Reload the request before editing the result again.");
    }
}
