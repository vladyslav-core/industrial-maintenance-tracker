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

class RepairRequestAssignmentRulesTest {

    private static final Instant FIRST_ASSIGNMENT = Instant.parse("2026-10-20T10:00:00Z");
    private static final Instant FIRST_START = FIRST_ASSIGNMENT.plusSeconds(60);
    private static final Instant REASSIGNMENT = FIRST_START.plusSeconds(60);

    @Test
    void reassigningRunningRepairPreservesFirstStartAndDraft() {
        RepairRequest request = runningRequest();
        User replacement = technician(2L);
        request.assignTo(replacement, REASSIGNMENT);
        assertThat(request.getAssignedTechnician()).isSameAs(replacement);
        assertThat(request.getAssignedAt()).isEqualTo(REASSIGNMENT);
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(request.getStartedAt()).isEqualTo(FIRST_START);
        assertThat(request.getResolution()).isEqualTo("Seal removed, checking the cylinder");
        assertThat(request.getPriority()).isEqualTo(RepairRequestPriority.HIGH);
    }

    @Test
    void rejectedRemovalKeepsRunningRepairIntact() {
        RepairRequest request = runningRequest();
        User assigned = request.getAssignedTechnician();
        assertThatThrownBy(request::unassign).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(request.getAssignedTechnician()).isSameAs(assigned);
        assertThat(request.getAssignedAt()).isEqualTo(FIRST_ASSIGNMENT);
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(request.getStartedAt()).isEqualTo(FIRST_START);
        assertThat(request.getResolution()).isEqualTo("Seal removed, checking the cylinder");
    }

    @Test
    void invalidTechnicianOrMissingTimeCannotPartlyAssignRequest() {
        RepairRequest request = newRequest();
        User inactive = technician(1L);
        inactive.deactivate();
        assertThatThrownBy(() -> request.assignTo(inactive, FIRST_ASSIGNMENT))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> request.assignTo(request.getCreatedBy(), FIRST_ASSIGNMENT))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> request.assignTo(technician(2L), null)).isInstanceOf(NullPointerException.class);
        assertThat(request.getAssignedTechnician()).isNull();
        assertThat(request.getAssignedAt()).isNull();
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.NEW);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"COMPLETED", "CLOSED", "CANCELLED"})
    void finalOrReviewStatesKeepAssignmentAndPriorityAfterRejectedActions(RepairRequestStatus status) {
        RepairRequest request = runningRequest();
        ReflectionTestUtils.setField(request, "status", status);
        User assigned = request.getAssignedTechnician();
        assertThatThrownBy(() -> request.assignTo(technician(2L), REASSIGNMENT))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> request.changePriority(RepairRequestPriority.LOW))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(request.getAssignedTechnician()).isSameAs(assigned);
        assertThat(request.getPriority()).isEqualTo(RepairRequestPriority.HIGH);
        assertThat(request.getStatus()).isEqualTo(status);
    }

    private RepairRequest newRequest() {
        return new RepairRequest("Oil leak in press", "Hydraulic oil leaking around the seal",
                new Equipment("Press", "PRESS-001", null, "Workshop A"),
                new User("Author", "author@example.com", "hashed-password", Role.REQUESTER),
                RepairRequestPriority.HIGH);
    }

    private RepairRequest runningRequest() {
        RepairRequest request = newRequest();
        request.assignTo(technician(1L), FIRST_ASSIGNMENT);
        // Starting work is introduced in step 08.
        ReflectionTestUtils.setField(request, "status", RepairRequestStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(request, "startedAt", FIRST_START);
        ReflectionTestUtils.setField(request, "resolution", "Seal removed, checking the cylinder");
        return request;
    }

    private User technician(Long id) {
        User user = new User("Technician", "tech-" + id + "@example.com", "hashed-password", Role.TECHNICIAN);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
