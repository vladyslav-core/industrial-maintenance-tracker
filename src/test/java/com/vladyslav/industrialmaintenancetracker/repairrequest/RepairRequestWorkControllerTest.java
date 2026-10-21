package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestWorkConflictException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestDetails;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestResolutionForm;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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
class RepairRequestWorkControllerTest {

    private static final Instant VERSION = Instant.parse("2026-10-21T10:00:00.123456Z");
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RepairRequestService service;

    @Test
    void startUsesTechnicianFromLoginAndIgnoresForgedIdentity() throws Exception {
        mockMvc.perform(action("start").with(currentUser(Role.TECHNICIAN)).with(csrf())
                        .param("technicianId", "99").param("status", "CLOSED"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("successMessage", "Repair work started."));
        verify(service).startWork(73L, 42L);
    }

    @Test
    void repeatedOrInvalidStartReturnsToCardWithMessage() throws Exception {
        doThrow(new InvalidRepairRequestActionException("Work has already started")).when(service).startWork(73L, 42L);
        mockMvc.perform(action("start").with(currentUser(Role.TECHNICIAN)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("errorMessage", "Work has already started"));
    }

    @Test
    void resultFormContainsDraftVersionCsrfAndBothActions() throws Exception {
        when(service.getResolutionForm(73L, 42L)).thenReturn(new RepairRequestResolutionForm("Existing draft", VERSION));
        mockMvc.perform(get("/repair-requests/73/complete").with(currentUser(Role.TECHNICIAN)))
                .andExpect(status().isOk()).andExpect(view().name("repair-requests/resolution"))
                .andExpect(content().string(containsString("Existing draft")))
                .andExpect(content().string(containsString("name=\"updatedAt\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("/repair-requests/73/resolution")))
                .andExpect(content().string(containsString("formaction=\"/repair-requests/73/complete\"")))
                .andExpect(content().string(not(containsString("name=\"status\""))));
    }

    @Test
    void draftPostBindsOnlyResultAndVersionAndReloadsForm() throws Exception {
        mockMvc.perform(action("resolution").with(currentUser(Role.TECHNICIAN)).with(csrf())
                        .param("technicianId", "99").param("assignedTechnicianId", "99")
                        .param("priority", "CRITICAL").param("status", "CLOSED"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73/complete"))
                .andExpect(flash().attribute("successMessage", "Draft saved."));
        ArgumentCaptor<RepairRequestResolutionForm> captor = ArgumentCaptor.forClass(RepairRequestResolutionForm.class);
        verify(service).saveResolution(eq(73L), captor.capture(), eq(42L));
        assertThat(captor.getValue().getResolution()).isEqualTo("  Seal replaced. Pressure test passed.  ");
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(VERSION);
    }

    @Test
    void emptyDraftCanBeSaved() throws Exception {
        mockMvc.perform(post("/repair-requests/73/resolution").with(currentUser(Role.TECHNICIAN)).with(csrf())
                        .param("updatedAt", VERSION.toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73/complete"));
        verify(service).saveResolution(eq(73L), argThat(form -> form.getResolution() == null), eq(42L));
    }

    @Test
    void completingSubmitsCurrentTextAndReturnsToCard() throws Exception {
        mockMvc.perform(action("complete").with(currentUser(Role.TECHNICIAN)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("successMessage", "Repair completed and submitted for review."));
        verify(service).completeWork(eq(73L), argThat(form -> VERSION.equals(form.getUpdatedAt())
                && form.getResolution().contains("Pressure test passed")), eq(42L));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void blankCompletionShowsFieldErrorWithoutSaving(String text) throws Exception {
        when(service.getResolutionForm(73L, 42L)).thenReturn(new RepairRequestResolutionForm("Existing draft", VERSION));
        var request = post("/repair-requests/73/complete").with(currentUser(Role.TECHNICIAN)).with(csrf())
                .param("updatedAt", VERSION.toString());
        if (text != null) request.param("resolution", text);
        mockMvc.perform(request).andExpect(status().isOk()).andExpect(view().name("repair-requests/resolution"))
                .andExpect(model().attributeHasFieldErrors("resolutionForm", "resolution"));
        verify(service, never()).completeWork(any(), any(), any());
        verify(service, never()).saveResolution(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolution", "complete"})
    void missingOrMalformedVersionRequiresReload(String action) throws Exception {
        when(service.getResolutionForm(73L, 42L)).thenReturn(new RepairRequestResolutionForm("Existing draft", VERSION));
        for (String version : new String[]{"", "not-a-time"}) {
            mockMvc.perform(post("/repair-requests/73/" + action).with(currentUser(Role.TECHNICIAN)).with(csrf())
                            .param("resolution", "Attempted result").param("updatedAt", version))
                    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("resolutionForm", "updatedAt"))
                    .andExpect(content().string(containsString("Reload the request to get its current version.")));
        }
        verify(service, never()).completeWork(any(), any(), any());
        verify(service, never()).saveResolution(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolution", "complete"})
    void conflictPreservesEscapedInputAndDisablesBothSubmitActions(String action) throws Exception {
        rejectAction(action, new RepairRequestWorkConflictException());
        mockMvc.perform(post("/repair-requests/73/" + action).with(currentUser(Role.TECHNICIAN)).with(csrf())
                        .param("resolution", "<script>alert(1)</script>").param("updatedAt", VERSION.toString()))
                .andExpect(status().isConflict()).andExpect(view().name("repair-requests/resolution"))
                .andExpect(model().attribute("canSave", false))
                .andExpect(content().string(containsString("Your changes were not saved")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(not(containsString(">Save draft</button>"))))
                .andExpect(content().string(not(containsString(">Complete repair</button>"))));
    }

    @Test
    void unavailableWorkFormRedirectsToCardWithMessage() throws Exception {
        when(service.getResolutionForm(73L, 42L)).thenThrow(new RepairRequestWorkConflictException());
        mockMvc.perform(get("/repair-requests/73/complete").with(currentUser(Role.TECHNICIAN)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "REQUESTER"})
    void otherRolesCannotOpenOrSubmitWorkActions(Role role) throws Exception {
        for (String action : new String[]{"start", "resolution", "complete"}) {
            mockMvc.perform(action(action).with(currentUser(role)).with(csrf())).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/repair-requests/73/complete").with(currentUser(role))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"start", "resolution", "complete"})
    void eachPostRequiresCsrf(String action) throws Exception {
        mockMvc.perform(action(action).with(currentUser(Role.TECHNICIAN))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"start", "resolution", "complete"})
    void anonymousPostWithCsrfStillRequiresLogin(String action) throws Exception {
        mockMvc.perform(action(action).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"start", "resolution", "complete"})
    void serviceCanRejectStaleTechnicianSessionOrWrongAssignment(String action) throws Exception {
        rejectAction(action, new AccessDeniedException("Only the assigned technician can work"));
        mockMvc.perform(action(action).with(currentUser(Role.TECHNICIAN)).with(csrf())).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"start", "resolution", "complete"})
    void hiddenRequestCannotBeChanged(String action) throws Exception {
        rejectAction(action, new RepairRequestNotFoundException(73L));
        mockMvc.perform(action(action).with(currentUser(Role.TECHNICIAN)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(view().name("repair-requests/not-found"));
    }

    @Test
    void hiddenFormReturnsGeneric404() throws Exception {
        when(service.getResolutionForm(73L, 42L)).thenThrow(new RepairRequestNotFoundException(73L));
        mockMvc.perform(get("/repair-requests/73/complete").with(currentUser(Role.TECHNICIAN)))
                .andExpect(status().isNotFound()).andExpect(view().name("repair-requests/not-found"));
    }

    @ParameterizedTest
    @EnumSource(RepairRequestStatus.class)
    void cardShowsOnlyAllowedTechnicianActions(RepairRequestStatus state) throws Exception {
        when(service.getDetails(73L, 42L)).thenReturn(details(state, true));
        mockMvc.perform(get("/repair-requests/73").with(currentUser(Role.TECHNICIAN))).andExpect(status().isOk())
                .andExpect(content().string(state == RepairRequestStatus.ASSIGNED
                        ? containsString("/repair-requests/73/start") : not(containsString("/repair-requests/73/start"))))
                .andExpect(content().string(state == RepairRequestStatus.IN_PROGRESS
                        ? containsString("/repair-requests/73/complete") : not(containsString("/repair-requests/73/complete"))));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "REQUESTER"})
    void nonTechniciansDoNotSeeWorkActionsOrDraftText(Role role) throws Exception {
        when(service.getDetails(73L, 42L)).thenReturn(details(RepairRequestStatus.IN_PROGRESS, false));
        mockMvc.perform(get("/repair-requests/73").with(currentUser(role))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/repair-requests/73/start"))))
                .andExpect(content().string(not(containsString("/repair-requests/73/complete"))))
                .andExpect(content().string(not(containsString("Draft result marker"))));
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"COMPLETED", "CLOSED"})
    void completedResultIsVisibleAndEscaped(RepairRequestStatus state) throws Exception {
        when(service.getDetails(73L, 42L)).thenReturn(details(state, false));
        mockMvc.perform(get("/repair-requests/73").with(currentUser(Role.REQUESTER))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Draft result marker &lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    private void rejectAction(String action, RuntimeException error) {
        switch (action) {
            case "start" -> doThrow(error).when(service).startWork(73L, 42L);
            case "resolution" -> doThrow(error).when(service).saveResolution(eq(73L), any(), eq(42L));
            case "complete" -> doThrow(error).when(service).completeWork(eq(73L), any(), eq(42L));
            default -> throw new IllegalArgumentException(action);
        }
    }

    private MockHttpServletRequestBuilder action(String action) {
        var request = post("/repair-requests/73/" + action);
        if (!action.equals("start")) request.param("resolution", "  Seal replaced. Pressure test passed.  ")
                .param("updatedAt", VERSION.toString());
        return request;
    }

    private RepairRequestDetails details(RepairRequestStatus state, boolean assignedTechnician) {
        return new RepairRequestDetails(73L, "Oil leak in press", "Hydraulic oil leaking around the seal",
                1L, "Press", "PRESS-001", "Workshop A", "Author", "Technician",
                RepairRequestPriority.HIGH, state, "Draft result marker <script>", null,
                VERSION, VERSION, VERSION, null, null, null, false, false, false,
                assignedTechnician && state == RepairRequestStatus.ASSIGNED,
                assignedTechnician && state == RepairRequestStatus.IN_PROGRESS, false);
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
