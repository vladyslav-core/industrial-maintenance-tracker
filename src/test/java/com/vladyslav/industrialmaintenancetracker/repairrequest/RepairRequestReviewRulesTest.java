package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairRequestReviewRulesTest {

    private static final Instant ASSIGNED = Instant.parse("2026-10-21T08:00:00Z");
    private static final Instant STARTED = ASSIGNED.plusSeconds(60);
    private static final Instant COMPLETED = STARTED.plusSeconds(120);

    @Test
    void returnClearsCompletionAndPreservesResultFirstStartAndAssignment() {
        RepairRequest request = completedRequest();
        User technician = request.getAssignedTechnician();
        request.returnForRework();
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(request.getCompletedAt()).isNull();
        assertThat(request.getResolution()).isEqualTo("Seal replaced. Pressure test passed.");
        assertThat(request.getStartedAt()).isEqualTo(STARTED);
        assertThat(request.getAssignedAt()).isEqualTo(ASSIGNED);
        assertThat(request.getAssignedTechnician()).isSameAs(technician);
        assertThat(request.getPriority()).isEqualTo(RepairRequestPriority.HIGH);
        assertThat(request.getClosedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void otherStatesRejectReturnWithoutChangingResultOrCompletion(RepairRequestStatus state) {
        RepairRequest request = completedRequest();
        ReflectionTestUtils.setField(request, "status", state);
        assertThatThrownBy(request::returnForRework).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(request.getStatus()).isEqualTo(state);
        assertThat(request.getCompletedAt()).isEqualTo(COMPLETED);
        assertThat(request.getResolution()).isEqualTo("Seal replaced. Pressure test passed.");
    }

    @Test
    void correctedResultCanBeCompletedAgainWithoutRestartingWork() {
        RepairRequest request = completedRequest();
        request.returnForRework();
        assertThatThrownBy(request::returnForRework).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> request.startWork(COMPLETED.plusSeconds(1))).isInstanceOf(InvalidRepairRequestActionException.class);
        request.saveResolution("Seal checked again. Extended pressure test passed.");
        Instant secondCompletion = COMPLETED.plusSeconds(300);
        request.completeWork(request.getResolution(), secondCompletion);
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(request.getResolution()).isEqualTo("Seal checked again. Extended pressure test passed.");
        assertThat(request.getCompletedAt()).isEqualTo(secondCompletion);
        assertThat(request.getStartedAt()).isEqualTo(STARTED);
    }

    private RepairRequest completedRequest() {
        RepairRequest request = new RepairRequest("Oil leak in press", "Hydraulic oil leaking around the seal",
                new Equipment("Press", "PRESS-001", null, "Workshop A"),
                new User("Author", "author@example.com", "hashed-password", Role.REQUESTER),
                RepairRequestPriority.HIGH);
        User technician = new User("Technician", "tech@example.com", "hashed-password", Role.TECHNICIAN);
        ReflectionTestUtils.setField(technician, "id", 1L);
        request.assignTo(technician, ASSIGNED);
        request.startWork(STARTED);
        request.completeWork("Seal replaced. Pressure test passed.", COMPLETED);
        return request;
    }
}
