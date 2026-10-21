package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotAvailableException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestEditConflictException;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestWorkConflictException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestResolutionForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestTechnicianOption;
import com.vladyslav.industrialmaintenancetracker.exception.UserNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCreateForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestDetails;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestEquipmentOption;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestListItem;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestEditForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestFilters;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestFilterOption;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestListPage;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecord;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecordRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class RepairRequestService {

    private final RepairRequestRepository requestRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final MaintenanceRecordRepository maintenanceRepository;

    public RepairRequestService(
            RepairRequestRepository requestRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            MaintenanceRecordRepository maintenanceRepository
    ) {
        this.requestRepository = requestRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    @Transactional(readOnly = true)
    public List<RepairRequestEquipmentOption> availableEquipment() {
        return equipmentRepository.search("", null).stream()
                .filter(equipment -> equipment.getStatus() != EquipmentStatus.DECOMMISSIONED)
                .map(equipment -> new RepairRequestEquipmentOption(
                        equipment.getId(), equipment.getName(), equipment.getInventoryNumber()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RepairRequestListItem> getList(Long viewerId) {
        User viewer = activeViewer(viewerId);
        return requestRepository.findVisible(
                        viewer.getId(),
                        viewer.getRole() == Role.ADMIN,
                        viewer.getRole() == Role.TECHNICIAN
                ).stream()
                .map(this::listItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public RepairRequestDetails getDetails(Long requestId, Long viewerId) {
        User viewer = activeViewer(viewerId);
        RepairRequest request = visibleRequest(requestId, viewer);

        return new RepairRequestDetails(
                request.getId(), request.getTitle(), request.getDescription(),
                request.getEquipment().getId(), request.getEquipment().getName(),
                request.getEquipment().getInventoryNumber(), request.getEquipment().getLocation(),
                request.getCreatedBy().getFullName(), technicianName(request),
                request.getPriority(), request.getStatus(),
                request.getResolution(), request.getCancellationReason(),
                request.getCreatedAt(), request.getUpdatedAt(), request.getAssignedAt(),
                request.getStartedAt(), request.getCompletedAt(), request.getClosedAt(),
                canEdit(request, viewer),
                viewer.getRole() == Role.ADMIN && manageable(request),
                viewer.getRole() == Role.ADMIN && request.getStatus() == RepairRequestStatus.ASSIGNED,
                isAssignedTechnician(request, viewer) && request.getStatus() == RepairRequestStatus.ASSIGNED,
                isAssignedTechnician(request, viewer) && request.getStatus() == RepairRequestStatus.IN_PROGRESS,
                viewer.getRole() == Role.ADMIN && request.getStatus() == RepairRequestStatus.COMPLETED,
                viewer.getRole() == Role.ADMIN && request.getStatus() == RepairRequestStatus.COMPLETED
        );
    }

    @Transactional(readOnly = true)
    public RepairRequestListPage getListPage(RepairRequestFilters filters, Long viewerId) {
        User viewer = activeViewer(viewerId);
        boolean admin = viewer.getRole() == Role.ADMIN;
        boolean technician = viewer.getRole() == Role.TECHNICIAN;
        String search = filters.getSearch() == null ? "" : filters.getSearch().trim();
        String escaped = search.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        RepairRequestScope scope = technician ? filters.getScope() : RepairRequestScope.ALL;
        List<RepairRequest> visible = requestRepository.findVisible(viewerId, admin, technician);
        List<RepairRequestListItem> items = requestRepository.searchVisible(
                viewerId, admin, technician, "%" + escaped + "%", searchId(search),
                filters.getStatus(), filters.getPriority(), filters.getEquipmentId(),
                admin ? filters.getAuthorId() : null, filters.getTechnicianId(),
                scope == RepairRequestScope.CREATED_BY_ME, scope == RepairRequestScope.ASSIGNED_TO_ME
        ).stream().map(this::listItem).toList();
        return new RepairRequestListPage(
                items,
                options(visible.stream().map(request -> new RepairRequestFilterOption(
                        request.getEquipment().getId(), request.getEquipment().getName()
                        + " (" + request.getEquipment().getInventoryNumber() + ")"
                ))),
                admin ? options(visible.stream().map(request -> new RepairRequestFilterOption(
                        request.getCreatedBy().getId(), request.getCreatedBy().getFullName()
                ))) : List.of(),
                options(visible.stream().filter(request -> request.getAssignedTechnician() != null)
                        .map(request -> new RepairRequestFilterOption(
                                request.getAssignedTechnician().getId(), technicianName(request)
                        ))),
                admin, technician
        );
    }

    @Transactional(readOnly = true)
    public RepairRequestEditForm getEditForm(Long requestId, Long viewerId) {
        User viewer = activeViewer(viewerId);
        RepairRequest request = visibleRequest(requestId, viewer);
        requireEditable(request, viewer);
        return new RepairRequestEditForm(request.getTitle(), request.getDescription(), request.getUpdatedAt());
    }

    @Transactional
    public void updateDetails(Long requestId, RepairRequestEditForm form, Long viewerId) {
        User viewer = activeViewer(viewerId);
        RepairRequest request = visibleRequest(requestId, viewer);
        requireEditable(request, viewer);
        if (!Objects.equals(request.getUpdatedAt(), form.getUpdatedAt())) {
            throw new RepairRequestEditConflictException();
        }
        Instant changedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (!changedAt.isAfter(form.getUpdatedAt())) {
            changedAt = form.getUpdatedAt().plus(1, ChronoUnit.MICROS);
        }
        int changed = requestRepository.updateOwnNewDetails(
                requestId, viewerId, RepairRequestStatus.NEW,
                form.getTitle().trim(), form.getDescription().trim(), form.getUpdatedAt(), changedAt
        );
        if (changed != 1) {
            throw new RepairRequestEditConflictException();
        }
    }

    @Transactional
    public RepairRequest create(RepairRequestCreateForm form, Long authorId) {
        Equipment equipment = equipmentRepository.findByIdForUpdate(form.getEquipmentId())
                .orElseThrow(() -> new EquipmentNotFoundException(form.getEquipmentId()));
        if (equipment.getStatus() == EquipmentStatus.DECOMMISSIONED) {
            throw new EquipmentNotAvailableException(equipment.getId());
        }

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new UserNotFoundException(authorId));
        if (!author.isActive()) {
            throw new AccessDeniedException("Inactive users cannot create repair requests");
        }

        RepairRequest request = new RepairRequest(
                form.getTitle().trim(),
                form.getDescription().trim(),
                equipment,
                author,
                form.getPriority()
        );
        return requestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<RepairRequestTechnicianOption> availableTechnicians(Long administratorId) {
        activeAdmin(administratorId);
        return userRepository.findByRoleAndActiveTrueOrderByFullNameAscIdAsc(Role.TECHNICIAN).stream()
                .map(user -> new RepairRequestTechnicianOption(user.getId(), user.getFullName(), user.getEmail()))
                .toList();
    }

    @Transactional
    public void assign(Long requestId, Long technicianId, Long administratorId) {
        activeAdmin(administratorId);
        if (technicianId == null) {
            throw new InvalidRepairRequestActionException("Select an active technician.");
        }
        // Lock the technician before the request; account restrictions will share this lock in step 12.
        User technician = userRepository.findByIdForUpdate(technicianId)
                .orElseThrow(() -> new InvalidRepairRequestActionException("Select an active technician."));
        lockedRequest(requestId).assignTo(technician, Instant.now());
    }

    @Transactional
    public void unassign(Long requestId, Long administratorId) {
        activeAdmin(administratorId);
        lockedRequest(requestId).unassign();
    }

    @Transactional
    public void changePriority(Long requestId, RepairRequestPriority priority, Long administratorId) {
        activeAdmin(administratorId);
        lockedRequest(requestId).changePriority(priority);
    }

    @Transactional
    public void closeRequest(Long requestId, Instant expectedUpdatedAt, Long administratorId) {
        activeAdmin(administratorId);
        RepairRequest request = lockedRequest(requestId);
        if (request.getStatus() == RepairRequestStatus.CLOSED) {
            return;
        }
        if (expectedUpdatedAt == null || !Objects.equals(request.getUpdatedAt(), expectedUpdatedAt)) {
            throw new InvalidRepairRequestActionException(
                    "The repair result has changed. Reload the request before accepting it."
            );
        }
        request.close(Instant.now());
        // Both writes belong to this transaction. A failed history insert rolls back closure as well.
        requestRepository.flush();
        maintenanceRepository.saveAndFlush(new MaintenanceRecord(request));
    }

    @Transactional
    public void returnForRework(Long requestId, Instant expectedUpdatedAt, Long administratorId) {
        activeAdmin(administratorId);
        RepairRequest request = lockedRequest(requestId);
        if (expectedUpdatedAt == null || !Objects.equals(request.getUpdatedAt(), expectedUpdatedAt)) {
            throw new InvalidRepairRequestActionException(
                    "The repair result has changed. Reload the request before returning it for rework."
            );
        }
        request.returnForRework();
    }

    @Transactional
    public void startWork(Long requestId, Long technicianId) {
        User technician = activeTechnician(technicianId);
        RepairRequest request = lockedTechnicianRequest(requestId, technician);
        request.startWork(Instant.now());
    }

    @Transactional(readOnly = true)
    public RepairRequestResolutionForm getResolutionForm(Long requestId, Long technicianId) {
        User technician = activeTechnician(technicianId);
        RepairRequest request = visibleRequest(requestId, technician);
        requireAssignedTechnician(request, technician);
        requireWorkInProgress(request);
        return new RepairRequestResolutionForm(request.getResolution(), request.getUpdatedAt());
    }

    @Transactional
    public void saveResolution(Long requestId, RepairRequestResolutionForm form, Long technicianId) {
        User technician = activeTechnician(technicianId);
        RepairRequest request = lockedTechnicianRequest(requestId, technician);
        requireCurrentWorkForm(request, form);
        request.saveResolution(form.getResolution());
    }

    @Transactional
    public void completeWork(Long requestId, RepairRequestResolutionForm form, Long technicianId) {
        User technician = activeTechnician(technicianId);
        RepairRequest request = lockedTechnicianRequest(requestId, technician);
        requireCurrentWorkForm(request, form);
        request.completeWork(form.getResolution(), Instant.now());
    }

    private User activeTechnician(Long technicianId) {
        User technician = activeViewer(technicianId);
        if (technician.getRole() != Role.TECHNICIAN) {
            throw new AccessDeniedException("Only a technician can perform repair work");
        }
        return technician;
    }

    private RepairRequest lockedTechnicianRequest(Long requestId, User technician) {
        RepairRequest request = requestRepository.findVisibleByIdForUpdate(requestId, technician.getId())
                .orElseThrow(() -> new RepairRequestNotFoundException(requestId));
        requireAssignedTechnician(request, technician);
        return request;
    }

    private boolean isAssignedTechnician(RepairRequest request, User viewer) {
        return viewer.getRole() == Role.TECHNICIAN && request.getAssignedTechnician() != null
                && request.getAssignedTechnician().getId().equals(viewer.getId());
    }

    private void requireAssignedTechnician(RepairRequest request, User technician) {
        if (!isAssignedTechnician(request, technician)) {
            throw new AccessDeniedException("Only the currently assigned technician can work on this request");
        }
    }

    private void requireWorkInProgress(RepairRequest request) {
        if (request.getStatus() != RepairRequestStatus.IN_PROGRESS) {
            throw new RepairRequestWorkConflictException();
        }
    }

    private void requireCurrentWorkForm(RepairRequest request, RepairRequestResolutionForm form) {
        requireWorkInProgress(request);
        if (form.getUpdatedAt() == null || !Objects.equals(request.getUpdatedAt(), form.getUpdatedAt())) {
            throw new RepairRequestWorkConflictException();
        }
    }

    private User activeAdmin(Long administratorId) {
        User administrator = activeViewer(administratorId);
        if (administrator.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only an administrator can manage assignments and priority");
        }
        return administrator;
    }

    private RepairRequest lockedRequest(Long requestId) {
        return requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new RepairRequestNotFoundException(requestId));
    }

    private boolean manageable(RepairRequest request) {
        return request.getStatus() == RepairRequestStatus.NEW
                || request.getStatus() == RepairRequestStatus.ASSIGNED
                || request.getStatus() == RepairRequestStatus.IN_PROGRESS;
    }

    private RepairRequest visibleRequest(Long requestId, User viewer) {
        return requestRepository.findVisibleById(
                requestId, viewer.getId(), viewer.getRole() == Role.ADMIN,
                viewer.getRole() == Role.TECHNICIAN
        ).orElseThrow(() -> new RepairRequestNotFoundException(requestId));
    }

    private boolean canEdit(RepairRequest request, User viewer) {
        return request.getCreatedBy().getId().equals(viewer.getId())
                && request.getStatus() == RepairRequestStatus.NEW
                && request.getAssignedTechnician() == null;
    }

    private void requireEditable(RepairRequest request, User viewer) {
        if (!request.getCreatedBy().getId().equals(viewer.getId())) {
            throw new AccessDeniedException("Only the author can edit request details");
        }
        if (!canEdit(request, viewer)) {
            throw new RepairRequestEditConflictException();
        }
    }

    private RepairRequestListItem listItem(RepairRequest request) {
        return new RepairRequestListItem(
                request.getId(), request.getTitle(),
                request.getEquipment().getId(), request.getEquipment().getName(),
                request.getCreatedBy().getFullName(), technicianName(request),
                request.getPriority(), request.getStatus(), request.getCreatedAt(), request.getUpdatedAt()
        );
    }

    private List<RepairRequestFilterOption> options(Stream<RepairRequestFilterOption> options) {
        return options.distinct().sorted(Comparator
                .comparing(RepairRequestFilterOption::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RepairRequestFilterOption::id)).toList();
    }

    private Long searchId(String search) {
        try {
            long id = Long.parseLong(search.startsWith("#") ? search.substring(1) : search);
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private User activeViewer(Long viewerId) {
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new AccessDeniedException("Account is unavailable"));
        if (!viewer.isActive()) {
            throw new AccessDeniedException("Account is inactive");
        }
        return viewer;
    }

    private String technicianName(RepairRequest request) {
        return request.getAssignedTechnician() == null
                ? null : request.getAssignedTechnician().getFullName();
    }
}
