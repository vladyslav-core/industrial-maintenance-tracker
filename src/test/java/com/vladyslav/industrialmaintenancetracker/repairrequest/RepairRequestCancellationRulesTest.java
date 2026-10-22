package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecord;
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

class RepairRequestCancellationRulesTest {

    private static final Instant ASSIGNED = Instant.parse("2026-10-22T08:00:00Z");
    private static final Instant STARTED = ASSIGNED.plusSeconds(60);
    private static final Instant CANCELLED = STARTED.plusSeconds(120);

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"NEW", "ASSIGNED", "IN_PROGRESS"})
    void cancellationPreservesExistingWorkAndSetsOnlyReasonStatusAndClosure(RepairRequestStatus state) {
        RepairRequest request = requestIn(state);
        var technician = request.getAssignedTechnician();
        var startedAt = request.getStartedAt();
        var assignedAt = request.getAssignedAt();
        var draft = request.getResolution();
        request.cancel("  Duplicate request for the same fault.  ", CANCELLED);
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.CANCELLED);
        assertThat(request.getCancellationReason()).isEqualTo("Duplicate request for the same fault.");
        assertThat(request.getClosedAt()).isEqualTo(CANCELLED);
        assertThat(request.getCompletedAt()).isNull();
        assertThat(request.getAssignedTechnician()).isSameAs(technician);
        assertThat(request.getAssignedAt()).isEqualTo(assignedAt);
        assertThat(request.getStartedAt()).isEqualTo(startedAt);
        assertThat(request.getResolution()).isEqualTo(draft);
        assertThat(request.getPriority()).isEqualTo(RepairRequestPriority.HIGH);
        assertThatThrownBy(() -> new MaintenanceRecord(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"COMPLETED", "CLOSED", "CANCELLED"})
    void cannotCancelCompletedClosedOrAlreadyCancelledRequest(RepairRequestStatus state) {
        RepairRequest request = requestIn(state);
        var originalClosedAt = request.getClosedAt();
        var originalCompletedAt = request.getCompletedAt();
        var originalReason = request.getCancellationReason();
        var originalResult = request.getResolution();
        assertThatThrownBy(() -> request.cancel("A different cancellation reason", CANCELLED.plusSeconds(60)))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(request.getStatus()).isEqualTo(state);
        assertThat(request.getClosedAt()).isEqualTo(originalClosedAt);
        assertThat(request.getCompletedAt()).isEqualTo(originalCompletedAt);
        assertThat(request.getCancellationReason()).isEqualTo(originalReason);
        assertThat(request.getResolution()).isEqualTo(originalResult);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"    ", "\u2003\u2003\u2003\u2003\u2003", "four", "  four  "})
    void invalidReasonCannotPartlyCancelRequest(String reason) {
        RepairRequest request = requestIn(RepairRequestStatus.IN_PROGRESS);
        assertThatThrownBy(() -> request.cancel(reason, CANCELLED)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertStillInProgress(request);
    }

    @Test
    void reasonCannotExceedFiveHundredCharacters() {
        RepairRequest request = requestIn(RepairRequestStatus.IN_PROGRESS);
        assertThatThrownBy(() -> request.cancel("x".repeat(501), CANCELLED)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertStillInProgress(request);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 500})
    void acceptsReasonLengthBoundariesAfterTrimming(int length) {
        RepairRequest request = requestIn(RepairRequestStatus.NEW);
        request.cancel("  " + "x".repeat(length) + "  ", CANCELLED);
        assertThat(request.getCancellationReason()).isEqualTo("x".repeat(length));
    }

    @Test
    void missingTimeCannotPartlyCancelRequest() {
        RepairRequest request = requestIn(RepairRequestStatus.IN_PROGRESS);
        assertThatThrownBy(() -> request.cancel("Duplicate request", null)).isInstanceOf(NullPointerException.class);
        assertStillInProgress(request);
    }

    private void assertStillInProgress(RepairRequest request) {
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(request.getCancellationReason()).isNull();
        assertThat(request.getClosedAt()).isNull();
        assertThat(request.getResolution()).isEqualTo("Seal inspection draft");
        assertThat(request.getStartedAt()).isEqualTo(STARTED);
    }

    private RepairRequest requestIn(RepairRequestStatus state) {
        RepairRequest request = new RepairRequest("Oil leak in press", "Hydraulic oil leaking around the seal",
                new Equipment("Press", "PRESS-001", null, "Workshop A"),
                new User("Author", "author@example.com", "hashed-password", Role.REQUESTER), RepairRequestPriority.HIGH);
        if (state == RepairRequestStatus.NEW) return request;
        User technician = new User("Technician", "tech@example.com", "hashed-password", Role.TECHNICIAN);
        ReflectionTestUtils.setField(technician, "id", 1L);
        request.assignTo(technician, ASSIGNED);
        if (state == RepairRequestStatus.ASSIGNED) return request;
        request.startWork(STARTED);
        request.saveResolution("Seal inspection draft");
        if (state == RepairRequestStatus.CANCELLED) request.cancel("Original reason", CANCELLED);
        if (state == RepairRequestStatus.COMPLETED || state == RepairRequestStatus.CLOSED) {
            request.completeWork("Seal replaced and tested", STARTED.plusSeconds(60));
            if (state == RepairRequestStatus.CLOSED) request.close(CANCELLED);
        }
        return request;
    }
}
