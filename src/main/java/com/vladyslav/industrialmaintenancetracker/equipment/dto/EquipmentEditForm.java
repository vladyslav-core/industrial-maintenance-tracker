package com.vladyslav.industrialmaintenancetracker.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EquipmentEditForm {

    @NotBlank
    @Size(min = 2, max = 120)
    private String name;

    @NotBlank
    @Size(min = 2, max = 50)
    private String inventoryNumber;

    @Size(max = 1000)
    private String description;

    @NotBlank
    @Size(min = 2, max = 120)
    private String location;

    public EquipmentEditForm() {
    }

    public EquipmentEditForm(
            String name,
            String inventoryNumber,
            String description,
            String location
    ) {
        this.name = name;
        this.inventoryNumber = inventoryNumber;
        this.description = description;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInventoryNumber() {
        return inventoryNumber;
    }

    public void setInventoryNumber(String inventoryNumber) {
        this.inventoryNumber = inventoryNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}