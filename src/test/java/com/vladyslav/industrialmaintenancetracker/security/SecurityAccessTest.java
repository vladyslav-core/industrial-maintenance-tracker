package com.vladyslav.industrialmaintenancetracker.security;

import com.vladyslav.industrialmaintenancetracker.common.controller.HomeController;
import com.vladyslav.industrialmaintenancetracker.user.UserController;
import com.vladyslav.industrialmaintenancetracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserListItem;

import java.util.List;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({
        HomeController.class,
        LoginController.class,
        AccessDeniedController.class,
        UserController.class
})
@Import(SecurityConfig.class)
class SecurityAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void shouldRedirectAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void shouldAllowAuthenticatedUserToOpenHomePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"));
    }

    @Test
    void shouldShowLoginPageToAnonymousUser() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void shouldLogoutAuthenticatedUser() throws Exception {
        mockMvc.perform(logout())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void shouldRenderLoginFormWithRequiredFieldsAndCsrfToken()
            throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("name=\"username\"")
                ))
                .andExpect(content().string(
                        containsString("name=\"password\"")
                ))
                .andExpect(content().string(
                        containsString("name=\"_csrf\"")
                ));
    }

    @Test
    void shouldShowLogoutMessageToAnonymousUser() throws Exception {
        mockMvc.perform(get("/login?logout"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(
                        containsString("You have been signed out.")
                ));
    }

    @Test
    void shouldRejectInvalidCredentials() throws Exception {
        mockMvc.perform(formLogin()
                        .user("unknown@example.com")
                        .password("wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void shouldShowLoginErrorMessageToAnonymousUser() throws Exception {
        mockMvc.perform(get("/login?error"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(
                        containsString("Invalid email or password.")
                ));
    }

    @Test
    void shouldShowAccessDeniedPage() throws Exception {
        mockMvc.perform(get("/access-denied"))
                .andExpect(status().isOk())
                .andExpect(view().name("access-denied"))
                .andExpect(content().string(
                        containsString(
                                "You do not have permission to access this page."
                        )
                ));
    }

    @Test
    @WithMockUser(
            username = "admin@example.com",
            roles = "ADMIN"
    )
    void shouldAllowAdminToAccessUserManagement() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/list"))
                .andExpect(model().attributeExists("users"))
                .andExpect(content().string(
                        containsString("No users found.")
                ));

        verify(userService).getAllUsers();
    }

    @Test
    @WithMockUser(
            username = "admin@example.com",
            roles = "ADMIN"
    )
    void shouldRenderUsersForAdmin() throws Exception {
        UserListItem administrator = new UserListItem(
                1L,
                "System Administrator",
                "admin@example.com",
                Role.ADMIN,
                true,
                Instant.parse("2026-09-17T07:00:00Z")
        );

        UserListItem technician = new UserListItem(
                2L,
                "Inactive Technician",
                "technician@example.com",
                Role.TECHNICIAN,
                false,
                Instant.parse("2026-09-16T07:00:00Z")
        );

        List<UserListItem> users = List.of(
                administrator,
                technician
        );

        when(userService.getAllUsers()).thenReturn(users);

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/list"))
                .andExpect(model().attribute("users", users))
                .andExpect(content().string(
                        containsString("System Administrator")
                ))
                .andExpect(content().string(
                        containsString("admin@example.com")
                ))
                .andExpect(content().string(
                        containsString("ADMIN")
                ))
                .andExpect(content().string(
                        containsString("Active")
                ))
                .andExpect(content().string(
                        containsString("Inactive Technician")
                ))
                .andExpect(content().string(
                        containsString("Inactive")
                ));

        verify(userService).getAllUsers();
    }

    @Test
    @WithMockUser(
            username = "technician@example.com",
            roles = "TECHNICIAN"
    )
    void shouldDenyTechnicianAccessToUserManagement() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/access-denied"));
    }

    @Test
    @WithMockUser(
            username = "requester@example.com",
            roles = "REQUESTER"
    )
    void shouldDenyRequesterAccessToUserManagement() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/access-denied"));
    }

    @Test
    void shouldRedirectAnonymousUserFromUserManagementToLogin()
            throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}