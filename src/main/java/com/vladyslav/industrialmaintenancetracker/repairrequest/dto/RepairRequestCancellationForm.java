package com.vladyslav.industrialmaintenancetracker.repairrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class RepairRequestCancellationForm {

    @NotBlank(message = "Enter a cancellation reason.")
    @Size(min = 5, max = 500, message = "The cancellation reason must contain 5 to 500 characters.")
    private String cancellationReason;

    @NotNull(message = "Reload the request before cancelling it.")
    private Instant updatedAt;

    public RepairRequestCancellationForm() {
    }

    public RepairRequestCancellationForm(String cancellationReason, Instant updatedAt) {
        setCancellationReason(cancellationReason);
        this.updatedAt = updatedAt;
    }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason == null ? null : cancellationReason.trim();
    }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
