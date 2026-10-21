package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestEditConflictException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestEditForm;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import org.junit.jupiter.api.Test;
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
class RepairRequestEditControllerTest {

    private static final Instant VERSION = Instant.parse("2026-10-21T06:00:00.123456Z");
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RepairRequestService service;

    @Test
    void formContainsVersionAndCsrfAndOnlyEditableFields() throws Exception {
        when(service.getEditForm(73L, 42L)).thenReturn(form());
        mockMvc.perform(get("/repair-requests/73/edit").with(currentUser()))
                .andExpect(status().isOk())
                .andExpect(view().name("repair-requests/edit"))
                .andExpect(content().string(containsString("name=\"updatedAt\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("name=\"priority\""))))
                .andExpect(content().string(not(containsString("name=\"equipmentId\""))));
    }

    @Test
    void validPostUsesAuthenticatedAuthorAndIgnoresProtectedFields() throws Exception {
        mockMvc.perform(validPost().with(csrf()).param("priority", "CRITICAL")
                        .param("equipmentId", "99").param("createdBy", "99").param("status", "CLOSED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attribute("successMessage", "Request details updated."));
        ArgumentCaptor<RepairRequestEditForm> captor = ArgumentCaptor.forClass(RepairRequestEditForm.class);
        verify(service).updateDetails(eq(73L), captor.capture(), eq(42L));
        assertThat(captor.getValue().getTitle()).isEqualTo("Updated problem title");
        assertThat(captor.getValue().getDescription()).isEqualTo("Updated problem description");
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(VERSION);
    }

    @Test
    void invalidFieldsAndMissingVersionRedisplayWithoutSaving() throws Exception {
        when(service.getEditForm(73L, 42L)).thenReturn(form());
        mockMvc.perform(post("/repair-requests/73/edit").with(currentUser()).with(csrf())
                        .param("title", " ").param("description", "short"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("editForm", "title", "description", "updatedAt"))
                .andExpect(view().name("repair-requests/edit"));
        verify(service, never()).updateDetails(any(), any(), any());
    }

    @Test
    void staleFormPreservesInputAndDisablesSave() throws Exception {
        doThrow(new RepairRequestEditConflictException()).when(service).updateDetails(eq(73L), any(), eq(42L));
        mockMvc.perform(validPost().with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(view().name("repair-requests/edit"))
                .andExpect(model().attribute("canSave", false))
                .andExpect(content().string(containsString("Updated problem title")))
                .andExpect(content().string(containsString("Your changes were not saved")))
                .andExpect(content().string(not(containsString(">Save changes</button>"))));
    }

    @Test
    void noLongerEditableGetRedirectsToDetailsWithMessage() throws Exception {
        when(service.getEditForm(73L, 42L)).thenThrow(new RepairRequestEditConflictException());
        mockMvc.perform(get("/repair-requests/73/edit").with(currentUser()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/repair-requests/73"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void missingCsrfIsForbiddenBeforeCallingService() throws Exception {
        mockMvc.perform(validPost()).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void visibleNonAuthorCannotEdit() throws Exception {
        doThrow(new AccessDeniedException("Only the author can edit")).when(service)
                .updateDetails(eq(73L), any(), eq(42L));
        mockMvc.perform(validPost().with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void hiddenRequestReturnsGeneric404() throws Exception {
        when(service.getEditForm(73L, 42L)).thenThrow(new RepairRequestNotFoundException(73L));
        mockMvc.perform(get("/repair-requests/73/edit").with(currentUser()))
                .andExpect(status().isNotFound()).andExpect(view().name("repair-requests/not-found"));
    }

    private RepairRequestEditForm form() {
        return new RepairRequestEditForm("Oil leak in press", "Hydraulic oil leaking around the seal", VERSION);
    }

    private MockHttpServletRequestBuilder validPost() {
        return post("/repair-requests/73/edit").with(currentUser())
                .param("title", "  Updated problem title  ")
                .param("description", "  Updated problem description  ")
                .param("updatedAt", VERSION.toString());
    }

    private RequestPostProcessor currentUser() {
        User account = mock(User.class);
        when(account.getId()).thenReturn(42L);
        when(account.getFullName()).thenReturn("Test User");
        when(account.getEmail()).thenReturn("user@example.com");
        when(account.getPasswordHash()).thenReturn("hashed-password");
        when(account.getRole()).thenReturn(Role.REQUESTER);
        when(account.isActive()).thenReturn(true);
        return user(new CustomUserDetails(account));
    }
}
