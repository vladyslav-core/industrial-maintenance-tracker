package com.vladyslav.industrialmaintenancetracker.repairrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class RepairRequestEditForm {

    @NotBlank
    @Size(min = 5, max = 150)
    private String title;

    @NotBlank
    @Size(min = 10, max = 3000)
    private String description;

    @NotNull
    private Instant updatedAt;

    public RepairRequestEditForm() {
    }

    public RepairRequestEditForm(String title, String description, Instant updatedAt) {
        setTitle(title);
        setDescription(description);
        this.updatedAt = updatedAt;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title == null ? null : title.trim(); }
    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description == null ? null : description.trim();
    }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
