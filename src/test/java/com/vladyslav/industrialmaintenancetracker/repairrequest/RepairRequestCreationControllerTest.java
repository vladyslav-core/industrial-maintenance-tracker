package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCreateForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestEquipmentOption;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotAvailableException;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(RepairRequestController.class)
@Import(SecurityConfig.class)
class RepairRequestCreationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RepairRequestService requestService;

    @ParameterizedTest
    @EnumSource(Role.class)
    void eachRoleCanOpenCreateFormAndSubmitOwnRequest(Role role) throws Exception {
        RepairRequest created = mock(RepairRequest.class);
        when(created.getId()).thenReturn(73L);
        when(requestService.create(any(RepairRequestCreateForm.class), eq(42L)))
                .thenReturn(created);
        when(requestService.availableEquipment()).thenReturn(List.of(
                new RepairRequestEquipmentOption(7L, "Hydraulic press", "PRESS-001")
        ));

        mockMvc.perform(get("/repair-requests/new")
                        .param("equipmentId", "7")
                        .with(currentUser(role)))
                .andExpect(status().isOk())
                .andExpect(view().name("repair-requests/create"))
                .andExpect(model().attributeExists("repairRequestCreateForm"));

        mockMvc.perform(post("/repair-requests")
                        .with(currentUser(role))
                        .with(csrf())
                        .param("equipmentId", "7")
                        .param("title", "Oil leak in press")
                        .param("description", "Hydraulic oil leaking around the seal")
                        .param("createdById", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment/7"));

        verify(requestService).create(any(RepairRequestCreateForm.class), eq(42L));
    }

    @Test
    void rejectsMissingCsrfToken() throws Exception {
        mockMvc.perform(post("/repair-requests")
                        .with(currentUser(Role.REQUESTER))
                        .param("equipmentId", "7")
                        .param("title", "Oil leak in press")
                        .param("description", "Hydraulic oil leaking around the seal"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(requestService);
    }

    @Test
    void returnsValidationErrorsBeforeCallingService() throws Exception {
        when(requestService.availableEquipment()).thenReturn(List.of());

        mockMvc.perform(post("/repair-requests")
                        .with(currentUser(Role.REQUESTER))
                        .with(csrf())
                        .param("equipmentId", "7")
                        .param("title", "  Hi  ")
                        .param("description", "  short  "))
                .andExpect(status().isOk())
                .andExpect(view().name("repair-requests/create"))
                .andExpect(model().attributeHasFieldErrors(
                        "repairRequestCreateForm", "title", "description"
                ));
    }

    @Test
    void showsEquipmentErrorWhenItWasDecommissionedAfterFormOpened() throws Exception {
        when(requestService.create(any(RepairRequestCreateForm.class), eq(42L)))
                .thenThrow(new EquipmentNotAvailableException(7L));
        when(requestService.availableEquipment()).thenReturn(List.of());

        mockMvc.perform(post("/repair-requests")
                        .with(currentUser(Role.REQUESTER))
                        .with(csrf())
                        .param("equipmentId", "7")
                        .param("title", "Oil leak in press")
                        .param("description", "Hydraulic oil leaking around the seal"))
                .andExpect(status().isOk())
                .andExpect(view().name("repair-requests/create"))
                .andExpect(model().attributeHasFieldErrors(
                        "repairRequestCreateForm", "equipmentId"
                ));
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
