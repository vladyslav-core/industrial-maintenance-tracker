package com.vladyslav.industrialmaintenancetracker.repairrequest;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, Long> {
}
