package com.vladyslav.industrialmaintenancetracker.equipment;

import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentDetails;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentDetailsPage;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentListItem;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.maintenance.dto.MaintenanceHistoryItem;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestPriority;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestListItem;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @MockitoBean
    private EquipmentDetailsService equipmentDetailsService;

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

    @ParameterizedTest
    @EnumSource(Role.class)
    void shouldShowEmptyEquipmentHistoryToEveryRole(Role role)
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

        when(equipmentDetailsService.getDetailsPage(7L, 42L))
                .thenReturn(new EquipmentDetailsPage(details, List.of(), List.of()));

        mockMvc.perform(get("/equipment/7").with(currentUser(role)))
                .andExpect(status().isOk())
                .andExpect(view().name("equipment/details"))
                .andExpect(model().attribute(
                        "equipment",
                        details
                ))
                .andExpect(model().attributeExists("statuses"))
                .andExpect(model().attribute("maintenanceHistory", List.of()))
                .andExpect(model().attribute("openRepairRequests", List.of()))
                .andExpect(content().string(containsString("No open repair requests.")))
                .andExpect(content().string(containsString("Maintenance history")))
                .andExpect(content().string(containsString("No maintenance records found.")))
                .andExpect(model().attribute(
                        "canManageEquipment",
                        role == Role.ADMIN
                ));

        verify(equipmentDetailsService).getDetailsPage(7L, 42L);
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

    @ParameterizedTest
    @EnumSource(Role.class)
    void shouldRedirectWhenEquipmentDoesNotExist(Role role)
            throws Exception {
        doThrow(new EquipmentNotFoundException(99L))
                .when(equipmentDetailsService)
                .getDetailsPage(99L, 42L);

        mockMvc.perform(get("/equipment/99").with(currentUser(role)))
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
    void shouldRequireAuthenticationForEquipmentDetails() throws Exception {
        mockMvc.perform(get("/equipment/7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(equipmentService, equipmentDetailsService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRenderOnlyPermittedRequestLinksUsingAuthenticatedIdentity(boolean canViewRequest) throws Exception {
        var item = historyItem(canViewRequest, "Seal replaced. Pressure test passed.");
        when(equipmentDetailsService.getDetailsPage(7L, 42L))
                .thenReturn(new EquipmentDetailsPage(details(), List.of(item), List.of()));

        var response = mockMvc.perform(get("/equipment/7").with(currentUser(Role.REQUESTER))
                        .param("viewerId", "99").param("admin", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#73")))
                .andExpect(content().string(containsString("Oil leak in press")))
                .andExpect(content().string(containsString("History Author")))
                .andExpect(content().string(containsString("History Technician")))
                .andExpect(content().string(containsString("2026-11-01T07:00:00Z")))
                .andExpect(content().string(containsString("2026-11-02T08:00:00Z")))
                .andExpect(content().string(containsString("2026-11-03T09:00:00Z")));

        String link = "href=\"/repair-requests/73\"";
        if (canViewRequest) {
            response.andExpect(content().string(containsString(link)));
        } else {
            response.andExpect(content().string(not(containsString(link))));
        }
        verify(equipmentDetailsService).getDetailsPage(7L, 42L);
    }

    @Test
    void shouldEscapeHistoryTextAndPreserveLineBreaks() throws Exception {
        var item = historyItem(false, "Seal replaced.\n<script>alert(1)</script>");
        when(equipmentDetailsService.getDetailsPage(7L, 42L))
                .thenReturn(new EquipmentDetailsPage(details(), List.of(item), List.of()));

        mockMvc.perform(get("/equipment/7").with(currentUser(Role.REQUESTER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Oil &lt;leak&gt; around the seal")))
                .andExpect(content().string(containsString("Seal replaced.\n&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(containsString("white-space: pre-wrap")));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void shouldRenderOpenRequestFieldsAndLinksForEveryRole(Role role) throws Exception {
        Instant created = Instant.parse("2026-11-01T07:00:00Z");
        var openRequests = List.of(
                new RepairRequestListItem(74L, "Pressure check completed", 7L, "Hydraulic press",
                        "Author", "Test Technician", RepairRequestPriority.MEDIUM,
                        RepairRequestStatus.COMPLETED, created, created),
                new RepairRequestListItem(73L, "Oil <leak> in press", 7L, "Hydraulic press",
                        "Author", null, RepairRequestPriority.HIGH, RepairRequestStatus.NEW, created, created)
        );
        when(equipmentDetailsService.getDetailsPage(7L, 42L))
                .thenReturn(new EquipmentDetailsPage(details(), List.of(), openRequests));

        mockMvc.perform(get("/equipment/7").with(currentUser(role)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("openRepairRequests", openRequests))
                .andExpect(content().string(containsString("Open repair requests")))
                .andExpect(content().string(containsString("href=\"/repair-requests/74\"")))
                .andExpect(content().string(containsString("href=\"/repair-requests/73\"")))
                .andExpect(content().string(containsString("Oil &lt;leak&gt; in press")))
                .andExpect(content().string(containsString("COMPLETED")))
                .andExpect(content().string(containsString("NEW")))
                .andExpect(content().string(containsString("HIGH")))
                .andExpect(content().string(containsString("Test Technician")))
                .andExpect(content().string(containsString("Not assigned")))
                .andExpect(content().string(not(containsString("No open repair requests."))));
    }

    @Test
    @WithMockUser(roles = "TECHNICIAN")
    void shouldDenyCreatePageToTechnician()
            throws Exception {
        mockMvc.perform(get("/equipment/new"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    private EquipmentDetails details() {
        return new EquipmentDetails(7L, "Hydraulic press", "PRESS-001", null, "Workshop A",
                EquipmentStatus.OPERATIONAL, Instant.parse("2026-09-21T10:00:00Z"),
                Instant.parse("2026-09-21T11:00:00Z"));
    }

    private MaintenanceHistoryItem historyItem(boolean canViewRequest, String workDescription) {
        return new MaintenanceHistoryItem(
                5L, 7L, "Hydraulic press", "PRESS-001", 73L, "Oil leak in press",
                Instant.parse("2026-11-01T07:00:00Z"), "History Author", "Oil <leak> around the seal",
                "History Technician", workDescription, Instant.parse("2026-11-02T08:00:00Z"),
                Instant.parse("2026-11-03T09:00:00Z"), canViewRequest
        );
    }

    private RequestPostProcessor currentUser(Role role) {
        User account = new User("Test User", "user@example.com", "hashed-password", role);
        ReflectionTestUtils.setField(account, "id", 42L);
        return user(new CustomUserDetails(account));
    }
}
