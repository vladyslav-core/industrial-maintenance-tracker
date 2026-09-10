package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.user.dto.UserCreateForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.vladyslav.industrialmaintenancetracker.exception.DuplicateEmailException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void shouldCreateUserWithNormalizedDataAndEncodedPassword() {
        UserCreateForm form = new UserCreateForm();
        form.setFullName("  Anna Müller  ");
        form.setEmail("  ANNA.MUELLER@EXAMPLE.COM  ");
        form.setPassword("StrongPassword#2026");
        form.setRole(Role.REQUESTER);

        when(userRepository.existsByEmailIgnoreCase(
                "anna.mueller@example.com"
        )).thenReturn(false);

        when(passwordEncoder.encode("StrongPassword#2026"))
                .thenReturn("{bcrypt}encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = userService.createUser(form);

        assertThat(createdUser.getFullName()).isEqualTo("Anna Müller");
        assertThat(createdUser.getEmail())
                .isEqualTo("anna.mueller@example.com");
        assertThat(createdUser.getPasswordHash())
                .isEqualTo("{bcrypt}encoded-password");
        assertThat(createdUser.getRole()).isEqualTo(Role.REQUESTER);
        assertThat(createdUser.isActive()).isTrue();

        verify(userRepository)
                .existsByEmailIgnoreCase("anna.mueller@example.com");
        verify(passwordEncoder).encode("StrongPassword#2026");
        verify(userRepository).save(createdUser);
    }

    @Test
    void shouldRejectDuplicateEmailWithoutEncodingOrSaving() {
        UserCreateForm form = new UserCreateForm();
        form.setFullName("Anna Müller");
        form.setEmail("  ANNA.MUELLER@EXAMPLE.COM  ");
        form.setPassword("StrongPassword#2026");
        form.setRole(Role.REQUESTER);

        when(userRepository.existsByEmailIgnoreCase(
                "anna.mueller@example.com"
        )).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(form))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("anna.mueller@example.com");

        verify(userRepository)
                .existsByEmailIgnoreCase("anna.mueller@example.com");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}