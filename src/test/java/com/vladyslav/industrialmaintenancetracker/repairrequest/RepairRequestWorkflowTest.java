package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecordRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestDetails;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=bootstrap.workflow@example.com",
        "APP_ADMIN_PASSWORD=StrongPassword#2026"
})
@AutoConfigureMockMvc
// No outer test transaction: HTTP operations use real services and commit independently.
class RepairRequestWorkflowTest {

    private static final String PASSWORD = "StrongPassword#2026";
    private static final String RESULT = "Seal replaced. Pressure test passed. <script>result</script>";
    private static final String CORRECTION = "Seal checked again. Extended pressure test passed.";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired private MockMvc mvc;
    @Autowired private RepairRequestService repairs;
    @Autowired private RepairRequestRepository requests;
    @Autowired private EquipmentRepository equipment;
    @Autowired private UserRepository users;
    @Autowired private MaintenanceRecordRepository maintenance;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private final EnumMap<Actor, Long> userIds = new EnumMap<>(Actor.class);
    private final EnumMap<Actor, MockHttpSession> sessions = new EnumMap<>(Actor.class);
    private Long equipmentId;
    private Long otherEquipmentId;

    @BeforeEach
    void seedIsolatedContainerDatabase() {
        clearDatabase();
        sessions.clear();
        userIds.clear();
        String hash = encoder.encode(PASSWORD);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Actor actor : Actor.values()) {
                userIds.put(actor, users.saveAndFlush(new User("Workflow " + actor.name(), actor.email, hash, actor.role)).getId());
            }
            equipmentId = equipment.saveAndFlush(new Equipment("Workflow Press", "WF-PRESS-001", null, "Workshop A")).getId();
            otherEquipmentId = equipment.saveAndFlush(new Equipment("Other Press", "WF-PRESS-002", null, "Workshop B")).getId();
        });
    }

    @AfterEach
    void clearDatabase() {
        jdbc.update("DELETE FROM maintenance_records");
        jdbc.update("DELETE FROM repair_requests");
        jdbc.update("DELETE FROM equipment");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void fullWorkflowUsesRealLoginFormsReworkReassignmentAndExactlyOneHistoryRecord() throws Exception {
        Long id = create(Actor.AUTHOR);
        assertThat(current(id).status()).isEqualTo(RepairRequestStatus.NEW);
        mvc.perform(get(card(id)).session(login(Actor.OTHER_TECHNICIAN))).andExpect(status().isNotFound());
        String edit = open(Actor.AUTHOR, card(id) + "/edit");
        mvc.perform(versionedPost(Actor.AUTHOR, card(id) + "/edit", edit)
                        .param("title", "Updated hydraulic oil leak").param("description", "Updated description after another inspection")
                        .param("equipmentId", otherEquipmentId.toString()).param("createdById", userIds.get(Actor.ADMIN).toString())
                        .param("priority", "CRITICAL").param("status", "CLOSED"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(card(id)));
        assertThat(current(id).title()).isEqualTo("Updated hydraulic oil leak");
        assertThat(current(id).equipmentId()).isEqualTo(equipmentId);
        assertThat(current(id).priority()).isEqualTo(RepairRequestPriority.MEDIUM);
        succeed(Action.PRIORITY, id);
        succeed(Action.ASSIGN, id);
        succeed(Action.UNASSIGN, id);
        assertThat(current(id).status()).isEqualTo(RepairRequestStatus.NEW);
        succeed(Action.ASSIGN, id);
        assertThat(open(Actor.AUTHOR, card(id))).doesNotContain("/edit\"").doesNotContain(card(id) + "/cancel");
        succeed(Action.START, id);
        var started = current(id).startedAt();

        String work = open(Actor.TECHNICIAN, card(id) + "/complete");
        mvc.perform(versionedPost(Actor.TECHNICIAN, card(id) + "/resolution", work)
                        .param("resolution", "Draft marker <script>draft</script>"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(card(id) + "/complete"));
        assertThat(open(Actor.AUTHOR, card(id))).doesNotContain("Draft marker");
        String draft = open(Actor.TECHNICIAN, card(id) + "/complete");
        assertThat(draft).contains("Draft marker &lt;script&gt;draft&lt;/script&gt;");
        mvc.perform(versionedPost(Actor.TECHNICIAN, card(id) + "/complete", draft).param("resolution", "   "))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("resolutionForm", "resolution"));
        assertThat(current(id).status()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        complete(Actor.TECHNICIAN, id, RESULT);
        var firstCompletion = current(id).completedAt();
        assertThat(maintenance.count()).isZero();
        assertThat(open(Actor.AUTHOR, card(id))).contains("&lt;script&gt;result&lt;/script&gt;")
                .doesNotContain("<script>result</script>");

        String originalReview = open(Actor.ADMIN, card(id));
        mvc.perform(versionedPost(Actor.ADMIN, card(id) + "/return", originalReview))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(card(id)));
        assertThat(current(id).completedAt()).isNull();
        assertThat(current(id).resolution()).isEqualTo(RESULT);
        mvc.perform(securedPost(Actor.ADMIN, card(id) + "/assign")
                        .param("technicianId", userIds.get(Actor.OTHER_TECHNICIAN).toString()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        mvc.perform(get(card(id)).session(login(Actor.TECHNICIAN))).andExpect(status().isNotFound());
        mvc.perform(versionedPost(Actor.TECHNICIAN, card(id) + "/complete", draft).param("resolution", "Old technician's result"))
                .andExpect(status().isNotFound());
        assertThat(open(Actor.OTHER_TECHNICIAN, card(id) + "/complete")).contains("Seal replaced.");
        complete(Actor.OTHER_TECHNICIAN, id, CORRECTION);
        var reviewed = current(id);
        assertThat(reviewed.startedAt()).isEqualTo(started);
        assertThat(reviewed.completedAt()).isAfter(firstCompletion);
        mvc.perform(versionedPost(Actor.ADMIN, card(id) + "/close", originalReview))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        assertThat(current(id).status()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(maintenance.count()).isZero();

        String finalReview = open(Actor.ADMIN, card(id));
        mvc.perform(versionedPost(Actor.ADMIN, card(id) + "/close", finalReview))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        var closed = current(id);
        var history = maintenance.findByRepairRequestId(id).orElseThrow();
        assertThat(closed.status()).isEqualTo(RepairRequestStatus.CLOSED);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(history.getWorkDescription()).isEqualTo(CORRECTION);
        assertThat(history.getPerformedAt()).isEqualTo(reviewed.completedAt());
        assertThat(jdbc.queryForObject("SELECT performed_by_id FROM maintenance_records WHERE repair_request_id = ?", Long.class, id))
                .isEqualTo(userIds.get(Actor.OTHER_TECHNICIAN));
        mvc.perform(versionedPost(Actor.ADMIN, card(id) + "/close", originalReview))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        assertThat(current(id)).isEqualTo(closed);
        assertThat(maintenance.count()).isEqualTo(1);
        assertThat(maintenance.findByRepairRequestId(id).orElseThrow().getId()).isEqualTo(history.getId());
        assertThat(open(Actor.ADMIN, card(id))).doesNotContain(card(id) + "/close").doesNotContain(card(id) + "/return");
        assertThat(current(id).priority()).isEqualTo(RepairRequestPriority.HIGH);
    }

    @ParameterizedTest
    @EnumSource(value = Actor.class, names = {"ADMIN", "AUTHOR", "TECHNICIAN"})
    void everyRoleCreatesOwnUnassignedRequestAndCanCancelItWithEscapedReason(Actor actor) throws Exception {
        Long id = create(actor);
        var created = current(id);
        assertThat(created.status()).isEqualTo(RepairRequestStatus.NEW);
        assertThat(created.priority()).isEqualTo(RepairRequestPriority.MEDIUM);
        assertThat(created.technicianName()).isNull();
        assertThat(jdbc.queryForObject("SELECT created_by_id FROM repair_requests WHERE id = ?", Long.class, id)).isEqualTo(userIds.get(actor));
        String form = open(actor, card(id));
        mvc.perform(versionedPost(actor, card(id) + "/cancel", form)
                        .param("cancellationReason", "  Duplicate <script>request</script>  ")
                        .param("closedAt", "1970-01-01T00:00:00Z").param("status", "CLOSED"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        var cancelled = current(id);
        assertThat(cancelled.status()).isEqualTo(RepairRequestStatus.CANCELLED);
        assertThat(cancelled.cancellationReason()).isEqualTo("Duplicate <script>request</script>");
        assertThat(cancelled.closedAt()).isAfterOrEqualTo(created.createdAt());
        assertThat(cancelled.completedAt()).isNull();
        assertThat(maintenance.count()).isZero();
        assertThat(open(actor, card(id))).contains("Duplicate &lt;script&gt;request&lt;/script&gt;")
                .doesNotContain("<script>request</script>").doesNotContain(card(id) + "/cancel");
    }

    @Test
    void listFiltersNeverExpandVisibilityAndDirectHiddenIdsStayUnavailable() throws Exception {
        Long own = create(Actor.AUTHOR);
        Long assigned = create(Actor.OTHER_REQUESTER);
        Long technicianOwn = create(Actor.TECHNICIAN);
        Long hidden = create(Actor.ADMIN);
        succeed(Action.ASSIGN, assigned);
        assertThat(listIds(open(Actor.AUTHOR, "/repair-requests?scope=ALL&authorId=" + userIds.get(Actor.ADMIN))))
                .containsExactlyInAnyOrder(own);
        assertThat(listIds(open(Actor.TECHNICIAN, "/repair-requests"))).containsExactlyInAnyOrder(assigned, technicianOwn);
        assertThat(listIds(open(Actor.TECHNICIAN, "/repair-requests?scope=CREATED_BY_ME"))).containsExactlyInAnyOrder(technicianOwn);
        assertThat(listIds(open(Actor.TECHNICIAN, "/repair-requests?scope=ASSIGNED_TO_ME"))).containsExactlyInAnyOrder(assigned);
        assertThat(listIds(open(Actor.ADMIN, "/repair-requests"))).containsExactlyInAnyOrder(own, assigned, technicianOwn, hidden);
        for (Actor actor : List.of(Actor.AUTHOR, Actor.TECHNICIAN)) {
            mvc.perform(get(card(hidden)).session(login(actor))).andExpect(status().isNotFound())
                    .andExpect(view().name("repair-requests/not-found"));
            mvc.perform(get(card(hidden) + "/edit").session(login(actor))).andExpect(status().isNotFound());
        }
    }

    @Test
    void staleEditFormKeepsInputButCannotOverwriteCommittedChanges() throws Exception {
        Long id = create(Actor.AUTHOR);
        String stale = open(Actor.AUTHOR, card(id) + "/edit");
        mvc.perform(versionedPost(Actor.AUTHOR, card(id) + "/edit", stale)
                        .param("title", "Current saved problem").param("description", "Current saved problem description"))
                .andExpect(status().is3xxRedirection());
        var committed = current(id);
        mvc.perform(versionedPost(Actor.AUTHOR, card(id) + "/edit", stale)
                        .param("title", "Stale attempted title").param("description", "Stale attempted description"))
                .andExpect(status().isConflict()).andExpect(model().attribute("canSave", false))
                .andExpect(content().string(containsString("Stale attempted title")))
                .andExpect(content().string(not(containsString(">Save changes</button>"))));
        assertThat(current(id)).isEqualTo(committed);
    }

    @ParameterizedTest
    @EnumSource(value = Action.class, names = {"RESOLUTION", "COMPLETE"})
    void staleWorkFormCannotOverwriteNewerDraft(Action action) throws Exception {
        Long id = prepare(RepairRequestStatus.IN_PROGRESS);
        String stale = open(Actor.TECHNICIAN, card(id) + "/complete");
        mvc.perform(versionedPost(Actor.TECHNICIAN, card(id) + "/resolution", stale).param("resolution", "Current saved draft"))
                .andExpect(status().is3xxRedirection());
        var committed = current(id);
        mvc.perform(versionedPost(Actor.TECHNICIAN, action.path(id), stale).param("resolution", "Stale attempted result"))
                .andExpect(status().isConflict()).andExpect(model().attribute("canSave", false))
                .andExpect(content().string(containsString("Stale attempted result")))
                .andExpect(content().string(not(containsString(">Complete repair</button>"))));
        assertThat(current(id)).isEqualTo(committed);
        assertThat(maintenance.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Actor.class, names = {"ADMIN", "AUTHOR", "TECHNICIAN"})
    void realAuthenticatedRolesCannotUseOtherRolesActionsEvenWithValidCsrf(Actor actor) throws Exception {
        Long id = prepare(RepairRequestStatus.ASSIGNED);
        var before = current(id);
        List<Action> forbidden = switch (actor) {
            case ADMIN -> List.of(Action.EDIT, Action.START, Action.RESOLUTION, Action.COMPLETE);
            case AUTHOR -> List.of(Action.ASSIGN, Action.UNASSIGN, Action.PRIORITY, Action.START,
                    Action.RESOLUTION, Action.COMPLETE, Action.RETURN, Action.CLOSE);
            case TECHNICIAN -> List.of(Action.EDIT, Action.ASSIGN, Action.UNASSIGN, Action.PRIORITY, Action.RETURN, Action.CLOSE);
            default -> throw new IllegalArgumentException("Not a role-matrix actor");
        };
        for (Action action : forbidden) {
            mvc.perform(validPost(action, id, actor).param("_csrf", csrf(actor))).andExpect(status().isForbidden());
        }
        assertThat(current(id)).isEqualTo(before);
        assertThat(maintenance.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void everyChangingRequestRouteRejectsMissingAndInvalidCsrfWithoutDatabaseChanges(Action action) throws Exception {
        Long id = action == Action.CREATE ? null : prepare(action.state);
        var before = id == null ? null : current(id);
        long count = requests.count();
        for (String token : new String[]{null, "invalid-token"}) {
            var request = validPost(action, id, action.actor);
            if (token != null) request.param("_csrf", token);
            mvc.perform(request).andExpect(status().isForbidden());
            assertThat(requests.count()).isEqualTo(count);
            if (id != null) assertThat(current(id)).isEqualTo(before);
            assertThat(maintenance.count()).isZero();
        }
    }

    @Test
    void assignedTechnicianStillCannotCancelSomeoneElsesRequest() throws Exception {
        Long id = prepare(RepairRequestStatus.ASSIGNED);
        var before = current(id);
        mvc.perform(validPost(Action.CANCEL, id, Actor.TECHNICIAN).param("_csrf", csrf(Actor.TECHNICIAN)))
                .andExpect(status().isForbidden());
        assertThat(current(id)).isEqualTo(before);
    }

    @Test
    void cancellationValidationRendersTheRealCardWithoutChangingRequest() throws Exception {
        Long id = create(Actor.AUTHOR);
        var before = current(id);
        mvc.perform(versionedPost(Actor.AUTHOR, card(id) + "/cancel", open(Actor.AUTHOR, card(id)))
                        .param("cancellationReason", "  four  "))
                .andExpect(status().isOk()).andExpect(view().name("repair-requests/details"))
                .andExpect(model().attributeHasFieldErrors("cancellationForm", "cancellationReason"))
                .andExpect(content().string(containsString("four")));
        assertThat(current(id)).isEqualTo(before);
    }

    @Test
    void cachedRequesterSessionCannotCreateOrListRequestsAfterDeactivation() throws Exception {
        Long id = create(Actor.AUTHOR);
        mvc.perform(securedPost(Actor.ADMIN, "/users/" + userIds.get(Actor.AUTHOR) + "/deactivate"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        mvc.perform(get("/repair-requests").session(login(Actor.AUTHOR))).andExpect(status().isForbidden());
        mvc.perform(get("/equipment/" + equipmentId).session(login(Actor.AUTHOR)))
                .andExpect(status().isForbidden());
        mvc.perform(validPost(Action.CREATE, null, Actor.AUTHOR).param("_csrf", csrf(Actor.AUTHOR)))
                .andExpect(status().isForbidden());
        assertThat(requests.count()).isEqualTo(1);
        assertThat(current(id).status()).isEqualTo(RepairRequestStatus.NEW);
    }

    @Test
    void cachedAdminAuthorityCannotBypassCurrentDatabaseRoleForRepairManagement() throws Exception {
        Long id = create(Actor.ADMIN);
        users.saveAndFlush(new User("Second Administrator", "second.admin@example.com", encoder.encode(PASSWORD), Role.ADMIN));
        mvc.perform(securedPost(Actor.ADMIN, "/users/" + userIds.get(Actor.ADMIN) + "/edit")
                        .param("fullName", "Workflow ADMIN").param("email", Actor.ADMIN.email).param("role", "REQUESTER"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        mvc.perform(validPost(Action.ASSIGN, id, Actor.ADMIN).param("_csrf", csrf(Actor.ADMIN)))
                .andExpect(status().isForbidden());
        assertThat(current(id).status()).isEqualTo(RepairRequestStatus.NEW);
        assertThat(open(Actor.ADMIN, card(id))).doesNotContain(card(id) + "/assign");
    }

    @Test
    void anonymousVisitorNeedsLoginEvenWithValidCsrf() throws Exception {
        mvc.perform(get("/repair-requests")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        var form = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/repair-requests").session((MockHttpSession) form.getRequest().getSession(false))
                        .param("_csrf", hidden(form.getResponse().getContentAsString(), "_csrf"))
                        .param("equipmentId", equipmentId.toString()).param("title", "Unauthenticated fault")
                        .param("description", "This request must never be persisted"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        assertThat(requests.count()).isZero();
    }

    @Test
    void realLoginRejectsInactiveDatabaseAccount() throws Exception {
        mvc.perform(securedPost(Actor.ADMIN, "/users/" + userIds.get(Actor.AUTHOR) + "/deactivate"))
                .andExpect(status().is3xxRedirection());
        var form = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/login").session((MockHttpSession) form.getRequest().getSession(false))
                        .param("_csrf", hidden(form.getResponse().getContentAsString(), "_csrf"))
                        .param("username", Actor.AUTHOR.email).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void managementPagesEnforceOpenRequestRestrictionsAndReleaseResourcesAfterClosure() throws Exception {
        Long id = prepare(RepairRequestStatus.COMPLETED);
        var before = current(id);
        mvc.perform(securedPost(Actor.ADMIN, "/equipment/" + equipmentId + "/status").param("status", "DECOMMISSIONED"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(securedPost(Actor.ADMIN, "/users/" + userIds.get(Actor.TECHNICIAN) + "/deactivate"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(securedPost(Actor.ADMIN, "/users/" + userIds.get(Actor.TECHNICIAN) + "/edit")
                        .param("fullName", "Changed technician").param("email", "changed@example.com").param("role", "REQUESTER"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("userEditForm", "role"));
        assertThat(current(id)).isEqualTo(before);
        assertThat(users.findById(userIds.get(Actor.TECHNICIAN)).orElseThrow().isActive()).isTrue();
        assertThat(equipment.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
        succeed(Action.CLOSE, id);
        mvc.perform(securedPost(Actor.ADMIN, "/equipment/" + equipmentId + "/status").param("status", "DECOMMISSIONED"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        mvc.perform(securedPost(Actor.ADMIN, "/users/" + userIds.get(Actor.TECHNICIAN) + "/deactivate"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        assertThat(maintenance.count()).isEqualTo(1);
        assertThat(equipment.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.DECOMMISSIONED);
        assertThat(users.findById(userIds.get(Actor.TECHNICIAN)).orElseThrow().isActive()).isFalse();

        for (Actor viewer : List.of(Actor.ADMIN, Actor.AUTHOR, Actor.OTHER_TECHNICIAN)) {
            assertThat(open(viewer, "/equipment/" + equipmentId))
                    .contains("Maintenance history", "Workflow TECHNICIAN", "DECOMMISSIONED",
                            "Seal replaced. Pressure test passed. &lt;script&gt;result&lt;/script&gt;");
        }
        mvc.perform(get("/equipment/" + equipmentId).session(login(Actor.TECHNICIAN)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(Actor.class)
    void equipmentHistoryShowsTheAcceptedRepairWithoutExpandingRequestAccess(Actor actor) throws Exception {
        Long id = prepare(RepairRequestStatus.COMPLETED);
        succeed(Action.CLOSE, id);
        var closed = current(id);

        String html = open(actor, "/equipment/" + equipmentId
                + "?viewerId=" + userIds.get(Actor.ADMIN) + "&admin=true");
        assertThat(html).contains(
                "Maintenance history", "#" + id, closed.title(), closed.authorName(),
                closed.description(), closed.technicianName(), closed.createdAt().toString(),
                closed.completedAt().toString(), closed.closedAt().toString(),
                "Seal replaced. Pressure test passed. &lt;script&gt;result&lt;/script&gt;"
        ).doesNotContain("<script>result</script>");

        boolean canView = actor == Actor.ADMIN || actor == Actor.AUTHOR || actor == Actor.TECHNICIAN;
        assertThat(listIds(html)).isEqualTo(canView ? Set.of(id) : Set.of());
        mvc.perform(get(card(id)).session(login(actor)))
                .andExpect(status().is(canView ? 200 : 404));
        assertThat(maintenance.count()).isEqualTo(1);
    }

    @Test
    void equipmentHistoryAppearsOnlyAfterAcceptanceAndExcludesCancelledRequests() throws Exception {
        Long completed = prepare(RepairRequestStatus.COMPLETED);
        Long cancelled = create(Actor.AUTHOR);
        succeed(Action.CANCEL, cancelled);

        assertThat(open(Actor.AUTHOR, "/equipment/" + equipmentId))
                .contains("No maintenance records found.")
                .doesNotContain("Seal replaced.", "#" + completed, "#" + cancelled);

        succeed(Action.CLOSE, completed);
        String history = open(Actor.AUTHOR, "/equipment/" + equipmentId);
        assertThat(listIds(history)).containsExactly(completed);
        assertThat(history).doesNotContain("No maintenance records found.", "#" + cancelled);
        assertThat(open(Actor.AUTHOR, "/equipment/" + otherEquipmentId))
                .contains("No maintenance records found.").doesNotContain("#" + completed);
    }

    @Test
    void currentDatabaseRoleControlsHistoryLinksForAnExistingTechnicianSession() throws Exception {
        Long id = prepare(RepairRequestStatus.COMPLETED);
        succeed(Action.CLOSE, id);
        // Work already authenticated this technician before the database role changes.
        jdbc.update("UPDATE users SET role = 'REQUESTER' WHERE id = ?", userIds.get(Actor.TECHNICIAN));

        String html = open(Actor.TECHNICIAN, "/equipment/" + equipmentId);
        assertThat(html).contains("#" + id, "Seal replaced. Pressure test passed.");
        assertThat(listIds(html)).isEmpty();
        mvc.perform(get(card(id)).session(login(Actor.TECHNICIAN))).andExpect(status().isNotFound());
    }

    private enum Actor {
        ADMIN("workflow.admin@example.com", Role.ADMIN),
        AUTHOR("workflow.author@example.com", Role.REQUESTER),
        OTHER_REQUESTER("workflow.other@example.com", Role.REQUESTER),
        TECHNICIAN("workflow.tech@example.com", Role.TECHNICIAN),
        OTHER_TECHNICIAN("workflow.other-tech@example.com", Role.TECHNICIAN);
        final String email;
        final Role role;
        Actor(String email, Role role) { this.email = email; this.role = role; }
    }

    private enum Action {
        CREATE(Actor.AUTHOR, RepairRequestStatus.NEW), EDIT(Actor.AUTHOR, RepairRequestStatus.NEW),
        ASSIGN(Actor.ADMIN, RepairRequestStatus.NEW), UNASSIGN(Actor.ADMIN, RepairRequestStatus.ASSIGNED),
        PRIORITY(Actor.ADMIN, RepairRequestStatus.NEW), START(Actor.TECHNICIAN, RepairRequestStatus.ASSIGNED),
        RESOLUTION(Actor.TECHNICIAN, RepairRequestStatus.IN_PROGRESS), COMPLETE(Actor.TECHNICIAN, RepairRequestStatus.IN_PROGRESS),
        RETURN(Actor.ADMIN, RepairRequestStatus.COMPLETED), CLOSE(Actor.ADMIN, RepairRequestStatus.COMPLETED),
        CANCEL(Actor.AUTHOR, RepairRequestStatus.NEW);
        final Actor actor;
        final RepairRequestStatus state;
        Action(Actor actor, RepairRequestStatus state) { this.actor = actor; this.state = state; }
        String path(Long id) { return this == CREATE ? "/repair-requests" : card(id) + "/" + name().toLowerCase(java.util.Locale.ROOT); }
    }

    private MockHttpSession login(Actor actor) throws Exception {
        if (sessions.containsKey(actor)) return sessions.get(actor);
        var form = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var result = mvc.perform(post("/login").session((MockHttpSession) form.getRequest().getSession(false))
                        .param("_csrf", hidden(form.getResponse().getContentAsString(), "_csrf"))
                        .param("username", actor.email).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(actor.email)).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        sessions.put(actor, session);
        return session;
    }

    private String open(Actor actor, String path) throws Exception {
        return mvc.perform(get(path).session(login(actor))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String csrf(Actor actor) throws Exception { return hidden(open(actor, "/"), "_csrf"); }

    private MockHttpServletRequestBuilder securedPost(Actor actor, String path) throws Exception {
        return post(path).session(login(actor)).param("_csrf", csrf(actor));
    }

    private MockHttpServletRequestBuilder versionedPost(Actor actor, String path, String html) throws Exception {
        return post(path).session(login(actor)).param("_csrf", hidden(html, "_csrf")).param("updatedAt", hidden(html, "updatedAt"));
    }

    private Long create(Actor actor) throws Exception {
        String form = open(actor, "/repair-requests/new?equipmentId=" + equipmentId);
        var result = mvc.perform(post("/repair-requests").session(login(actor)).param("_csrf", hidden(form, "_csrf"))
                        .param("equipmentId", equipmentId.toString()).param("title", "Oil leak in press")
                        .param("description", "Hydraulic oil leaking around the seal").param("priority", "")
                        .param("createdById", userIds.get(Actor.OTHER_REQUESTER).toString())
                        .param("assignedTechnicianId", userIds.get(Actor.TECHNICIAN).toString()).param("status", "CLOSED"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage")).andReturn();
        return Long.valueOf(result.getResponse().getRedirectedUrl().substring("/repair-requests/".length()));
    }

    private Long prepare(RepairRequestStatus state) throws Exception {
        Long id = create(Actor.AUTHOR);
        if (state == RepairRequestStatus.NEW) return id;
        succeed(Action.ASSIGN, id);
        if (state == RepairRequestStatus.ASSIGNED) return id;
        succeed(Action.START, id);
        if (state == RepairRequestStatus.COMPLETED) complete(Actor.TECHNICIAN, id, RESULT);
        return id;
    }

    private void complete(Actor actor, Long id, String result) throws Exception {
        String form = open(actor, card(id) + "/complete");
        mvc.perform(versionedPost(actor, card(id) + "/complete", form).param("resolution", result))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(card(id)))
                .andExpect(flash().attributeExists("successMessage"));
    }

    private ResultActions succeed(Action action, Long id) throws Exception {
        return mvc.perform(validPost(action, id, action.actor).param("_csrf", csrf(action.actor)))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
    }

    private MockHttpServletRequestBuilder validPost(Action action, Long id, Actor actor) throws Exception {
        var request = post(action.path(id)).session(login(actor));
        if (id != null) request.param("updatedAt", current(id).updatedAt().toString());
        switch (action) {
            case CREATE -> request.param("equipmentId", equipmentId.toString()).param("title", "Oil leak in press")
                    .param("description", "Hydraulic oil leaking around the seal");
            case EDIT -> request.param("title", "Changed fault title").param("description", "Changed fault description");
            case ASSIGN -> request.param("technicianId", userIds.get(Actor.TECHNICIAN).toString());
            case PRIORITY -> request.param("priority", "HIGH");
            case RESOLUTION, COMPLETE -> request.param("resolution", RESULT);
            case CANCEL -> request.param("cancellationReason", "Duplicate request for this fault");
            default -> { }
        }
        return request;
    }

    private RepairRequestDetails current(Long id) { return repairs.getDetails(id, userIds.get(Actor.ADMIN)); }
    private static String card(Long id) { return "/repair-requests/" + id; }

    private static String hidden(String html, String name) {
        var matcher = Pattern.compile("<input\\b(?=[^>]*\\bname=\"" + Pattern.quote(name)
                + "\")[^>]*\\bvalue=\"([^\"]*)\"[^>]*>").matcher(html);
        assertThat(matcher.find()).as("rendered hidden field %s", name).isTrue();
        return matcher.group(1);
    }

    private Set<Long> listIds(String html) {
        return Pattern.compile("href=\"/repair-requests/(\\d+)\"").matcher(html).results()
                .map(match -> Long.valueOf(match.group(1))).collect(Collectors.toSet());
    }
}
