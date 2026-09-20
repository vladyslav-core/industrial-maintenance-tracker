package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.exception.DuplicateEmailException;
import com.vladyslav.industrialmaintenancetracker.exception.LastActiveAdminException;
import com.vladyslav.industrialmaintenancetracker.exception.UserNotFoundException;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserEditForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
)
class UserEditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void shouldShowEditUserForm() throws Exception {
        UserEditForm form = new UserEditForm(
                "Anna Schmidt",
                "anna.mueller@example.com",
                Role.REQUESTER
        );

        when(userService.getUserForEditing(2L))
                .thenReturn(form);

        mockMvc.perform(get("/users/2/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/edit"))
                .andExpect(model().attribute("userId", 2L))
                .andExpect(model().attribute(
                        "userEditForm",
                        form
                ))
                .andExpect(model().attributeExists("roles"))
                .andExpect(content().string(
                        containsString("Anna Schmidt")
                ))
                .andExpect(content().string(
                        containsString("anna.mueller@example.com")
                ));

        verify(userService).getUserForEditing(2L);
    }

    @Test
    void shouldUpdateValidUserAndRedirectToList()
            throws Exception {
        mockMvc.perform(post("/users/2/edit")
                        .with(csrf())
                        .param("fullName", "Anna Schmidt")
                        .param(
                                "email",
                                "anna.schmidt@example.com"
                        )
                        .param("role", "TECHNICIAN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute(
                        "successMessage",
                        "User updated successfully."
                ));

        verify(userService).updateUser(
                eq(2L),
                argThat(form ->
                        form.getFullName().equals("Anna Schmidt")
                                && form.getEmail().equals(
                                "anna.schmidt@example.com"
                        )
                                && form.getRole()
                                == Role.TECHNICIAN
                )
        );
    }

    @Test
    void shouldRejectInvalidEditForm() throws Exception {
        mockMvc.perform(post("/users/2/edit")
                        .with(csrf())
                        .param("fullName", "")
                        .param("email", "invalid-email")
                        .param("role", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("users/edit"))
                .andExpect(model().attributeHasFieldErrors(
                        "userEditForm",
                        "fullName",
                        "email",
                        "role"
                ))
                .andExpect(model().attribute("userId", 2L))
                .andExpect(model().attributeExists("roles"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldShowErrorForDuplicateEmail() throws Exception {
        doThrow(new DuplicateEmailException(
                "admin@example.com"
        ))
                .when(userService)
                .updateUser(
                        eq(2L),
                        any(UserEditForm.class)
                );

        mockMvc.perform(post("/users/2/edit")
                        .with(csrf())
                        .param("fullName", "Anna Schmidt")
                        .param("email", "admin@example.com")
                        .param("role", "REQUESTER"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/edit"))
                .andExpect(model().attributeHasFieldErrors(
                        "userEditForm",
                        "email"
                ))
                .andExpect(model().attribute("userId", 2L))
                .andExpect(model().attributeExists("roles"))
                .andExpect(content().string(
                        containsString(
                                "A user with this email already exists."
                        )
                ));
    }

    @Test
    void shouldShowErrorWhenRemovingLastAdminRole()
            throws Exception {
        doThrow(new LastActiveAdminException())
                .when(userService)
                .updateUser(
                        eq(1L),
                        any(UserEditForm.class)
                );

        mockMvc.perform(post("/users/1/edit")
                        .with(csrf())
                        .param(
                                "fullName",
                                "System Administrator"
                        )
                        .param("email", "admin@example.com")
                        .param("role", "REQUESTER"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/edit"))
                .andExpect(model().attributeHasFieldErrors(
                        "userEditForm",
                        "role"
                ))
                .andExpect(model().attribute("userId", 1L))
                .andExpect(model().attributeExists("roles"))
                .andExpect(content().string(
                        containsString(
                                "The last active administrator "
                                        + "must keep the ADMIN role."
                        )
                ));
    }

    @Test
    void shouldRedirectWhenEditingUnknownUser()
            throws Exception {
        when(userService.getUserForEditing(99L))
                .thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/users/99/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute(
                        "errorMessage",
                        "User with id 99 was not found"
                ));

        verify(userService).getUserForEditing(99L);
    }

    @Test
    void shouldRedirectWhenUpdatingUnknownUser()
            throws Exception {
        doThrow(new UserNotFoundException(99L))
                .when(userService)
                .updateUser(
                        eq(99L),
                        any(UserEditForm.class)
                );

        mockMvc.perform(post("/users/99/edit")
                        .with(csrf())
                        .param("fullName", "Unknown User")
                        .param("email", "unknown@example.com")
                        .param("role", "REQUESTER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute(
                        "errorMessage",
                        "User with id 99 was not found"
                ));
    }

    @Test
    @WithMockUser(
            username = "technician@example.com",
            roles = "TECHNICIAN"
    )
    void shouldDenyTechnicianAccessToUserEditing()
            throws Exception {
        mockMvc.perform(get("/users/2/edit"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/access-denied"));

        verifyNoInteractions(userService);
    }
}