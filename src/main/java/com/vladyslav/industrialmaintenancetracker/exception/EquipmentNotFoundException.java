package com.vladyslav.industrialmaintenancetracker.exception;

public class EquipmentNotFoundException extends RuntimeException {

    public EquipmentNotFoundException(Long equipmentId) {
        super("Equipment with id " + equipmentId + " was not found");
    }
}