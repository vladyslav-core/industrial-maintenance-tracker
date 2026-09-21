package com.vladyslav.industrialmaintenancetracker.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByInventoryNumberIgnoreCase(String inventoryNumber);

    boolean existsByInventoryNumberIgnoreCase(String inventoryNumber);
}