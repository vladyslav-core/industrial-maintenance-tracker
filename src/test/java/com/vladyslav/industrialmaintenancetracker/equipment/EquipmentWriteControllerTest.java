package com.vladyslav.industrialmaintenancetracker.equipment;

import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentCreateForm;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentEditForm;
import com.vladyslav.industrialmaintenancetracker.exception.DuplicateInventoryNumberException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidEquipmentStatusTransitionException;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(EquipmentController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class EquipmentWriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EquipmentService equipmentService;

    @Test
    void shouldShowCreateFormToAdmin() throws Exception {
        mockMvc.perform(get("/equipment/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("equipment/create"))
                .andExpect(model().attributeExists(
                        "equipmentCreateForm"
                ))
                .andExpect(model().attribute(
                        "canManageEquipment",
                        true
                ));
    }

    @Test
    void shouldCreateValidEquipmentAndRedirectToDetails()
            throws Exception {
        Equipment equipment = mock(Equipment.class);

        when(equipment.getId()).thenReturn(7L);
        when(
                equipmentService.createEquipment(
                        any(EquipmentCreateForm.class)
                )
        ).thenReturn(equipment);

        mockMvc.perform(post("/equipment")
                        .with(csrf())
                        .param("name", "Hydraulic press")
                        .param("inventoryNumber", "PRESS-001")
                        .param(
                                "description",
                                "Main production press"
                        )
                        .param("location", "Workshop A"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment/7"))
                .andExpect(flash().attributeExists(
                        "successMessage"
                ));

        verify(equipmentService).createEquipment(argThat(form ->
                form.getName().equals("Hydraulic press")
                        && form.getInventoryNumber()
                        .equals("PRESS-001")
                        && form.getDescription()
                        .equals("Main production press")
                        && form.getLocation()
                        .equals("Workshop A")
        ));
    }

    @Test
    void shouldRejectInvalidCreateForm() throws Exception {
        mockMvc.perform(post("/equipment")
                        .with(csrf())
                        .param("name", "")
                        .param("inventoryNumber", "")
                        .param("description", "")
                        .param("location", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("equipment/create"))
                .andExpect(model().attributeHasFieldErrors(
                        "equipmentCreateForm",
                        "name",
                        "inventoryNumber",
                        "location"
                ));

        verifyNoInteractions(equipmentService);
    }

    @Test
    void shouldShowDuplicateInventoryNumberError()
            throws Exception {
        when(
                equipmentService.createEquipment(
                        any(EquipmentCreateForm.class)
                )
        ).thenThrow(
                new DuplicateInventoryNumberException("PRESS-001")
        );

        mockMvc.perform(post("/equipment")
                        .with(csrf())
                        .param("name", "Hydraulic press")
                        .param("inventoryNumber", "PRESS-001")
                        .param("description", "")
                        .param("location", "Workshop A"))
                .andExpect(status().isOk())
                .andExpect(view().name("equipment/create"))
                .andExpect(model().attributeHasFieldErrors(
                        "equipmentCreateForm",
                        "inventoryNumber"
                ));
    }

    @Test
    void shouldShowEditFormToAdmin() throws Exception {
        EquipmentEditForm form = new EquipmentEditForm(
                "Hydraulic press",
                "PRESS-001",
                "Main production press",
                "Workshop A"
        );

        when(equipmentService.getEquipmentForEditing(7L))
                .thenReturn(form);

        mockMvc.perform(get("/equipment/7/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("equipment/edit"))
                .andExpect(model().attribute("equipmentId", 7L))
                .andExpect(model().attribute(
                        "equipmentEditForm",
                        form
                ));

        verify(equipmentService).getEquipmentForEditing(7L);
    }

    @Test
    void shouldUpdateValidEquipmentAndRedirectToDetails()
            throws Exception {
        mockMvc.perform(post("/equipment/7/edit")
                        .with(csrf())
                        .param("name", "Updated press")
                        .param("inventoryNumber", "PRESS-002")
                        .param(
                                "description",
                                "Updated description"
                        )
                        .param("location", "Workshop B"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment/7"))
                .andExpect(flash().attributeExists(
                        "successMessage"
                ));

        verify(equipmentService).updateEquipment(
                org.mockito.ArgumentMatchers.eq(7L),
                argThat(form ->
                        form.getName().equals("Updated press")
                                && form.getInventoryNumber()
                                .equals("PRESS-002")
                                && form.getDescription()
                                .equals("Updated description")
                                && form.getLocation()
                                .equals("Workshop B")
                )
        );
    }

    @Test
    void shouldChangeEquipmentStatus() throws Exception {
        mockMvc.perform(post("/equipment/7/status")
                        .with(csrf())
                        .param("status", "UNDER_REPAIR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment/7"))
                .andExpect(flash().attributeExists(
                        "successMessage"
                ));

        verify(equipmentService).changeEquipmentStatus(
                7L,
                EquipmentStatus.UNDER_REPAIR
        );
    }

    @Test
    @WithMockUser(roles = "REQUESTER")
    void shouldDenyStatusChangeToRequester() throws Exception {
        mockMvc.perform(post("/equipment/7/status")
                        .with(csrf())
                        .param("status", "UNDER_REPAIR"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void shouldRedirectToListWhenEquipmentForStatusChangeDoesNotExist()
            throws Exception {
        doThrow(new EquipmentNotFoundException(99L))
                .when(equipmentService)
                .changeEquipmentStatus(
                        99L,
                        EquipmentStatus.UNDER_REPAIR
                );

        mockMvc.perform(post("/equipment/99/status")
                        .with(csrf())
                        .param("status", "UNDER_REPAIR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment"))
                .andExpect(flash().attributeExists(
                        "errorMessage"
                ));
    }

    @Test
    void shouldReturnToDetailsWhenStatusTransitionIsInvalid()
            throws Exception {
        doThrow(
                new InvalidEquipmentStatusTransitionException(
                        EquipmentStatus.DECOMMISSIONED,
                        EquipmentStatus.OPERATIONAL
                )
        )
                .when(equipmentService)
                .changeEquipmentStatus(
                        7L,
                        EquipmentStatus.OPERATIONAL
                );

        mockMvc.perform(post("/equipment/7/status")
                        .with(csrf())
                        .param("status", "OPERATIONAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment/7"))
                .andExpect(flash().attributeExists(
                        "errorMessage"
                ));
    }
}