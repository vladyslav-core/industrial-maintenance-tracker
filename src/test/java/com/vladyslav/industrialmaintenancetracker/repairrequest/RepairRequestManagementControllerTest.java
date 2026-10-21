package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestDetails;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestTechnicianOption;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RepairRequestController.class)
@Import(SecurityConfig.class)
class RepairRequestManagementControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RepairRequestService service;

    @Test
    void assignmentUsesAdministratorFromLoginAndIgnoresForgedActorAndStatus() throws Exception {
        mockMvc.perform(action("assign").with(currentUser(Role.ADMIN)).with(csrf())
                        .param("administratorId", "99").param("status", "CLOSED"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attributeExists("successMessage"));
        verify(service).assign(73L, 7L, 42L);
    }

    @Test
    void unassignmentUsesAuthenticatedAdministrator() throws Exception {
        mockMvc.perform(action("unassign").with(currentUser(Role.ADMIN)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attributeExists("successMessage"));
        verify(service).unassign(73L, 42L);
    }

    @Test
    void priorityUsesAuthenticatedAdministrator() throws Exception {
        mockMvc.perform(action("priority").with(currentUser(Role.ADMIN)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attributeExists("successMessage"));
        verify(service).changePriority(73L, RepairRequestPriority.CRITICAL, 42L);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"REQUESTER", "TECHNICIAN"})
    void nonAdministratorsCannotInvokeAnyManagementAction(Role role) throws Exception {
        for (String action : new String[]{"assign", "unassign", "priority"}) {
            mockMvc.perform(action(action).with(currentUser(role)).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"assign", "unassign", "priority"})
    void eachActionRequiresCsrf(String action) throws Exception {
        mockMvc.perform(action(action).with(currentUser(Role.ADMIN))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"assign", "unassign", "priority"})
    void anonymousPostWithCsrfStillRequiresLogin(String action) throws Exception {
        mockMvc.perform(action(action).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"assign", "unassign", "priority"})
    void rejectedBusinessActionReturnsToCardWithError(String action) throws Exception {
        rejectAction(action, new InvalidRepairRequestActionException("The request is no longer available for this action."));
        mockMvc.perform(action(action).with(currentUser(Role.ADMIN)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("errorMessage", "The request is no longer available for this action."))
                .andExpect(flash().attributeCount(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"assign", "unassign", "priority"})
    void missingRequestReturnsGeneric404(String action) throws Exception {
        rejectAction(action, new RepairRequestNotFoundException(73L));
        mockMvc.perform(action(action).with(currentUser(Role.ADMIN)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(view().name("repair-requests/not-found"));
    }

    @Test
    void databaseRoleCheckCanRejectStaleAdminSession() throws Exception {
        doThrow(new AccessDeniedException("Account is no longer an administrator"))
                .when(service).assign(73L, 7L, 42L);
        mockMvc.perform(action("assign").with(currentUser(Role.ADMIN)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void invalidOrMissingParametersAreRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/repair-requests/73/assign").with(currentUser(Role.ADMIN)).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/repair-requests/73/assign").with(currentUser(Role.ADMIN)).with(csrf()).param("technicianId", "invalid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/repair-requests/73/priority").with(currentUser(Role.ADMIN)).with(csrf()).param("priority", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/repair-requests/73/priority").with(currentUser(Role.ADMIN)).with(csrf()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(RepairRequestStatus.class)
    void administratorSeesOnlyActionsAllowedByStatus(RepairRequestStatus state) throws Exception {
        boolean manageable = state == RepairRequestStatus.NEW || state == RepairRequestStatus.ASSIGNED
                || state == RepairRequestStatus.IN_PROGRESS;
        when(service.getDetails(73L, 42L)).thenReturn(details(state, manageable));
        if (manageable) {
            when(service.availableTechnicians(42L)).thenReturn(List.of(new RepairRequestTechnicianOption(7L, "Technician", "tech@example.com")));
        }
        var result = mockMvc.perform(get("/repair-requests/73").with(currentUser(Role.ADMIN)))
                .andExpect(status().isOk());
        if (manageable) {
            result.andExpect(content().string(containsString("/repair-requests/73/assign")))
                    .andExpect(content().string(containsString("/repair-requests/73/priority")))
                    .andExpect(content().string(containsString("name=\"_csrf\"")));
        } else {
            result.andExpect(content().string(not(containsString("/repair-requests/73/assign"))))
                    .andExpect(content().string(not(containsString("/repair-requests/73/priority"))));
            verify(service, never()).availableTechnicians(anyLong());
        }
        result.andExpect(content().string(state == RepairRequestStatus.ASSIGNED
                ? containsString("/repair-requests/73/unassign") : not(containsString("/repair-requests/73/unassign"))));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"REQUESTER", "TECHNICIAN"})
    void nonAdministratorsNeverSeeManagementForms(Role role) throws Exception {
        when(service.getDetails(73L, 42L)).thenReturn(details(RepairRequestStatus.NEW, false));
        mockMvc.perform(get("/repair-requests/73").with(currentUser(role))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/repair-requests/73/assign"))))
                .andExpect(content().string(not(containsString("/repair-requests/73/priority"))));
        verify(service, never()).availableTechnicians(anyLong());
    }

    @Test
    void emptyTechnicianListStillAllowsPriorityChange() throws Exception {
        when(service.getDetails(73L, 42L)).thenReturn(details(RepairRequestStatus.NEW, true));
        when(service.availableTechnicians(42L)).thenReturn(List.of());
        mockMvc.perform(get("/repair-requests/73").with(currentUser(Role.ADMIN))).andExpect(status().isOk())
                .andExpect(content().string(containsString("No active technicians are available.")))
                .andExpect(content().string(containsString("/repair-requests/73/priority")))
                .andExpect(content().string(not(containsString("/repair-requests/73/assign"))));
    }

    private void rejectAction(String action, RuntimeException error) {
        switch (action) {
            case "assign" -> doThrow(error).when(service).assign(73L, 7L, 42L);
            case "unassign" -> doThrow(error).when(service).unassign(73L, 42L);
            case "priority" -> doThrow(error).when(service).changePriority(73L, RepairRequestPriority.CRITICAL, 42L);
            default -> throw new IllegalArgumentException(action);
        }
    }

    private MockHttpServletRequestBuilder action(String action) {
        var request = post("/repair-requests/73/" + action);
        if (action.equals("assign")) request.param("technicianId", "7");
        if (action.equals("priority")) request.param("priority", "CRITICAL");
        return request;
    }

    private RepairRequestDetails details(RepairRequestStatus state, boolean manageable) {
        Instant time = Instant.parse("2026-10-21T10:00:00Z");
        return new RepairRequestDetails(73L, "Oil leak in press", "Hydraulic oil leaking around the seal",
                1L, "Press", "PRESS-001", "Workshop A", "Author", null,
                RepairRequestPriority.HIGH, state, null, null, time, time, null, null, null, null,
                false, manageable, manageable && state == RepairRequestStatus.ASSIGNED, false, false);
    }

    private RequestPostProcessor currentUser(Role role) {
        User account = mock(User.class);
        when(account.getId()).thenReturn(42L);
        when(account.getFullName()).thenReturn("Test User");
        when(account.getEmail()).thenReturn("user@example.com");
        when(account.getPasswordHash()).thenReturn("hashed-password");
        when(account.getRole()).thenReturn(role);
        when(account.isActive()).thenReturn(true);
        return user(new CustomUserDetails(account));
    }
}
