package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestDetails;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestListItem;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(RepairRequestController.class)
@Import(SecurityConfig.class)
class RepairRequestReadControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RepairRequestService service;

    @ParameterizedTest
    @EnumSource(Role.class)
    void eachRoleCanOpenListAndVisibleDetailsUsingAuthenticatedIdentity(Role role) throws Exception {
        Instant createdAt = Instant.parse("2026-10-20T10:00:00Z");
        when(service.getList(42L)).thenReturn(List.of(new RepairRequestListItem(
                73L, "Oil leak in press", 7L, "Hydraulic press", "Test User", null,
                RepairRequestPriority.MEDIUM, RepairRequestStatus.NEW, createdAt, createdAt
        )));
        when(service.getDetails(73L, 42L)).thenReturn(details("Oil leaking around the seal"));

        mockMvc.perform(get("/repair-requests")
                        .param("viewerId", "99").param("admin", "true")
                        .with(currentUser(role)))
                .andExpect(status().isOk())
                .andExpect(view().name("repair-requests/list"))
                .andExpect(model().attributeExists("requests"))
                .andExpect(content().string(containsString("/repair-requests/73")));

        mockMvc.perform(get("/repair-requests/73")
                        .param("viewerId", "99").with(currentUser(role)))
                .andExpect(status().isOk())
                .andExpect(view().name("repair-requests/details"))
                .andExpect(content().string(containsString("Workshop A")))
                .andExpect(content().string(containsString("Not assigned")));

        verify(service).getList(42L);
        verify(service).getDetails(73L, 42L);
    }

    @Test
    void unavailableRequestReturns404() throws Exception {
        when(service.getDetails(99L, 42L)).thenThrow(new RepairRequestNotFoundException(99L));

        mockMvc.perform(get("/repair-requests/99").with(currentUser(Role.REQUESTER)))
                .andExpect(status().isNotFound())
                .andExpect(view().name("repair-requests/not-found"));
    }

    @Test
    void anonymousVisitorMustSignIn() throws Exception {
        mockMvc.perform(get("/repair-requests"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/repair-requests/73"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(service);
    }

    @Test
    void problemTextIsEscapedWhenRendered() throws Exception {
        when(service.getDetails(73L, 42L)).thenReturn(details("<script>alert(1)</script>"));

        mockMvc.perform(get("/repair-requests/73").with(currentUser(Role.REQUESTER)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    private RepairRequestDetails details(String description) {
        Instant createdAt = Instant.parse("2026-10-20T10:00:00Z");
        return new RepairRequestDetails(
                73L, "Oil leak in press", description,
                7L, "Hydraulic press", "PRESS-001", "Workshop A", "Test User", null,
                RepairRequestPriority.MEDIUM, RepairRequestStatus.NEW, null, null,
                createdAt, createdAt, null, null, null, null
        );
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
