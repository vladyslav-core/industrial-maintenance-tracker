package com.vladyslav.industrialmaintenancetracker.exception;

public class RepairRequestNotFoundException extends RuntimeException {

    public RepairRequestNotFoundException(Long requestId) {
        super("Repair request #" + requestId + " is unavailable");
    }
}
