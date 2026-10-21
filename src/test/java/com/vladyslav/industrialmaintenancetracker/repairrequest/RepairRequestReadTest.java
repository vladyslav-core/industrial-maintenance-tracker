package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestDetails;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestListItem;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestFilters;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@Import(RepairRequestService.class)
class RepairRequestReadTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired private RepairRequestService service;
    @Autowired private RepairRequestRepository requests;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private User admin;
    private User requester;
    private User technician;
    private User otherTechnician;
    private Equipment equipment;
    private Long ownNew;
    private Long ownAssigned;
    private Long technicianOwn;
    private Long technicianSelfAssigned;
    private Long foreignRequest;
    private Long adminOwn;

    @BeforeEach
    void seedVisibleAndHiddenRequests() {
        admin = saveUser("Admin", "admin@example.com", Role.ADMIN);
        requester = saveUser("Requester", "requester@example.com", Role.REQUESTER);
        technician = saveUser("Technician", "tech@example.com", Role.TECHNICIAN);
        otherTechnician = saveUser("Other Technician", "other-tech@example.com", Role.TECHNICIAN);
        User outsider = saveUser("Other Requester", "other@example.com", Role.REQUESTER);
        equipment = equipmentRepository.saveAndFlush(new Equipment(
                "Hydraulic press", "PRESS-READ-001", null, "Workshop A"
        ));

        ownNew = saveRequest(requester, null);
        ownAssigned = saveRequest(requester, technician);
        technicianOwn = saveRequest(technician, null);
        technicianSelfAssigned = saveRequest(technician, technician);
        foreignRequest = saveRequest(outsider, otherTechnician);
        adminOwn = saveRequest(admin, null);
        jdbc.update("UPDATE repair_requests SET created_at = TIMESTAMPTZ '2026-10-20 10:00:00+00'");
        jdbc.update("UPDATE repair_requests SET created_at = TIMESTAMPTZ '2026-10-19 10:00:00+00' WHERE id = ?", adminOwn);
        entityManager.clear();
    }

    @Test
    void adminSeesAllSortedByCreationThenId() {
        assertThat(service.getList(admin.getId())).extracting(RepairRequestListItem::id)
                .containsExactly(foreignRequest, technicianSelfAssigned, technicianOwn, ownAssigned, ownNew, adminOwn);
    }

    @Test
    void technicianSeesOwnAndAssignedWithoutDuplicates() {
        assertThat(service.getList(technician.getId())).extracting(RepairRequestListItem::id)
                .containsExactly(technicianSelfAssigned, technicianOwn, ownAssigned);
    }

    @Test
    void requesterSeesOnlyOwnIncludingUnassignedRequests() {
        assertThat(service.getList(requester.getId())).extracting(RepairRequestListItem::id)
                .containsExactly(ownAssigned, ownNew);
    }

    @Test
    void authorAssignedTechnicianAndAdminCanReadDetails() {
        for (User viewer : new User[]{requester, technician, admin}) {
            RepairRequestDetails details = service.getDetails(ownAssigned, viewer.getId());
            assertThat(details.id()).isEqualTo(ownAssigned);
            assertThat(details.equipmentId()).isEqualTo(equipment.getId());
            assertThat(details.technicianName()).isEqualTo("Technician");
            assertThat(details.authorName()).isEqualTo("Requester");
            assertThat(details.status()).isEqualTo(RepairRequestStatus.ASSIGNED);
            assertThat(details.assignedAt()).isNotNull();
        }
    }

    @Test
    void inaccessibleAndMissingIdsAreUnavailable() {
        assertThatThrownBy(() -> service.getDetails(foreignRequest, requester.getId()))
                .isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.getDetails(foreignRequest, technician.getId()))
                .isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.getDetails(Long.MAX_VALUE, admin.getId()))
                .isInstanceOf(RepairRequestNotFoundException.class);
    }

    @Test
    void reassignmentRemovesOldTechniciansAccessAndPreservesAuthorsAccess() {
        jdbc.update("UPDATE repair_requests SET assigned_technician_id = ? WHERE id = ?",
                otherTechnician.getId(), ownAssigned);
        entityManager.clear();

        assertThat(service.getList(technician.getId())).extracting(RepairRequestListItem::id)
                .doesNotContain(ownAssigned);
        assertThatThrownBy(() -> service.getDetails(ownAssigned, technician.getId()))
                .isInstanceOf(RepairRequestNotFoundException.class);
        assertThat(service.getDetails(ownAssigned, otherTechnician.getId()).id()).isEqualTo(ownAssigned);
        assertThat(service.getDetails(ownAssigned, requester.getId()).id()).isEqualTo(ownAssigned);
    }

    @Test
    void detailsUseCurrentEquipmentLocation() {
        jdbc.update("UPDATE equipment SET location = 'Workshop B' WHERE id = ?", equipment.getId());
        entityManager.clear();

        assertThat(service.getDetails(ownNew, requester.getId()).location()).isEqualTo("Workshop B");
    }

    @Test
    void deactivatedAccountCannotListOrRead() {
        jdbc.update("UPDATE users SET active = false WHERE id = ?", requester.getId());
        entityManager.clear();

        assertThatThrownBy(() -> service.getList(requester.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getDetails(ownNew, requester.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void emptyFiltersPreserveVisibilityAndStableOrder(Role role) {
        Long viewer = switch (role) {
            case ADMIN -> admin.getId();
            case TECHNICIAN -> technician.getId();
            case REQUESTER -> requester.getId();
        };
        assertThat(service.getListPage(new RepairRequestFilters(), viewer).requests())
                .isEqualTo(service.getList(viewer));
    }

    @Test
    void searchesByNumberWithOptionalHashPrefix() {
        RepairRequestFilters filters = new RepairRequestFilters();
        for (String search : new String[]{ownNew.toString(), "#" + ownNew}) {
            filters.setSearch(search);
            assertThat(service.getListPage(filters, requester.getId()).requests())
                    .extracting(RepairRequestListItem::id).containsExactly(ownNew);
        }
    }

    @Test
    void searchesTitleAndDescriptionIgnoringCaseAndOuterWhitespace() {
        jdbc.update("UPDATE repair_requests SET title = 'Seal crack', description = 'Needs pressure check' WHERE id = ?", ownNew);
        entityManager.clear();
        RepairRequestFilters filters = new RepairRequestFilters();
        for (String search : new String[]{"  CRACK  ", "PrEsSuRe"}) {
            filters.setSearch(search);
            assertThat(service.getListPage(filters, requester.getId()).requests())
                    .extracting(RepairRequestListItem::id).containsExactly(ownNew);
        }
    }

    @Test
    void treatsLikeWildcardsAndEscapeCharacterAsLiteralText() {
        jdbc.update("UPDATE repair_requests SET title = 'Leak at 50%_power!' WHERE id = ?", ownNew);
        entityManager.clear();
        RepairRequestFilters filters = new RepairRequestFilters();
        for (String search : new String[]{"%_", "!"}) {
            filters.setSearch(search);
            assertThat(service.getListPage(filters, admin.getId()).requests())
                    .extracting(RepairRequestListItem::id).containsExactly(ownNew);
        }
    }

    @Test
    void appliesStatusPriorityEquipmentAuthorAndTechnicianFiltersTogether() {
        RepairRequestFilters filters = new RepairRequestFilters();
        filters.setStatus(RepairRequestStatus.ASSIGNED);
        assertThat(service.getListPage(filters, admin.getId()).requests())
                .extracting(RepairRequestListItem::id)
                .containsExactly(foreignRequest, technicianSelfAssigned, ownAssigned);
        filters.setStatus(null);
        jdbc.update("UPDATE repair_requests SET priority = 'CRITICAL' WHERE id = ?", ownNew);
        entityManager.clear();
        filters.setPriority(RepairRequestPriority.CRITICAL);
        assertThat(service.getListPage(filters, admin.getId()).requests())
                .extracting(RepairRequestListItem::id).containsExactly(ownNew);
        Equipment pump = equipmentRepository.saveAndFlush(new Equipment("Water pump", "PUMP-READ-001", null, "Workshop B"));
        jdbc.update("UPDATE repair_requests SET equipment_id = ? WHERE id = ?", pump.getId(), ownNew);
        entityManager.clear();
        filters.setPriority(null);
        filters.setEquipmentId(pump.getId());
        assertThat(service.getListPage(filters, admin.getId()).requests())
                .extracting(RepairRequestListItem::id).containsExactly(ownNew);
        filters.setEquipmentId(null);
        filters.setAuthorId(requester.getId());
        assertThat(service.getListPage(filters, admin.getId()).requests())
                .extracting(RepairRequestListItem::id).containsExactly(ownAssigned, ownNew);
        filters.setStatus(RepairRequestStatus.ASSIGNED);
        filters.setTechnicianId(technician.getId());
        assertThat(service.getListPage(filters, admin.getId()).requests())
                .extracting(RepairRequestListItem::id).containsExactly(ownAssigned);
        filters.setAuthorId(null);
        assertThat(service.getListPage(filters, admin.getId()).requests())
                .extracting(RepairRequestListItem::id).containsExactly(technicianSelfAssigned, ownAssigned);
    }

    @Test
    void technicianCanNarrowToCreatedOrAssignedRequests() {
        RepairRequestFilters filters = new RepairRequestFilters();
        filters.setScope(RepairRequestScope.CREATED_BY_ME);
        assertThat(service.getListPage(filters, technician.getId()).requests())
                .extracting(RepairRequestListItem::id).containsExactly(technicianSelfAssigned, technicianOwn);
        filters.setScope(RepairRequestScope.ASSIGNED_TO_ME);
        assertThat(service.getListPage(filters, technician.getId()).requests())
                .extracting(RepairRequestListItem::id).containsExactly(technicianSelfAssigned, ownAssigned);
    }

    @Test
    void forgedFiltersCannotExposeHiddenRequestsOrOptions() {
        Equipment hidden = equipmentRepository.saveAndFlush(new Equipment("Hidden pump", "HIDDEN-READ-001", null, "Workshop C"));
        jdbc.update("UPDATE repair_requests SET equipment_id = ? WHERE id = ?", hidden.getId(), foreignRequest);
        entityManager.clear();
        RepairRequestFilters filters = new RepairRequestFilters();
        filters.setSearch("#" + foreignRequest);
        filters.setAuthorId(admin.getId());
        filters.setTechnicianId(otherTechnician.getId());
        filters.setScope(RepairRequestScope.ASSIGNED_TO_ME);
        var page = service.getListPage(filters, requester.getId());
        assertThat(page.requests()).isEmpty();
        assertThat(page.authors()).isEmpty();
        assertThat(page.equipment()).noneMatch(option -> option.id().equals(hidden.getId()));
        assertThat(page.technicians()).noneMatch(option -> option.id().equals(otherTechnician.getId()));
    }

    @Test
    void emptySearchResultKeepsVisibleFilterOptions() {
        RepairRequestFilters filters = new RepairRequestFilters();
        filters.setSearch("unmatched phrase");
        var page = service.getListPage(filters, requester.getId());
        assertThat(page.requests()).isEmpty();
        assertThat(page.equipment()).hasSize(1);
        assertThat(page.technicians()).hasSize(1);
    }

    private User saveUser(String name, String email, Role role) {
        return users.saveAndFlush(new User(name, email, "hashed-password", role));
    }

    // Assignment actions are added in step 07; these rows test read permissions independently.
    private Long saveRequest(User author, User assigned) {
        RepairRequest saved = requests.saveAndFlush(new RepairRequest(
                "Oil leak in press", "Hydraulic oil leaking around the seal",
                equipment, author, RepairRequestPriority.MEDIUM
        ));
        if (assigned != null) {
            jdbc.update("""
                    UPDATE repair_requests SET status = 'ASSIGNED',
                        assigned_technician_id = ?, assigned_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, assigned.getId(), saved.getId());
        }
        return saved.getId();
    }
}
