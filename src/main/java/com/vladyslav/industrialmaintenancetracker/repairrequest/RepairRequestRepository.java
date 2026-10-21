package com.vladyslav.industrialmaintenancetracker.repairrequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM RepairRequest request WHERE request.id = :id")
    Optional<RepairRequest> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request FROM RepairRequest request
            WHERE request.id = :id
              AND (request.createdBy.id = :viewerId OR request.assignedTechnician.id = :viewerId)
            """)
    Optional<RepairRequest> findVisibleByIdForUpdate(@Param("id") Long id, @Param("viewerId") Long viewerId);

    @Query("""
            SELECT request FROM RepairRequest request
            JOIN FETCH request.equipment
            JOIN FETCH request.createdBy
            LEFT JOIN FETCH request.assignedTechnician
            WHERE (:admin = true
                   OR request.createdBy.id = :viewerId
                   OR (:technician = true AND request.assignedTechnician.id = :viewerId))
              AND (LOWER(request.title) LIKE :pattern ESCAPE '!'
                   OR LOWER(request.description) LIKE :pattern ESCAPE '!'
                   OR request.id = :searchId)
              AND (:status IS NULL OR request.status = :status)
              AND (:priority IS NULL OR request.priority = :priority)
              AND (:equipmentId IS NULL OR request.equipment.id = :equipmentId)
              AND (:authorId IS NULL OR request.createdBy.id = :authorId)
              AND (:technicianId IS NULL OR request.assignedTechnician.id = :technicianId)
              AND (:createdOnly = false OR request.createdBy.id = :viewerId)
              AND (:assignedOnly = false OR request.assignedTechnician.id = :viewerId)
            ORDER BY request.createdAt DESC, request.id DESC
            """)
    List<RepairRequest> searchVisible(
            @Param("viewerId") Long viewerId,
            @Param("admin") boolean admin,
            @Param("technician") boolean technician,
            @Param("pattern") String pattern,
            @Param("searchId") Long searchId,
            @Param("status") RepairRequestStatus status,
            @Param("priority") RepairRequestPriority priority,
            @Param("equipmentId") Long equipmentId,
            @Param("authorId") Long authorId,
            @Param("technicianId") Long technicianId,
            @Param("createdOnly") boolean createdOnly,
            @Param("assignedOnly") boolean assignedOnly
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RepairRequest request
            SET request.title = :title, request.description = :description,
                request.updatedAt = :changedAt
            WHERE request.id = :id AND request.createdBy.id = :authorId
              AND request.status = :newStatus AND request.assignedTechnician IS NULL
              AND request.updatedAt = :expectedUpdatedAt
            """)
    int updateOwnNewDetails(
            @Param("id") Long id,
            @Param("authorId") Long authorId,
            @Param("newStatus") RepairRequestStatus newStatus,
            @Param("title") String title,
            @Param("description") String description,
            @Param("expectedUpdatedAt") Instant expectedUpdatedAt,
            @Param("changedAt") Instant changedAt
    );

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
