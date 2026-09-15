package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.user.dto.UserCreateForm;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class InitialAdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory()
                .getValidator();
    }

    @Test
    void shouldCreateInitialAdminWhenNoActiveAdminExists() {
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN))
                .thenReturn(0L);

        InitialAdminBootstrap bootstrap = new InitialAdminBootstrap(
                userRepository,
                userService,
                validator,
                "admin@example.com",
                "StrongPassword#2026"
        );

        bootstrap.run(null);

        ArgumentCaptor<UserCreateForm> formCaptor =
                ArgumentCaptor.forClass(UserCreateForm.class);

        verify(userService).createUser(formCaptor.capture());

        UserCreateForm capturedForm = formCaptor.getValue();

        assertThat(capturedForm.getFullName())
                .isEqualTo("System Administrator");
        assertThat(capturedForm.getEmail())
                .isEqualTo("admin@example.com");
        assertThat(capturedForm.getPassword())
                .isEqualTo("StrongPassword#2026");
        assertThat(capturedForm.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldNotCreateAdminWhenActiveAdminAlreadyExists() {
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN))
                .thenReturn(1L);

        InitialAdminBootstrap bootstrap = new InitialAdminBootstrap(
                userRepository,
                userService,
                validator,
                "",
                ""
        );

        bootstrap.run(null);

        verify(userRepository).countByRoleAndActiveTrue(Role.ADMIN);
        verifyNoInteractions(userService);
    }

    @Test
    void shouldFailWhenInitialAdminConfigurationIsInvalid() {
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN))
                .thenReturn(0L);

        InitialAdminBootstrap bootstrap = new InitialAdminBootstrap(
                userRepository,
                userService,
                validator,
                "",
                "short"
        );

        assertThatThrownBy(() -> bootstrap.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Initial administrator configuration is invalid. "
                                + "Invalid fields: email, password"
                );

        verifyNoInteractions(userService);
    }
}