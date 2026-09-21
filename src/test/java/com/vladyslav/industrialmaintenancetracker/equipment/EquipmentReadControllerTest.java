package com.vladyslav.industrialmaintenancetracker.equipment;

import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentDetails;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentListItem;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(EquipmentController.class)
@Import(SecurityConfig.class)
class EquipmentReadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EquipmentService equipmentService;

    @Test
    @WithMockUser(roles = "TECHNICIAN")
    void shouldShowFilteredEquipmentListToTechnician()
            throws Exception {
        EquipmentListItem item = new EquipmentListItem(
                7L,
                "Hydraulic press",
                "PRESS-001",
                "Workshop A",
                EquipmentStatus.UNDER_REPAIR
        );

        when(
                equipmentService.getEquipmentList(
                        "press",
                        EquipmentStatus.UNDER_REPAIR
                )
        ).thenReturn(List.of(item));

        mockMvc.perform(get("/equipment")
                        .param("search", "press")
                        .param("status", "UNDER_REPAIR"))
                .andExpect(status().isOk())
                .andExpect(view().name("equipment/list"))
                .andExpect(model().attribute(
                        "equipment",
                        List.of(item)
                ))
                .andExpect(model().attribute("search", "press"))
                .andExpect(model().attribute(
                        "selectedStatus",
                        EquipmentStatus.UNDER_REPAIR
                ))
                .andExpect(model().attributeExists("statuses"))
                .andExpect(model().attribute(
                        "canManageEquipment",
                        false
                ));

        verify(equipmentService).getEquipmentList(
                "press",
                EquipmentStatus.UNDER_REPAIR
        );
    }

    @Test
    @WithMockUser(roles = "REQUESTER")
    void shouldShowEquipmentDetailsToRequester()
            throws Exception {
        EquipmentDetails details = new EquipmentDetails(
                7L,
                "Hydraulic press",
                "PRESS-001",
                "Main production press",
                "Workshop A",
                EquipmentStatus.OPERATIONAL,
                Instant.parse("2026-09-21T10:00:00Z"),
                Instant.parse("2026-09-21T11:00:00Z")
        );

        when(equipmentService.getEquipmentDetails(7L))
                .thenReturn(details);

        mockMvc.perform(get("/equipment/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("equipment/details"))
                .andExpect(model().attribute(
                        "equipment",
                        details
                ))
                .andExpect(model().attributeExists("statuses"))
                .andExpect(model().attribute(
                        "canManageEquipment",
                        false
                ));

        verify(equipmentService).getEquipmentDetails(7L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldExposeManagementControlsToAdmin()
            throws Exception {
        when(equipmentService.getEquipmentList(null, null))
                .thenReturn(List.of());

        mockMvc.perform(get("/equipment"))
                .andExpect(status().isOk())
                .andExpect(view().name("equipment/list"))
                .andExpect(model().attribute(
                        "canManageEquipment",
                        true
                ));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRedirectWhenEquipmentDoesNotExist()
            throws Exception {
        doThrow(new EquipmentNotFoundException(99L))
                .when(equipmentService)
                .getEquipmentDetails(99L);

        mockMvc.perform(get("/equipment/99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void shouldRequireAuthenticationForEquipmentList()
            throws Exception {
        mockMvc.perform(get("/equipment"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(equipmentService);
    }

    @Test
    @WithMockUser(roles = "TECHNICIAN")
    void shouldDenyCreatePageToTechnician()
            throws Exception {
        mockMvc.perform(get("/equipment/new"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }
}