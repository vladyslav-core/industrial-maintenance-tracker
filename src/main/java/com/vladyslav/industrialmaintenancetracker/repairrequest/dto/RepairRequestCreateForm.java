package com.vladyslav.industrialmaintenancetracker.repairrequest.dto;

import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RepairRequestCreateForm {

    @NotNull
    private Long equipmentId;

    @NotBlank
    @Size(min = 5, max = 150)
    private String title;

    @NotBlank
    @Size(min = 10, max = 3000)
    private String description;

    private RepairRequestPriority priority;

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? null : title.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? null : description.trim();
    }

    public RepairRequestPriority getPriority() {
        return priority;
    }

    public void setPriority(RepairRequestPriority priority) {
        this.priority = priority;
    }
}
