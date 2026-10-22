package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "repair_requests")
public class RepairRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 3000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false, updatable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_technician_id")
    private User assignedTechnician;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RepairRequestPriority priority = RepairRequestPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RepairRequestStatus status = RepairRequestStatus.NEW;

    @Column(columnDefinition = "text")
    private String resolution;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected RepairRequest() {
    }

    public RepairRequest(
            String title,
            String description,
            Equipment equipment,
            User createdBy,
            RepairRequestPriority priority
    ) {
        this.title = title;
        this.description = description;
        this.equipment = Objects.requireNonNull(equipment, "Equipment must not be null");
        this.createdBy = Objects.requireNonNull(createdBy, "Author must not be null");
        this.priority = priority == null ? RepairRequestPriority.MEDIUM : priority;
        this.status = RepairRequestStatus.NEW;
    }

    public void assignTo(User technician, Instant assignedAt) {
        requireManageable();
        if (technician == null || !technician.isActive() || technician.getRole() != Role.TECHNICIAN) {
            throw new InvalidRepairRequestActionException("Select an active technician.");
        }
        if (assignedTechnician != null && assignedTechnician.getId().equals(technician.getId())) {
            throw new InvalidRepairRequestActionException("This technician is already assigned to the request.");
        }
        Objects.requireNonNull(assignedAt, "Assignment time must not be null");
        this.assignedTechnician = technician;
        this.assignedAt = assignedAt;
        if (status == RepairRequestStatus.NEW) {
            status = RepairRequestStatus.ASSIGNED;
        }
    }

    public void unassign() {
        if (status != RepairRequestStatus.ASSIGNED) {
            throw new InvalidRepairRequestActionException("Assignment can only be removed before work starts.");
        }
        assignedTechnician = null;
        assignedAt = null;
        status = RepairRequestStatus.NEW;
    }

    public void changePriority(RepairRequestPriority priority) {
        requireManageable();
        if (priority == null) {
            throw new InvalidRepairRequestActionException("Select a priority.");
        }
        this.priority = priority;
    }

    public void startWork(Instant startedAt) {
        if (status != RepairRequestStatus.ASSIGNED) {
            throw new InvalidRepairRequestActionException("Work can only be started for an ASSIGNED request.");
        }
        Objects.requireNonNull(startedAt, "Start time must not be null");
        if (this.startedAt == null) {
            this.startedAt = startedAt;
        }
        status = RepairRequestStatus.IN_PROGRESS;
    }

    public void saveResolution(String resolution) {
        requireInProgress();
        this.resolution = normalizedResolution(resolution);
    }

    public void completeWork(String resolution, Instant completedAt) {
        requireInProgress();
        String result = normalizedResolution(resolution);
        if (result == null) {
            throw new InvalidRepairRequestActionException("Describe the repair result before completing the request.");
        }
        Objects.requireNonNull(completedAt, "Completion time must not be null");
        this.resolution = result;
        this.completedAt = completedAt;
        status = RepairRequestStatus.COMPLETED;
    }

    public void cancel(String cancellationReason, Instant closedAt) {
        if (status != RepairRequestStatus.NEW && status != RepairRequestStatus.ASSIGNED
                && status != RepairRequestStatus.IN_PROGRESS) {
            throw new InvalidRepairRequestActionException(
                    "Only NEW, ASSIGNED or IN_PROGRESS requests can be cancelled."
            );
        }
        String reason = cancellationReason == null ? null : cancellationReason.trim();
        if (reason == null || reason.isBlank() || reason.length() < 5 || reason.length() > 500) {
            throw new InvalidRepairRequestActionException("The cancellation reason must contain 5 to 500 characters.");
        }
        Objects.requireNonNull(closedAt, "Cancellation time must not be null");
        this.cancellationReason = reason;
        this.closedAt = closedAt;
        status = RepairRequestStatus.CANCELLED;
    }

    public void close(Instant closedAt) {
        if (status != RepairRequestStatus.COMPLETED) {
            throw new InvalidRepairRequestActionException("Only a COMPLETED repair can be closed.");
        }
        this.closedAt = Objects.requireNonNull(closedAt, "Closure time must not be null");
        status = RepairRequestStatus.CLOSED;
    }

    public void returnForRework() {
        if (status != RepairRequestStatus.COMPLETED) {
            throw new InvalidRepairRequestActionException("Only a COMPLETED repair can be returned for rework.");
        }
        completedAt = null;
        status = RepairRequestStatus.IN_PROGRESS;
    }

    private void requireInProgress() {
        if (status != RepairRequestStatus.IN_PROGRESS) {
            throw new InvalidRepairRequestActionException("Repair results can only be changed while work is IN_PROGRESS.");
        }
    }

    private String normalizedResolution(String resolution) {
        return resolution == null || resolution.isBlank() ? null : resolution.trim();
    }

    private void requireManageable() {
        if (status != RepairRequestStatus.NEW && status != RepairRequestStatus.ASSIGNED
                && status != RepairRequestStatus.IN_PROGRESS) {
            throw new InvalidRepairRequestActionException(
                    "Assignment and priority can only be changed for NEW, ASSIGNED or IN_PROGRESS requests."
            );
        }
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getAssignedTechnician() {
        return assignedTechnician;
    }

    public RepairRequestPriority getPriority() {
        return priority;
    }

    public RepairRequestStatus getStatus() {
        return status;
    }

    public String getResolution() {
        return resolution;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
