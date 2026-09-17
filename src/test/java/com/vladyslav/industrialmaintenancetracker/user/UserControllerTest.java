package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.exception.DuplicateEmailException;
import com.vladyslav.industrialmaintenancetracker.security.SecurityConfig;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserCreateForm;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void shouldCreateValidUserAndRedirectToList() throws Exception {
        mockMvc.perform(post("/users")
                        .with(csrf())
                        .param("fullName", "Anna Müller")
                        .param("email", "anna@example.com")
                        .param("password", "password123")
                        .param("role", "TECHNICIAN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"));

        verify(userService).createUser(argThat(form ->
                form.getFullName().equals("Anna Müller")
                        && form.getEmail().equals("anna@example.com")
                        && form.getPassword().equals("password123")
                        && form.getRole() == Role.TECHNICIAN
        ));
    }

    @Test
    void shouldRejectInvalidUserForm() throws Exception {
        mockMvc.perform(post("/users")
                        .with(csrf())
                        .param("fullName", "")
                        .param("email", "invalid-email")
                        .param("password", "short")
                        .param("role", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("users/create"))
                .andExpect(model().attributeHasFieldErrors(
                        "userCreateForm",
                        "fullName",
                        "email",
                        "password",
                        "role"
                ))
                .andExpect(model().attributeExists("roles"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldShowErrorForDuplicateEmail() throws Exception {
        doThrow(new DuplicateEmailException("existing@example.com"))
                .when(userService)
                .createUser(any(UserCreateForm.class));

        mockMvc.perform(post("/users")
                        .with(csrf())
                        .param("fullName", "Existing User")
                        .param("email", "existing@example.com")
                        .param("password", "password123")
                        .param("role", "REQUESTER"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/create"))
                .andExpect(model().attributeHasFieldErrors(
                        "userCreateForm",
                        "email"
                ))
                .andExpect(model().attributeExists("roles"))
                .andExpect(content().string(
                        containsString(
                                "A user with this email already exists."
                        )
                ));
    }
}