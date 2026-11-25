package com.vladyslav.industrialmaintenancetracker.maintenance;

import com.vladyslav.industrialmaintenancetracker.common.controller.HomeController;
import com.vladyslav.industrialmaintenancetracker.dashboard.DashboardService;
import com.vladyslav.industrialmaintenancetracker.dashboard.dto.AdminDashboard;
import com.vladyslav.industrialmaintenancetracker.dashboard.dto.DashboardData;
import com.vladyslav.industrialmaintenancetracker.dashboard.dto.TechnicianDashboard;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentListItem;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.maintenance.dto.MaintenanceHistoryItem;
import com.vladyslav.industrialmaintenancetracker.maintenance.dto.MaintenanceHistoryPage;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.dto.CurrentUserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MaintenanceRecordController.class, HomeController.class})
@Import(SecurityConfig.class)
class MaintenanceRecordControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private MaintenanceRecordService records;
    @MockitoBean private DashboardService dashboards;

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "TECHNICIAN"})
    void permittedRolesReadAllHistoryWithEquipmentAndEscapedContractFields(Role role) throws Exception {
        when(records.getHistoryPage(null, 42L)).thenReturn(page(true));

        String html = mvc.perform(get("/maintenance-history").with(currentUser(role)))
                .andExpect(status().isOk()).andExpect(view().name("maintenance/list"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("href=\"/equipment/7\"", "Press &lt;A&gt; (HIST10-A)",
                "href=\"/repair-requests/73\"", "#73", "Oil leak &lt;script&gt;title&lt;/script&gt;",
                "Request created", "2026-11-01T07:00:00Z", "Author", "Rick &amp; Co",
                "Problem description", "First line.\nSecond &lt;b&gt;line&lt;/b&gt;.",
                "Technician", "Teks &lt;Rem&gt;", "Repair result", "Seal replaced.\nTest &lt;script&gt;passed&lt;/script&gt;.",
                "Completed by technician", "2026-11-02T08:00:00Z", "Closed", "2026-11-03T09:00:00Z")
                .doesNotContain("<script>", "<b>line</b>", "<form method=\"post\"");
        verify(records).getHistoryPage(null, 42L);
    }

    @Test
    void equipmentFilterPersistsSelectionAndClearRemovesIt() throws Exception {
        when(records.getHistoryPage(7L, 42L)).thenReturn(page(false));

        String html = mvc.perform(get("/maintenance-history").param("equipmentId", "7")
                        .param("viewerId", "99").param("admin", "true").with(currentUser(Role.TECHNICIAN)))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedEquipmentId", 7L))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("action=\"/maintenance-history\" method=\"get\"", "name=\"equipmentId\"",
                "All equipment", ">Apply</button>", "href=\"/maintenance-history\">Clear</a>",
                "Press &lt;A&gt; (HIST10-A)", "Empty press (HIST10-B)", "#73")
                .containsPattern("<option[^>]*value=\"7\"[^>]*selected=\"selected\"[^>]*>")
                .doesNotContain("href=\"/repair-requests/73\"");
        verify(records).getHistoryPage(7L, 42L);
    }

    @Test
    void existingEquipmentWithNoHistoryKeepsFilterAndShowsEmptyState() throws Exception {
        when(records.getHistoryPage(8L, 42L)).thenReturn(new MaintenanceHistoryPage(List.of(), page(false).equipment()));

        mvc.perform(get("/maintenance-history").param("equipmentId", "8").with(currentUser(Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedEquipmentId", 8L))
                .andExpect(content().string(containsString("No maintenance records found.")));
    }

    @Test
    void emptyDatabaseStillOffersAllEquipmentAndShowsEmptyState() throws Exception {
        when(records.getHistoryPage(null, 42L)).thenReturn(new MaintenanceHistoryPage(List.of(), List.of()));

        String html = mvc.perform(get("/maintenance-history").with(currentUser(Role.ADMIN)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("No maintenance records found.")
                .containsPattern("<option[^>]*value=\"\"[^>]*selected=\"selected\"[^>]*>All equipment</option>");
    }

    @Test
    void emptyFilterMeansAllEquipment() throws Exception {
        when(records.getHistoryPage(null, 42L)).thenReturn(new MaintenanceHistoryPage(List.of(), List.of()));

        mvc.perform(get("/maintenance-history").param("equipmentId", "").with(currentUser(Role.TECHNICIAN)))
                .andExpect(status().isOk());

        verify(records).getHistoryPage(null, 42L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidEquipmentFilterReturns400WithoutReadingHistory(String id) throws Exception {
        mvc.perform(get("/maintenance-history").param("equipmentId", id).with(currentUser(Role.ADMIN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(records);
    }

    @Test
    void nonexistentPositiveEquipmentFilterReturns404() throws Exception {
        when(records.getHistoryPage(999L, 42L)).thenThrow(new EquipmentNotFoundException(999L));

        mvc.perform(get("/maintenance-history").param("equipmentId", "999").with(currentUser(Role.ADMIN)))
                .andExpect(status().isNotFound()).andExpect(view().name("maintenance/not-found"))
                .andExpect(content().string(containsString("The selected equipment does not exist.")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/maintenance-history", "/maintenance-history?equipmentId=7"})
    void requesterCannotReadGeneralHistoryEvenWithForgedPrivileges(String path) throws Exception {
        mvc.perform(get(path).param("viewerId", "99").param("admin", "true").with(currentUser(Role.REQUESTER)))
                .andExpect(status().isForbidden()).andExpect(forwardedUrl("/access-denied"));

        verifyNoInteractions(records);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/maintenance-history", "/maintenance-history?equipmentId=7"})
    void guestIsRedirectedToLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));

        verifyNoInteractions(records);
    }

    @Test
    void databaseAccountRejectionIsNotHiddenByCachedTechnicianAuthority() throws Exception {
        when(records.getHistoryPage(null, 42L)).thenThrow(new AccessDeniedException("Account is inactive"));

        mvc.perform(get("/maintenance-history").with(currentUser(Role.TECHNICIAN)))
                .andExpect(status().isForbidden()).andExpect(forwardedUrl("/access-denied"));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void homeOffersMaintenanceHistoryOnlyToPermittedRoles(Role role) throws Exception {
        when(dashboards.getDashboard(42L, role)).thenReturn(new DashboardData(
                new CurrentUserSummary(42L, "Viewer", role),
                role == Role.ADMIN ? new AdminDashboard(0, 0, 0, 0, 0) : null,
                role == Role.TECHNICIAN ? new TechnicianDashboard(0, 0, List.of(), List.of()) : null,
                List.of()
        ));
        String html = mvc.perform(get("/").with(currentUser(role)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html.contains("href=\"/maintenance-history\""))
                .isEqualTo(role == Role.ADMIN || role == Role.TECHNICIAN);
        verify(dashboards).getDashboard(42L, role);
        verifyNoInteractions(records);
    }

    @Test
    void historyHasNoPostOperationEvenWithValidCsrf() throws Exception {
        mvc.perform(post("/maintenance-history").with(currentUser(Role.ADMIN)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(records);
    }

    private MaintenanceHistoryPage page(boolean canViewRequest) {
        return new MaintenanceHistoryPage(List.of(new MaintenanceHistoryItem(
                5L, 7L, "Press <A>", "HIST10-A", 73L, "Oil leak <script>title</script>",
                Instant.parse("2026-11-01T07:00:00Z"), "Rick & Co", "First line.\nSecond <b>line</b>.",
                "Teks <Rem>", "Seal replaced.\nTest <script>passed</script>.",
                Instant.parse("2026-11-02T08:00:00Z"), Instant.parse("2026-11-03T09:00:00Z"), canViewRequest
        )), List.of(
                new EquipmentListItem(7L, "Press <A>", "HIST10-A", "Workshop A", EquipmentStatus.DECOMMISSIONED),
                new EquipmentListItem(8L, "Empty press", "HIST10-B", "Workshop B", EquipmentStatus.OPERATIONAL)
        ));
    }

    private RequestPostProcessor currentUser(Role role) {
        User viewer = new User("Viewer", "viewer@example.com", "hashed-password", role);
        ReflectionTestUtils.setField(viewer, "id", 42L);
        return user(new CustomUserDetails(viewer));
    }
}
