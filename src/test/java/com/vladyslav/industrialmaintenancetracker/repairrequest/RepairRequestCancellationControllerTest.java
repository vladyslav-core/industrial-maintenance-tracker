package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCancellationForm;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RepairRequestController.class)
@Import(SecurityConfig.class)
class RepairRequestCancellationControllerTest {

    private static final Instant VERSION = Instant.parse("2026-10-22T08:00:00.123456Z");
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RepairRequestService service;

    @ParameterizedTest
    @EnumSource(Role.class)
    void authenticatedRolesReachServiceWithOnlyReasonVersionAndCurrentActor(Role role) throws Exception {
        mockMvc.perform(validPost().with(currentUser(role)).with(csrf())
                        .param("viewerId", "99").param("authorId", "99").param("status", "CLOSED")
                        .param("closedAt", Instant.EPOCH.toString()).param("equipmentId", "99")
                        .param("assignedTechnicianId", "99").param("resolution", "Forged result"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("successMessage", "Request cancelled."));
        var captor = ArgumentCaptor.forClass(RepairRequestCancellationForm.class);
        verify(service).cancelRequest(eq(73L), captor.capture(), eq(42L));
        assertThat(captor.getValue().getCancellationReason()).isEqualTo("Duplicate request for this fault.");
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(VERSION);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"    ", "four", "  four  ", "\u2003\u2003\u2003\u2003\u2003"})
    void invalidReasonsRedisplayCardWithFieldErrorAndDoNotSave(String reason) throws Exception {
        prepareEditableCard();
        var post = post("/repair-requests/73/cancel").with(currentUser(Role.REQUESTER)).with(csrf())
                .param("updatedAt", VERSION.toString());
        if (reason != null) post.param("cancellationReason", reason);
        mockMvc.perform(post).andExpect(status().isOk()).andExpect(view().name("repair-requests/details"))
                .andExpect(model().attributeHasFieldErrors("cancellationForm", "cancellationReason"));
        verify(service, never()).cancelRequest(any(), any(), any());
    }

    @Test
    void oversizedReasonIsRejectedAndInputIsEscapedWhenRedisplayed() throws Exception {
        prepareEditableCard();
        String reason = "<script>alert(1)</script>" + "x".repeat(500);
        mockMvc.perform(post("/repair-requests/73/cancel").with(currentUser(Role.REQUESTER)).with(csrf())
                        .param("updatedAt", VERSION.toString()).param("cancellationReason", reason))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("cancellationForm", "cancellationReason"))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        verify(service, never()).cancelRequest(any(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "not-a-time")
    void missingOrMalformedVersionRedisplaysReasonWithoutCancelling(String version) throws Exception {
        prepareEditableCard();
        var post = post("/repair-requests/73/cancel").with(currentUser(Role.REQUESTER)).with(csrf())
                .param("cancellationReason", "Duplicate request for this fault.");
        if (version != null) post.param("updatedAt", version);
        mockMvc.perform(post).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("cancellationForm", "updatedAt"))
                .andExpect(content().string(containsString("Duplicate request for this fault.")));
        verify(service, never()).cancelRequest(any(), any(), any());
    }

    @Test
    void staleCardRedirectsToReloadWithErrorAndNoSuccess() throws Exception {
        doThrow(new InvalidRepairRequestActionException("The request has changed. Reload it before cancelling."))
                .when(service).cancelRequest(eq(73L), any(), eq(42L));
        mockMvc.perform(validPost().with(currentUser(Role.REQUESTER)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("errorMessage", "The request has changed. Reload it before cancelling."))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    void invalidFormStillChecksWhetherRequestIsCancellable() throws Exception {
        when(service.getCancellationForm(73L, 42L)).thenThrow(new InvalidRepairRequestActionException("Already assigned"));
        mockMvc.perform(post("/repair-requests/73/cancel").with(currentUser(Role.REQUESTER)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("errorMessage", "Already assigned"));
        verify(service, never()).cancelRequest(any(), any(), any());
        verify(service, never()).getDetails(any(), any());
    }

    @Test
    void assignedNonAuthorCannotCancelVisibleRepair() throws Exception {
        doThrow(new AccessDeniedException("Only the author or an administrator can cancel"))
                .when(service).cancelRequest(eq(73L), any(), eq(42L));
        mockMvc.perform(validPost().with(currentUser(Role.TECHNICIAN)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void hiddenRequestReturnsGeneric404EvenForInvalidForm() throws Exception {
        when(service.getCancellationForm(73L, 42L)).thenThrow(new RepairRequestNotFoundException(73L));
        mockMvc.perform(post("/repair-requests/73/cancel").with(currentUser(Role.REQUESTER)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(view().name("repair-requests/not-found"));
        verify(service, never()).cancelRequest(any(), any(), any());
    }

    @Test
    void hiddenRequestWithValidInputAlsoReturns404() throws Exception {
        doThrow(new RepairRequestNotFoundException(73L)).when(service).cancelRequest(eq(73L), any(), eq(42L));
        mockMvc.perform(validPost().with(currentUser(Role.REQUESTER)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(view().name("repair-requests/not-found"));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleNeedsCsrf(Role role) throws Exception {
        mockMvc.perform(validPost().with(currentUser(role))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousPostWithCsrfMustSignIn() throws Exception {
        mockMvc.perform(validPost().with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(service);
    }

    @Test
    void getCannotCancel() throws Exception {
        mockMvc.perform(get("/repair-requests/73/cancel").with(currentUser(Role.ADMIN)))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @Test
    void allowedCardContainsInlineReasonVersionAndCsrf() throws Exception {
        prepareEditableCard();
        mockMvc.perform(get("/repair-requests/73").with(currentUser(Role.REQUESTER)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/repair-requests/73/cancel")))
                .andExpect(content().string(containsString("name=\"cancellationReason\"")))
                .andExpect(content().string(containsString(VERSION.toString())))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @ParameterizedTest
    @EnumSource(RepairRequestStatus.class)
    void cardWithoutPermissionNeverShowsCancellationForm(RepairRequestStatus state) throws Exception {
        when(service.getDetails(73L, 42L)).thenReturn(details(state, false));
        var result = mockMvc.perform(get("/repair-requests/73").with(currentUser(Role.REQUESTER)))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("/repair-requests/73/cancel"))));
        if (state == RepairRequestStatus.CANCELLED) {
            result.andExpect(content().string(containsString("Duplicate &lt;request&gt;")))
                    .andExpect(content().string(containsString("Cancelled")));
        }
    }

    private void prepareEditableCard() {
        when(service.getCancellationForm(73L, 42L)).thenReturn(new RepairRequestCancellationForm(null, VERSION));
        when(service.getDetails(73L, 42L)).thenReturn(details(RepairRequestStatus.NEW, true));
    }

    private RepairRequestDetails details(RepairRequestStatus state, boolean canCancel) {
        return new RepairRequestDetails(73L, "Oil leak in press", "Hydraulic oil leaking around the seal",
                1L, "Press", "PRESS-001", "Workshop A", "Author", null,
                RepairRequestPriority.HIGH, state, null, "Duplicate <request>",
                VERSION, VERSION, null, null, null, state == RepairRequestStatus.CANCELLED ? VERSION : null,
                false, false, false, false, false, false, false, canCancel);
    }

    private MockHttpServletRequestBuilder validPost() {
        return post("/repair-requests/73/cancel").param("updatedAt", VERSION.toString())
                .param("cancellationReason", "  Duplicate request for this fault.  ");
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
