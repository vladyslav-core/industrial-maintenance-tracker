package com.vladyslav.industrialmaintenancetracker.dashboard;

import com.vladyslav.industrialmaintenancetracker.common.controller.HomeController;
import com.vladyslav.industrialmaintenancetracker.dashboard.dto.AdminDashboard;
import com.vladyslav.industrialmaintenancetracker.dashboard.dto.DashboardData;
import com.vladyslav.industrialmaintenancetracker.dashboard.dto.TechnicianDashboard;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestPriority;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestPreview;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.dto.CurrentUserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    private static final Instant CREATED = Instant.parse("2026-11-25T09:15:00Z");

    @Autowired private MockMvc mvc;
    @MockitoBean private DashboardService dashboards;

    @Test
    void anonymousVisitorIsRedirectedBeforeReadingAnyDashboardData() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(dashboards);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void authenticationWithoutADatabaseIdentityCannotReadTheDashboard() throws Exception {
        mvc.perform(get("/")).andExpect(status().isForbidden()).andExpect(forwardedUrl("/access-denied"));
        verifyNoInteractions(dashboards);
    }

    @Test
    void administratorSeesFullCountsAndAccurateStatusLinksWithAnInformationalCriticalCount() throws Exception {
        when(dashboards.getDashboard(42L, Role.ADMIN)).thenReturn(page(Role.ADMIN));

        String html = open(Role.ADMIN);

        assertMetric(html, "new-request-count", 11);
        assertMetric(html, "assigned-request-count", 12);
        assertMetric(html, "in-progress-request-count", 13);
        assertMetric(html, "awaiting-review-count", 14);
        assertMetric(html, "critical-open-request-count", 5);
        assertThat(html).contains("href=\"/repair-requests?status=NEW\"", "href=\"/repair-requests?status=ASSIGNED\"",
                "href=\"/repair-requests?status=IN_PROGRESS\"", "href=\"/repair-requests?status=COMPLETED\"",
                "Excludes closed and cancelled requests", "Latest requests", "View all requests",
                "href=\"/equipment/new\"", "href=\"/users/new\"", "href=\"/repair-requests/71\"", "href=\"/repair-requests/72\"")
                .doesNotContain("id=\"my-assigned-count\"", "id=\"requester-dashboard\"")
                .doesNotContainPattern("href=\"[^\"]*priority=CRITICAL");
    }

    @Test
    void technicianSeesOnlyTheTwoWorkingPreviewsAndExistingScopedTransitions() throws Exception {
        when(dashboards.getDashboard(42L, Role.TECHNICIAN)).thenReturn(page(Role.TECHNICIAN));

        String html = open(Role.TECHNICIAN);

        assertMetric(html, "my-assigned-count", 7);
        assertMetric(html, "my-in-progress-count", 9);
        assertThat(html).contains("Assigned to me", "My work in progress", "Assigned test repair", "Working test repair",
                "href=\"/repair-requests?scope=ASSIGNED_TO_ME&amp;status=ASSIGNED\"",
                "href=\"/repair-requests?scope=ASSIGNED_TO_ME&amp;status=IN_PROGRESS\"",
                "href=\"/repair-requests?scope=CREATED_BY_ME\"", "Created by me", "All visible requests")
                .doesNotContain("id=\"administrator-dashboard\"", "id=\"latest-requests\"", "href=\"/users\"",
                        "href=\"/users/new\"", "href=\"/equipment/new\"", "Critical open requests");
    }

    @Test
    void requesterSeesOwnLatestIncludingFinalStatesWithoutPrivilegedMetricsOrNavigation() throws Exception {
        when(dashboards.getDashboard(42L, Role.REQUESTER)).thenReturn(page(Role.REQUESTER));

        String html = open(Role.REQUESTER);

        assertThat(html).contains("My latest requests", "View my requests", "href=\"/repair-requests/71\"",
                "href=\"/repair-requests/72\"", "data-status=\"NEW\"", "data-status=\"CLOSED\"")
                .doesNotContain("id=\"administrator-dashboard\"", "id=\"technician-dashboard\"", "href=\"/users\"",
                        "href=\"/maintenance-history\"", "href=\"/equipment/new\"", "Critical open requests");
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void navigationUsesTheRoleOfTheCurrentDashboardAndKeepsRealLogoutCsrf(Role role) throws Exception {
        when(dashboards.getDashboard(42L, role)).thenReturn(page(role));

        String html = open(role);

        assertThat(html).contains("<h1>Dashboard</h1>", "Current &amp; Viewer", "href=\"/repair-requests/new\"",
                "href=\"/repair-requests\"", "href=\"/equipment\"", "action=\"/logout\"", "method=\"post\"",
                "name=\"_csrf\"", "Sign out", "aria-current=\"page\"")
                .doesNotContain("Application is running successfully.", "Cached session name", "private-session-hash");
        assertThat(html.contains("href=\"/users\"")).isEqualTo(role == Role.ADMIN);
        assertThat(html.contains("href=\"/maintenance-history\"")).isEqualTo(role != Role.REQUESTER);
        assertThat(Pattern.compile("href=\"/repair-requests/(\\d+)\"").matcher(html).results()
                .map(match -> Long.valueOf(match.group(1))).toList()).containsExactly(71L, 72L);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void forgedViewerRoleScopeAndLimitAreIgnoredWhileIdentityComesFromThePrincipal(Role role) throws Exception {
        DashboardData data = page(role);
        when(dashboards.getDashboard(42L, role)).thenReturn(data);

        mvc.perform(get("/").with(currentUser(role)).param("viewerId", "999").param("role", "ADMIN")
                        .param("admin", "true").param("technicianId", "999").param("scope", "ALL")
                        .param("limit", "100").param("status", "CANCELLED"))
                .andExpect(status().isOk()).andExpect(view().name("home"))
                .andExpect(model().attribute("dashboard", data));
        verify(dashboards).getDashboard(42L, role);
        verifyNoMoreInteractions(dashboards);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void emptySelectionsHaveRoleSpecificMessagesAndValidTransitions(Role role) throws Exception {
        when(dashboards.getDashboard(42L, role)).thenReturn(emptyPage(role));

        String html = open(role);

        assertThat(html).contains("href=\"/repair-requests/new\"").doesNotContain("href=\"/repair-requests/123\"", "<tbody>");
        switch (role) {
            case ADMIN -> {
                assertThat(html).contains("No repair requests yet.", "View all requests");
                assertMetric(html, "new-request-count", 0);
                assertMetric(html, "critical-open-request-count", 0);
            }
            case TECHNICIAN -> {
                assertThat(html).contains("No requests assigned to you.", "No repairs in progress.");
                assertMetric(html, "my-assigned-count", 0);
                assertMetric(html, "my-in-progress-count", 0);
            }
            case REQUESTER -> assertThat(html).contains("You have not created any requests yet.", "View my requests");
        }
    }

    @Test
    void previewUsesContractFieldsAndEscapesUserSuppliedNamesAndProblemText() throws Exception {
        var item = new RepairRequestPreview(71L, "Fault <script>check()</script>", 7L, "Press <A> & B",
                RepairRequestStatus.CLOSED, RepairRequestPriority.CRITICAL, CREATED);
        when(dashboards.getDashboard(42L, Role.REQUESTER)).thenReturn(new DashboardData(
                new CurrentUserSummary(42L, "Viewer <b>name</b>", Role.REQUESTER), null, null, List.of(item)
        ));

        String html = open(Role.REQUESTER);

        assertThat(html).contains("Viewer &lt;b&gt;name&lt;/b&gt;", "Fault &lt;script&gt;check()&lt;/script&gt;",
                "Press &lt;A&gt; &amp; B", "href=\"/repair-requests/71\"", "#71", "href=\"/equipment/7\"",
                "data-status=\"CLOSED\"", "data-priority=\"CRITICAL\"", "datetime=\"2026-11-25T09:15:00Z\"")
                .doesNotContain("<script>", "<b>name</b>");
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void databaseAccountRejectionIsForwardedAs403WithoutRenderingDashboardData(Role role) throws Exception {
        when(dashboards.getDashboard(42L, role)).thenThrow(new AccessDeniedException("Account is unavailable"));

        mvc.perform(get("/").with(currentUser(role))).andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/access-denied"));
        verify(dashboards).getDashboard(42L, role);
    }

    @Test
    void logoutUsesTheTokenRenderedByTheActualHomeForm() throws Exception {
        when(dashboards.getDashboard(42L, Role.REQUESTER)).thenReturn(page(Role.REQUESTER));
        var result = mvc.perform(get("/").with(currentUser(Role.REQUESTER))).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        var matcher = Pattern.compile("<input\\b(?=[^>]*\\bname=\"_csrf\")[^>]*\\bvalue=\"([^\"]+)\"[^>]*>")
                .matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();

        mvc.perform(post("/logout").session(session).with(currentUser(Role.REQUESTER)).param("_csrf", matcher.group(1)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
    }

    @Test
    void homeHasNoPostOperationEvenWithValidAuthenticationAndCsrf() throws Exception {
        mvc.perform(post("/").with(currentUser(Role.REQUESTER)).with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(dashboards);
    }

    private String open(Role role) throws Exception {
        return mvc.perform(get("/").with(currentUser(role))).andExpect(status().isOk()).andExpect(view().name("home"))
                .andReturn().getResponse().getContentAsString();
    }

    private DashboardData page(Role role) {
        var viewer = new CurrentUserSummary(42L, "Current & Viewer", role);
        var first = preview(71L, "Own new request", RepairRequestStatus.NEW);
        var closed = preview(72L, "Accepted repair", RepairRequestStatus.CLOSED);
        return switch (role) {
            case ADMIN -> new DashboardData(viewer, new AdminDashboard(11, 12, 13, 14, 5), null, List.of(first, closed));
            case TECHNICIAN -> new DashboardData(viewer, null, new TechnicianDashboard(7, 9,
                    List.of(preview(71L, "Assigned test repair", RepairRequestStatus.ASSIGNED)),
                    List.of(preview(72L, "Working test repair", RepairRequestStatus.IN_PROGRESS))), List.of());
            case REQUESTER -> new DashboardData(viewer, null, null, List.of(first, closed));
        };
    }

    private DashboardData emptyPage(Role role) {
        return new DashboardData(new CurrentUserSummary(42L, "Current & Viewer", role),
                role == Role.ADMIN ? new AdminDashboard(0, 0, 0, 0, 0) : null,
                role == Role.TECHNICIAN ? new TechnicianDashboard(0, 0, List.of(), List.of()) : null,
                List.of());
    }

    private RepairRequestPreview preview(Long id, String title, RepairRequestStatus state) {
        return new RepairRequestPreview(id, title, 7L, "Dashboard press", state, RepairRequestPriority.HIGH, CREATED);
    }

    private RequestPostProcessor currentUser(Role role) {
        User account = new User("Cached session name", "viewer@example.com", "private-session-hash", role);
        ReflectionTestUtils.setField(account, "id", 42L);
        return user(new CustomUserDetails(account));
    }

    private static void assertMetric(String html, String id, long count) {
        assertThat(html).containsPattern("\\bid=\"" + Pattern.quote(id) + "\"[^>]*>\\s*" + count + "\\s*<");
    }
}
