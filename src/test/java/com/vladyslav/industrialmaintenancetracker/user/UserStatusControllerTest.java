package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.exception.LastActiveAdminException;
import com.vladyslav.industrialmaintenancetracker.exception.UserNotFoundException;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
)
class UserStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void shouldDeactivateUserAndRedirectToList() throws Exception {
        mockMvc.perform(post("/users/2/deactivate")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute(
                        "successMessage",
                        "User deactivated successfully."
                ));

        verify(userService).deactivateUser(2L);
    }

    @Test
    void shouldActivateUserAndRedirectToList() throws Exception {
        mockMvc.perform(post("/users/2/activate")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute(
                        "successMessage",
                        "User activated successfully."
                ));

        verify(userService).activateUser(2L);
    }

    @Test
    void shouldShowErrorWhenDeactivatingLastActiveAdmin()
            throws Exception {
        doThrow(new LastActiveAdminException())
                .when(userService)
                .deactivateUser(1L);

        mockMvc.perform(post("/users/1/deactivate")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute(
                        "errorMessage",
                        "The last active administrator cannot be deactivated"
                ));

        verify(userService).deactivateUser(1L);
    }

    @Test
    void shouldShowErrorForUnknownUser() throws Exception {
        doThrow(new UserNotFoundException(99L))
                .when(userService)
                .activateUser(99L);

        mockMvc.perform(post("/users/99/activate")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute(
                        "errorMessage",
                        "User with id 99 was not found"
                ));

        verify(userService).activateUser(99L);
    }

    @Test
    @WithMockUser(
            username = "technician@example.com",
            roles = "TECHNICIAN"
    )
    void shouldDenyTechnicianStatusChange() throws Exception {
        mockMvc.perform(post("/users/2/deactivate")
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/access-denied"));

        verifyNoInteractions(userService);
    }
}