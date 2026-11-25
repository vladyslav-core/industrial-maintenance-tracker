package com.vladyslav.industrialmaintenancetracker.dashboard;

import com.vladyslav.industrialmaintenancetracker.dashboard.dto.AdminDashboard;
import com.vladyslav.industrialmaintenancetracker.dashboard.dto.DashboardData;
import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecord;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecordRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequest;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestPriority;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestService;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestPreview;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import com.vladyslav.industrialmaintenancetracker.user.UserService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.vladyslav.industrialmaintenancetracker.dashboard.DashboardReadTest$SqlCapture"
})
@Import({DashboardService.class, UserService.class, RepairRequestService.class})
// Let each service open its own real transaction, including REPEATABLE_READ.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class DashboardReadTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T08:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired private DashboardService dashboards;
    @Autowired private RepairRequestService repairs;
    @Autowired private RepairRequestRepository requests;
    @Autowired private UserRepository users;
    @Autowired private EquipmentRepository equipment;
    @Autowired private MaintenanceRecordRepository history;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoBean private PasswordEncoder passwordEncoder;

    private User admin;
    private User technician;
    private User otherTechnician;
    private User requester;
    private User otherRequester;
    private Equipment press;
    private Equipment pump;
    private final List<Long> ids = new ArrayList<>();

    @BeforeEach
    void seedCommittedRequests() {
        admin = saveUser("Administrator", Role.ADMIN);
        technician = saveUser("Technician", Role.TECHNICIAN);
        otherTechnician = saveUser("Other technician", Role.TECHNICIAN);
        requester = saveUser("Requester", Role.REQUESTER);
        otherRequester = saveUser("Other requester", Role.REQUESTER);
        press = equipment.saveAndFlush(new Equipment("Dashboard press", "DASH-READ-A", null, "Workshop A"));
        pump = equipment.saveAndFlush(new Equipment("Dashboard pump", "DASH-READ-B", null, "Workshop B"));

        ids.clear();
        ids.add(saveRequest(requester, null, RepairRequestStatus.NEW, RepairRequestPriority.CRITICAL, press, CREATED));
        ids.add(saveRequest(requester, technician, RepairRequestStatus.ASSIGNED, RepairRequestPriority.HIGH, press, CREATED));
        ids.add(saveRequest(otherRequester, technician, RepairRequestStatus.IN_PROGRESS, RepairRequestPriority.MEDIUM, press, CREATED));
        ids.add(saveRequest(otherRequester, otherTechnician, RepairRequestStatus.COMPLETED, RepairRequestPriority.CRITICAL, press, CREATED));
        ids.add(saveRequest(requester, technician, RepairRequestStatus.CLOSED, RepairRequestPriority.CRITICAL, press, CREATED));
        ids.add(saveRequest(otherRequester, null, RepairRequestStatus.CANCELLED, RepairRequestPriority.CRITICAL, press, CREATED));
        ids.add(saveRequest(technician, null, RepairRequestStatus.NEW, RepairRequestPriority.LOW, press, CREATED));
        ids.add(saveRequest(technician, technician, RepairRequestStatus.IN_PROGRESS, RepairRequestPriority.MEDIUM, press, CREATED));
        ids.add(saveRequest(otherRequester, otherTechnician, RepairRequestStatus.ASSIGNED, RepairRequestPriority.MEDIUM, pump, CREATED));
        SqlCapture.clear();
    }

    @AfterEach
    void removeCommittedFixtures() {
        history.deleteAllInBatch();
        requests.deleteAllInBatch();
        equipment.deleteAllInBatch();
        users.deleteAllInBatch();
        SqlCapture.clear();
    }

    @Test
    void administratorCountsAllRequestsAndSeesTheLatestFiveIncludingFinalStates() {
        DashboardData data = dashboards.getDashboard(admin.getId(), Role.ADMIN);

        assertThat(data.administrator()).isEqualTo(new AdminDashboard(2, 2, 2, 1, 2));
        assertThat(data.technician()).isNull();
        assertThat(data.latestRequests()).extracting(RepairRequestPreview::id)
                .containsExactly(q(9), q(8), q(7), q(6), q(5));
        assertThat(data.latestRequests().getFirst()).isEqualTo(new RepairRequestPreview(
                q(9), "Dashboard request from Other requester", pump.getId(), "Dashboard pump",
                RepairRequestStatus.ASSIGNED, RepairRequestPriority.MEDIUM, CREATED
        ));
    }

    @ParameterizedTest
    @EnumSource(RepairRequestStatus.class)
    void criticalCountIncludesExactlyTheFourOpenStates(RepairRequestStatus state) {
        saveRequest(otherRequester, state == RepairRequestStatus.NEW || state == RepairRequestStatus.CANCELLED
                ? null : otherTechnician, state, RepairRequestPriority.CRITICAL, press, CREATED);
        boolean finalState = state == RepairRequestStatus.CLOSED || state == RepairRequestStatus.CANCELLED;

        assertThat(dashboards.getDashboard(admin.getId(), Role.ADMIN).administrator().criticalOpenCount())
                .isEqualTo(finalState ? 2 : 3);
    }

    @ParameterizedTest
    @EnumSource(RepairRequestPriority.class)
    void otherPrioritiesNeverIncreaseTheCriticalCount(RepairRequestPriority priority) {
        saveRequest(otherRequester, null, RepairRequestStatus.NEW, priority, press, CREATED);

        assertThat(dashboards.getDashboard(admin.getId(), Role.ADMIN).administrator().criticalOpenCount())
                .isEqualTo(priority == RepairRequestPriority.CRITICAL ? 3 : 2);
    }

    @Test
    void technicianWorkDependsOnAssignmentRatherThanAuthorshipAndDoesNotDuplicateSelfAssignedRequests() {
        saveRequest(technician, otherTechnician, RepairRequestStatus.ASSIGNED, RepairRequestPriority.CRITICAL,
                pump, CREATED.plusSeconds(60));

        var data = dashboards.getDashboard(technician.getId(), Role.TECHNICIAN);

        assertThat(data.administrator()).isNull();
        assertThat(data.latestRequests()).isEmpty();
        assertThat(data.technician().assignedCount()).isEqualTo(1);
        assertThat(data.technician().inProgressCount()).isEqualTo(2);
        assertThat(data.technician().assignedRequests()).extracting(RepairRequestPreview::id).containsExactly(q(2));
        assertThat(data.technician().inProgressRequests()).extracting(RepairRequestPreview::id).containsExactly(q(8), q(3));
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"ASSIGNED", "IN_PROGRESS"})
    void technicianCountsTheWholeAssignmentButLimitsInSqlAfterFiltering(RepairRequestStatus state) {
        List<Long> matching = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            matching.add(saveRequest(otherRequester, technician, state, RepairRequestPriority.MEDIUM,
                    press, CREATED.plusSeconds(60)));
            // These are newer and created by this technician, but assigned to someone else.
            saveRequest(technician, otherTechnician, state, RepairRequestPriority.CRITICAL,
                    pump, CREATED.plusSeconds(120));
        }
        Collections.reverse(matching);
        SqlCapture.clear();

        var work = dashboards.getDashboard(technician.getId(), Role.TECHNICIAN).technician();
        long count = state == RepairRequestStatus.ASSIGNED ? work.assignedCount() : work.inProgressCount();
        var preview = state == RepairRequestStatus.ASSIGNED ? work.assignedRequests() : work.inProgressRequests();

        assertThat(count).isEqualTo(state == RepairRequestStatus.ASSIGNED ? 8 : 9);
        assertThat(preview).extracting(RepairRequestPreview::id).containsExactlyElementsOf(matching.subList(0, 5));
        assertThat(previewSql()).hasSize(2).allSatisfy(DashboardReadTest::assertSqlLimit);
    }

    @Test
    void requesterKeepsOwnUnassignedAndFinalRequestsEvenWhenManyHiddenRequestsAreNewer() {
        for (int i = 0; i < 7; i++) {
            saveRequest(otherRequester, otherTechnician, RepairRequestStatus.ASSIGNED, RepairRequestPriority.CRITICAL,
                    pump, CREATED.plusSeconds(60));
        }

        var data = dashboards.getDashboard(requester.getId(), Role.REQUESTER);

        assertThat(data.administrator()).isNull();
        assertThat(data.technician()).isNull();
        assertThat(data.latestRequests()).extracting(RepairRequestPreview::id).containsExactly(q(5), q(2), q(1));
    }

    @Test
    void requesterLimitRunsInSqlAfterAuthorshipAndKeepsStableOrderForTies() {
        List<Long> matching = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            matching.add(saveRequest(requester, null, RepairRequestStatus.NEW, RepairRequestPriority.LOW,
                    press, CREATED.plusSeconds(60)));
            saveRequest(otherRequester, null, RepairRequestStatus.NEW, RepairRequestPriority.CRITICAL,
                    pump, CREATED.plusSeconds(120));
        }
        Collections.reverse(matching);
        SqlCapture.clear();

        var data = dashboards.getDashboard(requester.getId(), Role.REQUESTER);

        assertThat(data.latestRequests()).extracting(RepairRequestPreview::id)
                .containsExactlyElementsOf(matching.subList(0, 5));
        assertThat(previewSql()).hasSize(1).allSatisfy(DashboardReadTest::assertSqlLimit);
        assertThat(SqlCapture.statements()).noneMatch(sql -> sql.contains("count("));
    }

    @Test
    void creationTimePrecedesTheIdTieBreakerAndLastUpdateOrPriorityDoesNotReorderThePreview() {
        jdbc.update("UPDATE repair_requests SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(CREATED.plusSeconds(60)), Timestamp.from(CREATED.plusSeconds(60)), q(1));
        jdbc.update("UPDATE repair_requests SET updated_at = ?, priority = 'CRITICAL' WHERE id = ?",
                Timestamp.from(CREATED.plusSeconds(3600)), q(2));

        assertThat(dashboards.getDashboard(admin.getId(), Role.ADMIN).latestRequests())
                .extracting(RepairRequestPreview::id).containsExactly(q(1), q(9), q(8), q(7), q(6));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void emptySelectionsReturnZeroCountsAndEmptyLists(Role role) {
        history.deleteAllInBatch();
        requests.deleteAllInBatch();
        User viewer = viewer(role);

        var data = dashboards.getDashboard(viewer.getId(), role);

        assertThat(data.latestRequests()).isEmpty();
        if (role == Role.ADMIN) {
            assertThat(data.administrator()).isEqualTo(new AdminDashboard(0, 0, 0, 0, 0));
        } else if (role == Role.TECHNICIAN) {
            assertThat(data.technician().assignedCount()).isZero();
            assertThat(data.technician().inProgressCount()).isZero();
            assertThat(data.technician().assignedRequests()).isEmpty();
            assertThat(data.technician().inProgressRequests()).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("changedRoles")
    void aDatabaseRoleChangeIsRejectedBeforeAnyRequestQuery(Role cachedRole, Role currentRole) {
        User changed = saveUser("Changed account", cachedRole);
        changed.updateProfile(changed.getFullName(), changed.getEmail(), currentRole);
        users.saveAndFlush(changed);
        SqlCapture.clear();

        assertThatThrownBy(() -> dashboards.getDashboard(changed.getId(), cachedRole))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(SqlCapture.statements()).noneMatch(sql -> sql.contains("repair_requests"));
    }

    static Stream<Arguments> changedRoles() {
        return Arrays.stream(Role.values()).flatMap(oldRole -> Arrays.stream(Role.values())
                .filter(currentRole -> currentRole != oldRole).map(currentRole -> Arguments.of(oldRole, currentRole)));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void anInactiveDatabaseAccountIsRejectedBeforeAnyRequestQuery(Role role) {
        User inactive = saveUser("Inactive account", role);
        inactive.deactivate();
        users.saveAndFlush(inactive);
        SqlCapture.clear();

        assertThatThrownBy(() -> dashboards.getDashboard(inactive.getId(), role))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(SqlCapture.statements()).noneMatch(sql -> sql.contains("repair_requests"));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void previewsLoadScalarFieldsInABoundedNumberOfSelectsWithoutRequestEntities(Role role) {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        SqlCapture.clear();
        try {
            dashboards.getDashboard(viewer(role).getId(), role);

            assertThat(statistics.getPrepareStatementCount()).isEqualTo(switch (role) {
                case ADMIN -> 7L;       // User, five counts, one preview.
                case TECHNICIAN -> 5L;  // User, two counts, two previews.
                case REQUESTER -> 2L;   // User and own preview, with no count query.
            });
            assertThat(statistics.getEntityLoadCount()).isEqualTo(1); // Only the current User.
            assertThat(statistics.getCollectionFetchCount()).isZero();
            assertThat(previewSql()).isNotEmpty().allSatisfy(DashboardReadTest::assertSqlLimit);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void reassignmentChangesWorkloadButPreservesTheAuthorsPreview() {
        repairs.assign(q(2), otherTechnician.getId(), admin.getId());

        var former = dashboards.getDashboard(technician.getId(), Role.TECHNICIAN).technician();
        assertThat(former.assignedCount()).isZero();
        assertThat(former.assignedRequests()).isEmpty();
        assertThat(dashboards.getDashboard(otherTechnician.getId(), Role.TECHNICIAN).technician().assignedCount())
                .isEqualTo(2);
        assertThat(dashboards.getDashboard(requester.getId(), Role.REQUESTER).latestRequests())
                .extracting(RepairRequestPreview::id).contains(q(2));
    }

    @Test
    void reworkCompletionClosureAndCancellationRefreshTheCorrectCountsAndKeepOneHistoryRecord() {
        repairs.returnForRework(q(4), repairs.getDetails(q(4), admin.getId()).updatedAt(), admin.getId());
        assertThat(dashboards.getDashboard(admin.getId(), Role.ADMIN).administrator())
                .isEqualTo(new AdminDashboard(2, 2, 3, 0, 2));
        assertThat(dashboards.getDashboard(otherTechnician.getId(), Role.TECHNICIAN).technician().inProgressCount())
                .isEqualTo(1);

        var result = repairs.getResolutionForm(q(4), otherTechnician.getId());
        result.setResolution("Connection checked again. Final test passed.");
        repairs.completeWork(q(4), result, otherTechnician.getId());
        assertThat(dashboards.getDashboard(admin.getId(), Role.ADMIN).administrator())
                .isEqualTo(new AdminDashboard(2, 2, 2, 1, 2));

        repairs.closeRequest(q(4), repairs.getDetails(q(4), admin.getId()).updatedAt(), admin.getId());
        assertThat(dashboards.getDashboard(admin.getId(), Role.ADMIN).administrator())
                .isEqualTo(new AdminDashboard(2, 2, 2, 0, 1));
        assertThat(history.count()).isEqualTo(2); // Q5 from the fixture and the accepted Q4.
        assertThat(history.findByRepairRequestId(q(4))).isPresent();

        var cancellation = repairs.getCancellationForm(q(1), admin.getId());
        cancellation.setCancellationReason("Dashboard test request cancelled.");
        repairs.cancelRequest(q(1), cancellation, admin.getId());
        assertThat(dashboards.getDashboard(admin.getId(), Role.ADMIN).administrator())
                .isEqualTo(new AdminDashboard(1, 2, 2, 0, 0));
        assertThat(history.count()).isEqualTo(2);
        assertThat(history.findByRepairRequestId(q(1))).isEmpty();
    }

    @Test
    void repeatedReadsLeaveRequestsAndHistoryUnchanged() {
        var beforeRequests = jdbc.queryForList("SELECT * FROM repair_requests ORDER BY id");
        var beforeHistory = jdbc.queryForList("SELECT * FROM maintenance_records ORDER BY id");

        for (int i = 0; i < 2; i++) {
            for (Role role : Role.values()) {
                dashboards.getDashboard(viewer(role).getId(), role);
            }
        }

        assertThat(jdbc.queryForList("SELECT * FROM repair_requests ORDER BY id")).isEqualTo(beforeRequests);
        assertThat(jdbc.queryForList("SELECT * FROM maintenance_records ORDER BY id")).isEqualTo(beforeHistory);
    }

    @Test
    @Timeout(30)
    void statusChangedBetweenCountAndPreviewUsesOneRepeatableReadSnapshot() throws Exception {
        CountDownLatch counted = new CountDownLatch(1);
        CountDownLatch continueReading = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var reading = executor.submit(() -> {
                SqlCapture.beforeNextPreview(() -> {
                    counted.countDown();
                    try {
                        assertThat(continueReading.await(10, TimeUnit.SECONDS))
                                .as("The writer must finish before the preview continues").isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Dashboard preview was interrupted", exception);
                    }
                });
                try {
                    return dashboards.getDashboard(technician.getId(), Role.TECHNICIAN);
                } finally {
                    SqlCapture.clear();
                }
            });
            try {
                boolean reachedPreview = counted.await(10, TimeUnit.SECONDS);
                if (!reachedPreview && reading.isDone()) {
                    reading.get(10, TimeUnit.SECONDS); // Surface a reader failure instead of hiding it behind the latch.
                }
                assertThat(reachedPreview).as("Dashboard must reach its preview after reading both counts").isTrue();
                executor.submit(() -> repairs.startWork(q(2), technician.getId())).get(10, TimeUnit.SECONDS);
            } finally {
                continueReading.countDown();
            }

            var beforeChange = reading.get(10, TimeUnit.SECONDS).technician();
            assertThat(beforeChange.assignedCount()).isEqualTo(1);
            assertThat(beforeChange.assignedRequests()).singleElement().satisfies(request -> {
                assertThat(request.id()).isEqualTo(q(2));
                assertThat(request.status()).isEqualTo(RepairRequestStatus.ASSIGNED);
            });
            assertThat(beforeChange.inProgressCount()).isEqualTo(2);
            assertThat(beforeChange.inProgressRequests()).extracting(RepairRequestPreview::id).containsExactly(q(8), q(3));
        }

        var afterChange = dashboards.getDashboard(technician.getId(), Role.TECHNICIAN).technician();
        assertThat(afterChange.assignedCount()).isZero();
        assertThat(afterChange.assignedRequests()).isEmpty();
        assertThat(afterChange.inProgressCount()).isEqualTo(3);
        assertThat(afterChange.inProgressRequests()).extracting(RepairRequestPreview::id).contains(q(2));
    }

    private User viewer(Role role) {
        return switch (role) {
            case ADMIN -> admin;
            case TECHNICIAN -> technician;
            case REQUESTER -> requester;
        };
    }

    private Long q(int number) {
        return ids.get(number - 1);
    }

    private User saveUser(String name, Role role) {
        return users.saveAndFlush(new User(name, name.replace(' ', '.').toLowerCase(Locale.ROOT) + "@example.com",
                "{bcrypt}test-hash", role));
    }

    private Long saveRequest(User author, User assigned, RepairRequestStatus state, RepairRequestPriority priority,
                             Equipment unit, Instant createdAt) {
        RepairRequest request = new RepairRequest("Dashboard request from " + author.getFullName(),
                "A reported test fault on " + unit.getName(), unit, author, priority);
        if (assigned != null) {
            request.assignTo(assigned, createdAt.plusSeconds(10));
        }
        if (state == RepairRequestStatus.IN_PROGRESS || state == RepairRequestStatus.COMPLETED
                || state == RepairRequestStatus.CLOSED) {
            request.startWork(createdAt.plusSeconds(20));
        }
        if (state == RepairRequestStatus.COMPLETED || state == RepairRequestStatus.CLOSED) {
            request.completeWork("Connection tightened. Final test passed.", createdAt.plusSeconds(30));
        }
        if (state == RepairRequestStatus.CLOSED) {
            request.close(createdAt.plusSeconds(40));
        } else if (state == RepairRequestStatus.CANCELLED) {
            request.cancel("Test request cancelled.", createdAt.plusSeconds(40));
        }
        RepairRequest saved = requests.saveAndFlush(request);
        if (state == RepairRequestStatus.CLOSED) {
            history.saveAndFlush(new MaintenanceRecord(saved));
        }
        jdbc.update("UPDATE repair_requests SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(createdAt), Timestamp.from(createdAt.plusSeconds(50)), saved.getId());
        return saved.getId();
    }

    private static List<String> previewSql() {
        return SqlCapture.statements().stream()
                .filter(sql -> sql.contains("from repair_requests") && !sql.contains("count("))
                .toList();
    }

    private static void assertSqlLimit(String sql) {
        assertThat(sql).containsPattern("fetch (first|next) \\? rows only|limit \\?");
    }

    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> SQL = ThreadLocal.withInitial(ArrayList::new);
        private static final ThreadLocal<Runnable> BEFORE_PREVIEW = new ThreadLocal<>();

        @Override
        public String inspect(String sql) {
            String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            SQL.get().add(normalized);
            if (normalized.contains("from repair_requests") && !normalized.contains("count(")) {
                Runnable action = BEFORE_PREVIEW.get();
                if (action != null) {
                    BEFORE_PREVIEW.remove();
                    action.run();
                }
            }
            return sql;
        }

        static void beforeNextPreview(Runnable action) {
            BEFORE_PREVIEW.set(action);
        }

        static List<String> statements() {
            return List.copyOf(SQL.get());
        }

        static void clear() {
            SQL.remove();
            BEFORE_PREVIEW.remove();
        }
    }
}
