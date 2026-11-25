package com.vladyslav.industrialmaintenancetracker.dashboard;

import com.vladyslav.industrialmaintenancetracker.dashboard.dto.AdminDashboard;
import com.vladyslav.industrialmaintenancetracker.dashboard.dto.DashboardData;
import com.vladyslav.industrialmaintenancetracker.dashboard.dto.TechnicianDashboard;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestPriority;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.UserService;
import com.vladyslav.industrialmaintenancetracker.user.dto.CurrentUserSummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private static final PageRequest PREVIEW = PageRequest.of(0, 5);
    private static final List<RepairRequestStatus> OPEN_STATUSES = List.of(
            RepairRequestStatus.NEW, RepairRequestStatus.ASSIGNED,
            RepairRequestStatus.IN_PROGRESS, RepairRequestStatus.COMPLETED
    );

    private final UserService userService;
    private final RepairRequestRepository requestRepository;

    public DashboardService(UserService userService, RepairRequestRepository requestRepository) {
        this.userService = userService;
        this.requestRepository = requestRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardData getDashboard(Long viewerId, Role authenticatedRole) {
        if (viewerId == null || authenticatedRole == null) {
            throw new AccessDeniedException("Account is unavailable");
        }
        CurrentUserSummary viewer = userService.findActiveCurrentUserSummary(viewerId)
                .orElseThrow(() -> new AccessDeniedException("Account is unavailable"));
        if (viewer.role() != authenticatedRole) {
            throw new AccessDeniedException("Account role has changed. Sign in again.");
        }

        return switch (viewer.role()) {
            case ADMIN -> administratorDashboard(viewer);
            case TECHNICIAN -> technicianDashboard(viewer);
            case REQUESTER -> new DashboardData(viewer, null, null,
                    requestRepository.findLatestDashboardRequestsByAuthor(viewer.id(), PREVIEW));
        };
    }

    private DashboardData administratorDashboard(CurrentUserSummary viewer) {
        AdminDashboard counts = new AdminDashboard(
                requestRepository.countByStatus(RepairRequestStatus.NEW),
                requestRepository.countByStatus(RepairRequestStatus.ASSIGNED),
                requestRepository.countByStatus(RepairRequestStatus.IN_PROGRESS),
                requestRepository.countByStatus(RepairRequestStatus.COMPLETED),
                requestRepository.countByPriorityAndStatusIn(RepairRequestPriority.CRITICAL, OPEN_STATUSES)
        );
        return new DashboardData(viewer, counts, null,
                requestRepository.findLatestDashboardRequests(PREVIEW));
    }

    private DashboardData technicianDashboard(CurrentUserSummary viewer) {
        TechnicianDashboard work = new TechnicianDashboard(
                requestRepository.countByAssignedTechnician_IdAndStatus(viewer.id(), RepairRequestStatus.ASSIGNED),
                requestRepository.countByAssignedTechnician_IdAndStatus(viewer.id(), RepairRequestStatus.IN_PROGRESS),
                requestRepository.findLatestDashboardAssignments(viewer.id(), RepairRequestStatus.ASSIGNED, PREVIEW),
                requestRepository.findLatestDashboardAssignments(viewer.id(), RepairRequestStatus.IN_PROGRESS, PREVIEW)
        );
        return new DashboardData(viewer, null, work, List.of());
    }
}
