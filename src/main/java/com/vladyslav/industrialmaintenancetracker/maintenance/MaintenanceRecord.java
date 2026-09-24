package com.vladyslav.industrialmaintenancetracker.maintenance;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequest;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;
import com.vladyslav.industrialmaintenancetracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "maintenance_records")
public class MaintenanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false, updatable = false)
    private Equipment equipment;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repair_request_id", nullable = false, unique = true, updatable = false)
    private RepairRequest repairRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by_id", nullable = false, updatable = false)
    private User performedBy;

    @Column(name = "work_description", nullable = false, updatable = false, columnDefinition = "text")
    private String workDescription;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MaintenanceRecord() {
    }

    public MaintenanceRecord(RepairRequest request) {
        Objects.requireNonNull(request, "Repair request must not be null");
        if (request.getStatus() != RepairRequestStatus.CLOSED) {
            throw new IllegalArgumentException("Only a closed request can produce a maintenance record");
        }

        String result = Objects.requireNonNull(request.getResolution(), "Result must not be null");
        if (result.isBlank() || !result.equals(result.strip())) {
            throw new IllegalArgumentException("Result must be nonblank and trimmed");
        }

        this.repairRequest = request;
        this.equipment = Objects.requireNonNull(request.getEquipment(), "Equipment must not be null");
        this.performedBy = Objects.requireNonNull(request.getAssignedTechnician(), "Technician must not be null");
        this.workDescription = result;
        this.performedAt = Objects.requireNonNull(request.getCompletedAt(), "Completion time must not be null");
    }

    public Long getId() {
        return id;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public RepairRequest getRepairRequest() {
        return repairRequest;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public String getWorkDescription() {
        return workDescription;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
