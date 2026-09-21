package com.vladyslav.industrialmaintenancetracker.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidEquipmentStatusTransitionException;

import java.util.Objects;
import java.time.Instant;

@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "inventory_number", nullable = false, length = 50)
    private String inventoryNumber;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 120)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EquipmentStatus status = EquipmentStatus.OPERATIONAL;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Equipment() {
    }

    public Equipment(
            String name,
            String inventoryNumber,
            String description,
            String location
    ) {
        this.name = name;
        this.inventoryNumber = inventoryNumber;
        this.description = description;
        this.location = location;
        this.status = EquipmentStatus.OPERATIONAL;
    }

    public void updateDetails(
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

    public void changeStatus(EquipmentStatus targetStatus) {
        Objects.requireNonNull(
                targetStatus,
                "Equipment status must not be null"
        );

        if (
                status == EquipmentStatus.DECOMMISSIONED
                        && targetStatus != EquipmentStatus.DECOMMISSIONED
        ) {
            throw new InvalidEquipmentStatusTransitionException(
                    status,
                    targetStatus
            );
        }

        this.status = targetStatus;
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