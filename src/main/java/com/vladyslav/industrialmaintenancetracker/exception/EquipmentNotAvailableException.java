package com.vladyslav.industrialmaintenancetracker.exception;

public class EquipmentNotAvailableException extends RuntimeException {

    public EquipmentNotAvailableException(Long equipmentId) {
        super("Equipment with id " + equipmentId + " has been decommissioned");
    }
}
