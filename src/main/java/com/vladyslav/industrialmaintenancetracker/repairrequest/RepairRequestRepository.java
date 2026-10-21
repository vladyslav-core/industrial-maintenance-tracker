package com.vladyslav.industrialmaintenancetracker.repairrequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, Long> {

    @Query("""
            SELECT request FROM RepairRequest request
            JOIN FETCH request.equipment
            JOIN FETCH request.createdBy
            LEFT JOIN FETCH request.assignedTechnician
            WHERE :admin = true
               OR request.createdBy.id = :viewerId
               OR (:technician = true AND request.assignedTechnician.id = :viewerId)
            ORDER BY request.createdAt DESC, request.id DESC
            """)
    List<RepairRequest> findVisible(
            @Param("viewerId") Long viewerId,
            @Param("admin") boolean admin,
            @Param("technician") boolean technician
    );

    @Query("""
            SELECT request FROM RepairRequest request
            JOIN FETCH request.equipment
            JOIN FETCH request.createdBy
            LEFT JOIN FETCH request.assignedTechnician
            WHERE request.id = :id
              AND (:admin = true
                   OR request.createdBy.id = :viewerId
                   OR (:technician = true AND request.assignedTechnician.id = :viewerId))
            """)
    Optional<RepairRequest> findVisibleById(
            @Param("id") Long id,
            @Param("viewerId") Long viewerId,
            @Param("admin") boolean admin,
            @Param("technician") boolean technician
    );
}
