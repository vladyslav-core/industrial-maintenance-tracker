package com.vladyslav.industrialmaintenancetracker.equipment.dto;

import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;

import java.time.Instant;

public class EquipmentDetails {

    private final Long id;
    private final String name;
    private final String inventoryNumber;
    private final String description;
    private final String location;
    private final EquipmentStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public EquipmentDetails(
            Long id,
            String name,
            String inventoryNumber,
            String description,
            String location,
            EquipmentStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.inventoryNumber = inventoryNumber;
        this.description = description;
        this.location = location;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public EquipmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}