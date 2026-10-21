package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestDetails;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

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
class RepairRequestClosureControllerTest {

    private static final Instant VERSION = Instant.parse("2026-10-21T10:00:00.123456Z");
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RepairRequestService service;

    @Test
    void closeUsesReviewedVersionAndAuthenticatedAdminAndIgnoresProtectedFields() throws Exception {
        mockMvc.perform(closePost().with(currentUser(Role.ADMIN)).with(csrf())
                        .param("administratorId", "99").param("technicianId", "99")
                        .param("status", "CANCELLED").param("resolution", "Attempted replacement"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("successMessage", "Repair closed and recorded in maintenance history."));
        verify(service).closeRequest(73L, VERSION, 42L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void invalidStateOrStaleReviewReturnsToCardWithError() throws Exception {
        doThrow(new InvalidRepairRequestActionException("Reload the current repair result"))
                .when(service).closeRequest(73L, VERSION, 42L);
        mockMvc.perform(closePost().with(currentUser(Role.ADMIN)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("errorMessage", "Reload the current repair result"))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    void databaseFailureShowsGenericErrorWithoutLeakingDatabaseDetailsOrClaimingSuccess() throws Exception {
        doThrow(new DataIntegrityViolationException("private database details"))
                .when(service).closeRequest(73L, VERSION, 42L);
        mockMvc.perform(closePost().with(currentUser(Role.ADMIN)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("errorMessage", "Could not confirm closure. Reload the request before trying again."))
                .andExpect(flash().attributeCount(1));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"TECHNICIAN", "REQUESTER"})
    void otherRolesCannotCloseEvenWithCsrf(Role role) throws Exception {
        mockMvc.perform(closePost().with(currentUser(role)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void administratorStillNeedsCsrf() throws Exception {
        mockMvc.perform(closePost().with(currentUser(Role.ADMIN))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousVisitorWithCsrfMustSignIn() throws Exception {
        mockMvc.perform(closePost().with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "not-a-time")
    void missingOrMalformedVersionCannotReachService(String version) throws Exception {
        var request = post("/repair-requests/73/close").with(currentUser(Role.ADMIN)).with(csrf());
        if (version != null) request.param("updatedAt", version);
        mockMvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void databaseRoleCheckCanRejectStaleAdminSession() throws Exception {
        doThrow(new AccessDeniedException("Account is no longer an administrator"))
                .when(service).closeRequest(73L, VERSION, 42L);
        mockMvc.perform(closePost().with(currentUser(Role.ADMIN)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void missingRequestReturnsGeneric404() throws Exception {
        doThrow(new RepairRequestNotFoundException(73L)).when(service).closeRequest(73L, VERSION, 42L);
        mockMvc.perform(closePost().with(currentUser(Role.ADMIN)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(view().name("repair-requests/not-found"));
    }

    @Test
    void getRequestCannotCloseRepair() throws Exception {
        mockMvc.perform(get("/repair-requests/73/close").with(currentUser(Role.ADMIN)))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(RepairRequestStatus.class)
    void onlyCompletedCardShowsCloseFormWithReviewedVersionAndCsrf(RepairRequestStatus state) throws Exception {
        when(service.getDetails(73L, 42L)).thenReturn(details(state, state == RepairRequestStatus.COMPLETED));
        var result = mockMvc.perform(get("/repair-requests/73").with(currentUser(Role.ADMIN))).andExpect(status().isOk());
        if (state == RepairRequestStatus.COMPLETED) {
            result.andExpect(content().string(containsString("Seal replaced. Pressure test passed.")))
                    .andExpect(content().string(containsString("/repair-requests/73/close")))
                    .andExpect(content().string(containsString("Accept result and close")))
                    .andExpect(content().string(containsString("name=\"updatedAt\"")))
                    .andExpect(content().string(containsString(VERSION.toString())))
                    .andExpect(content().string(containsString("name=\"_csrf\"")));
        } else {
            result.andExpect(content().string(not(containsString("/repair-requests/73/close"))));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"TECHNICIAN", "REQUESTER"})
    void otherRolesCanReadResultWithoutCloseAction(Role role) throws Exception {
        when(service.getDetails(73L, 42L)).thenReturn(details(RepairRequestStatus.COMPLETED, false));
        mockMvc.perform(get("/repair-requests/73").with(currentUser(role))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Seal replaced. Pressure test passed.")))
                .andExpect(content().string(not(containsString("/repair-requests/73/close"))));
    }

    private MockHttpServletRequestBuilder closePost() {
        return post("/repair-requests/73/close").param("updatedAt", VERSION.toString());
    }

    private RepairRequestDetails details(RepairRequestStatus state, boolean canClose) {
        return new RepairRequestDetails(73L, "Oil leak in press", "Hydraulic oil leaking around the seal",
                1L, "Press", "PRESS-001", "Workshop A", "Author", "Technician",
                RepairRequestPriority.HIGH, state, "Seal replaced. Pressure test passed.", null,
                VERSION, VERSION, VERSION, VERSION, VERSION, null,
                false, false, false, false, false, false, canClose);
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
