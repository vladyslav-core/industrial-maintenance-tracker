package com.vladyslav.industrialmaintenancetracker.repairrequest.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class RepairRequestResolutionForm {

    private String resolution;

    @NotNull
    private Instant updatedAt;

    public RepairRequestResolutionForm() {
    }

    public RepairRequestResolutionForm(String resolution, Instant updatedAt) {
        this.resolution = resolution;
        this.updatedAt = updatedAt;
    }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
