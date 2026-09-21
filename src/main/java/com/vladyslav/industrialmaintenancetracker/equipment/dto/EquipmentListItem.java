package com.vladyslav.industrialmaintenancetracker.equipment.dto;

import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;

public class EquipmentListItem {

    private final Long id;
    private final String name;
    private final String inventoryNumber;
    private final String location;
    private final EquipmentStatus status;

    public EquipmentListItem(
            Long id,
            String name,
            String inventoryNumber,
            String location,
            EquipmentStatus status
    ) {
        this.id = id;
        this.name = name;
        this.inventoryNumber = inventoryNumber;
        this.location = location;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInventoryNumber() {
        return inventoryNumber;
    }

    public String getLocation() {
        return location;
    }

    public EquipmentStatus getStatus() {
        return status;
    }
}