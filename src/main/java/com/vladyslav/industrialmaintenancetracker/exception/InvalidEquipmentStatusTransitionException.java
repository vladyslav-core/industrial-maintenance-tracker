package com.vladyslav.industrialmaintenancetracker.exception;

import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;

public class InvalidEquipmentStatusTransitionException
        extends RuntimeException {

    public InvalidEquipmentStatusTransitionException(
            EquipmentStatus currentStatus,
            EquipmentStatus targetStatus
    ) {
        super(
                "Equipment status cannot be changed from "
                        + currentStatus
                        + " to "
                        + targetStatus
        );
    }
}