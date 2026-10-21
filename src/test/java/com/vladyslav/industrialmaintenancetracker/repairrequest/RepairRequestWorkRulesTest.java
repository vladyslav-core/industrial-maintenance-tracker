package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairRequestWorkRulesTest {

    private static final Instant ASSIGNED = Instant.parse("2026-10-21T08:00:00Z");
    private static final Instant STARTED = ASSIGNED.plusSeconds(60);
    private static final Instant COMPLETED = STARTED.plusSeconds(120);

    @Test
    void repeatedStartCannotChangeFirstStartOrDraft() {
        RepairRequest request = assignedRequest();
        request.startWork(STARTED);
        request.saveResolution("Seal removed, checking the cylinder");
        assertThatThrownBy(() -> request.startWork(COMPLETED)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(request.getStartedAt()).isEqualTo(STARTED);
        assertThat(request.getAssignedAt()).isEqualTo(ASSIGNED);
        assertThat(request.getResolution()).isEqualTo("Seal removed, checking the cylinder");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void emptyDraftIsStoredAsAbsence(String draft) {
        RepairRequest request = assignedRequest();
        request.startWork(STARTED);
        request.saveResolution("Existing draft");
        request.saveResolution(draft);
        assertThat(request.getResolution()).isNull();
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(request.getCompletedAt()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void blankCompletionCannotEraseDraftOrChangeStatus(String result) {
        RepairRequest request = assignedRequest();
        request.startWork(STARTED);
        request.saveResolution("Existing draft");
        assertThatThrownBy(() -> request.completeWork(result, COMPLETED)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(request.getResolution()).isEqualTo("Existing draft");
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(request.getCompletedAt()).isNull();
    }

    @Test
    void completionStoresSubmittedResultAndKeepsFirstStartAndAssignment() {
        RepairRequest request = assignedRequest();
        request.startWork(STARTED);
        request.completeWork("  Seal replaced.\nPressure test passed.  ", COMPLETED);
        assertThat(request.getResolution()).isEqualTo("Seal replaced.\nPressure test passed.");
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(COMPLETED);
        assertThat(request.getStartedAt()).isEqualTo(STARTED);
        assertThat(request.getAssignedAt()).isEqualTo(ASSIGNED);
        assertThat(request.getClosedAt()).isNull();
        assertThatThrownBy(() -> request.completeWork("Another result", COMPLETED.plusSeconds(60)))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(request.getCompletedAt()).isEqualTo(COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void resultsCannotBeChangedOutsideWorkInProgress(RepairRequestStatus state) {
        RepairRequest request = assignedRequest();
        ReflectionTestUtils.setField(request, "status", state);
        assertThatThrownBy(() -> request.saveResolution("Changed draft"))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> request.completeWork("Completed repair", COMPLETED))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(request.getStatus()).isEqualTo(state);
        assertThat(request.getResolution()).isNull();
        assertThat(request.getCompletedAt()).isNull();
    }

    @Test
    void missingTimeCannotPartlyStartOrCompleteRepair() {
        RepairRequest request = assignedRequest();
        assertThatThrownBy(() -> request.startWork(null)).isInstanceOf(NullPointerException.class);
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.ASSIGNED);
        assertThat(request.getStartedAt()).isNull();
        request.startWork(STARTED);
        request.saveResolution("Existing draft");
        assertThatThrownBy(() -> request.completeWork("Changed result", null)).isInstanceOf(NullPointerException.class);
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(request.getResolution()).isEqualTo("Existing draft");
    }

    private RepairRequest assignedRequest() {
        RepairRequest request = new RepairRequest("Oil leak in press", "Hydraulic oil leaking around the seal",
                new Equipment("Press", "PRESS-001", null, "Workshop A"),
                new User("Author", "author@example.com", "hashed-password", Role.REQUESTER),
                RepairRequestPriority.HIGH);
        User technician = new User("Technician", "tech@example.com", "hashed-password", Role.TECHNICIAN);
        ReflectionTestUtils.setField(technician, "id", 1L);
        request.assignTo(technician, ASSIGNED);
        return request;
    }
}
