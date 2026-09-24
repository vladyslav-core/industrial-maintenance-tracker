package com.vladyslav.industrialmaintenancetracker.maintenance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    Optional<MaintenanceRecord> findByRepairRequestId(Long repairRequestId);
}
