package com.vladyslav.industrialmaintenancetracker.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface EquipmentRepository
        extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByInventoryNumberIgnoreCase(
            String inventoryNumber
    );

    boolean existsByInventoryNumberIgnoreCase(
            String inventoryNumber
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT equipment FROM Equipment equipment WHERE equipment.id = :id")
    Optional<Equipment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT equipment
        FROM Equipment equipment
        WHERE (
            LOWER(equipment.name) LIKE
                CONCAT('%', LOWER(:searchTerm), '%')
            OR LOWER(equipment.inventoryNumber) LIKE
                CONCAT('%', LOWER(:searchTerm), '%')
        )
        AND (
            :status IS NULL
            OR equipment.status = :status
        )
        ORDER BY equipment.name ASC,
                 equipment.inventoryNumber ASC
        """)
    List<Equipment> search(
            @Param("searchTerm") String searchTerm,
            @Param("status") EquipmentStatus status
    );
}
