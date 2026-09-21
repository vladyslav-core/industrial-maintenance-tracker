package com.vladyslav.industrialmaintenancetracker.exception;

public class DuplicateInventoryNumberException extends RuntimeException {

    public DuplicateInventoryNumberException(String inventoryNumber) {
        super(
                "Equipment with inventory number '"
                        + inventoryNumber
                        + "' already exists"
        );
    }
}