package com.vladyslav.industrialmaintenancetracker.exception;

public class EquipmentHasOpenRequestsException extends RuntimeException {

    public EquipmentHasOpenRequestsException() {
        super("This equipment has open repair requests. Close or cancel them before decommissioning it.");
    }
}
