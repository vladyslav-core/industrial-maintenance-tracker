package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecord;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairRequestClosureRulesTest {

    private static final Instant ASSIGNED = Instant.parse("2026-10-21T08:00:00Z");
    private static final Instant STARTED = ASSIGNED.plusSeconds(60);
    private static final Instant COMPLETED = STARTED.plusSeconds(120);
    private static final Instant CLOSED = COMPLETED.plusSeconds(300);

    @Test
    void closingPreservesRepairResultAssignmentAndTimesForSnapshot() {
        RepairRequest request = completedRequest("Seal replaced. Pressure test passed.");
        User technician = request.getAssignedTechnician();
        request.close(CLOSED);
        MaintenanceRecord snapshot = new MaintenanceRecord(request);
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.CLOSED);
        assertThat(request.getClosedAt()).isEqualTo(CLOSED);
        assertThat(request.getStartedAt()).isEqualTo(STARTED);
        assertThat(request.getCompletedAt()).isEqualTo(COMPLETED);
        assertThat(request.getAssignedAt()).isEqualTo(ASSIGNED);
        assertThat(request.getAssignedTechnician()).isSameAs(technician);
        assertThat(request.getPriority()).isEqualTo(RepairRequestPriority.HIGH);
        assertThat(snapshot.getEquipment()).isSameAs(request.getEquipment());
        assertThat(snapshot.getRepairRequest()).isSameAs(request);
        assertThat(snapshot.getPerformedBy()).isSameAs(technician);
        assertThat(snapshot.getWorkDescription()).isEqualTo(request.getResolution());
        assertThat(snapshot.getPerformedAt()).isEqualTo(COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void cannotCloseOtherStatesOrChangeTheirResult(RepairRequestStatus state) {
        RepairRequest request = completedRequest("Original completed result");
        ReflectionTestUtils.setField(request, "status", state);
        assertThatThrownBy(() -> request.close(CLOSED)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(request.getStatus()).isEqualTo(state);
        assertThat(request.getResolution()).isEqualTo("Original completed result");
        assertThat(request.getCompletedAt()).isEqualTo(COMPLETED);
        assertThat(request.getClosedAt()).isNull();
    }

    @Test
    void domainRejectsSecondClosureWithoutChangingOriginalTime() {
        RepairRequest request = completedRequest("Seal replaced and tested");
        request.close(CLOSED);
        assertThatThrownBy(() -> request.close(CLOSED.plusSeconds(60))).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(request.getClosedAt()).isEqualTo(CLOSED);
    }

    @Test
    void missingTimeCannotPartlyCloseRequest() {
        RepairRequest request = completedRequest("Seal replaced and tested");
        assertThatThrownBy(() -> request.close(null)).isInstanceOf(NullPointerException.class);
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(request.getClosedAt()).isNull();
    }

    @Test
    void snapshotAcceptsExistingResolutionWhitespaceRulesWithoutChangingText() {
        String result = "\u2003Seal replaced. Pressure test passed.\u2003";
        RepairRequest request = completedRequest(result);
        request.close(CLOSED);
        assertThat(new MaintenanceRecord(request).getWorkDescription()).isEqualTo(result);
    }

    private RepairRequest completedRequest(String result) {
        RepairRequest request = new RepairRequest("Oil leak in press", "Hydraulic oil leaking around the seal",
                new Equipment("Press", "PRESS-001", null, "Workshop A"),
                new User("Author", "author@example.com", "hashed-password", Role.REQUESTER),
                RepairRequestPriority.HIGH);
        User technician = new User("Technician", "tech@example.com", "hashed-password", Role.TECHNICIAN);
        ReflectionTestUtils.setField(technician, "id", 1L);
        request.assignTo(technician, ASSIGNED);
        request.startWork(STARTED);
        request.completeWork(result, COMPLETED);
        return request;
    }
}
