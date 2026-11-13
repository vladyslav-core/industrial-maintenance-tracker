package com.vladyslav.industrialmaintenancetracker.maintenance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    Optional<MaintenanceRecord> findByRepairRequestId(Long repairRequestId);

    @Query("""
            SELECT record FROM MaintenanceRecord record
            JOIN FETCH record.equipment
            JOIN FETCH record.repairRequest request
            JOIN FETCH request.createdBy
            LEFT JOIN FETCH request.assignedTechnician
            JOIN FETCH record.performedBy
            WHERE request.status = com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus.CLOSED
              AND (:equipmentId IS NULL OR record.equipment.id = :equipmentId)
            ORDER BY record.performedAt DESC, record.id DESC
            """)
    List<MaintenanceRecord> findHistory(@Param("equipmentId") Long equipmentId);
}
