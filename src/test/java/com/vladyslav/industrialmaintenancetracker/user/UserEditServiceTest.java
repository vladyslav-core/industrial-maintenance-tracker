package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.exception.DuplicateEmailException;
import com.vladyslav.industrialmaintenancetracker.exception.LastActiveAdminException;
import com.vladyslav.industrialmaintenancetracker.exception.UserNotFoundException;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserEditForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEditServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                passwordEncoder
        );
    }

    @Test
    void shouldReturnUserDataForEditing() {
        User user = createUser(
                "Anna Müller",
                "anna.mueller@example.com",
                Role.REQUESTER
        );

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        UserEditForm form =
                userService.getUserForEditing(1L);

        assertThat(form.getFullName())
                .isEqualTo("Anna Müller");
        assertThat(form.getEmail())
                .isEqualTo("anna.mueller@example.com");
        assertThat(form.getRole())
                .isEqualTo(Role.REQUESTER);

        verify(userRepository).findById(1L);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldUpdateUserWithNormalizedData() {
        User user = createUser(
                "Anna Müller",
                "anna.mueller@example.com",
                Role.REQUESTER
        );

        UserEditForm form = new UserEditForm(
                "  Anna Schmidt  ",
                "  ANNA.SCHMIDT@EXAMPLE.COM  ",
                Role.TECHNICIAN
        );

        when(userRepository.findById(2L))
                .thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase(
                "anna.schmidt@example.com"
        )).thenReturn(false);

        userService.updateUser(2L, form);

        assertThat(user.getFullName())
                .isEqualTo("Anna Schmidt");
        assertThat(user.getEmail())
                .isEqualTo("anna.schmidt@example.com");
        assertThat(user.getRole())
                .isEqualTo(Role.TECHNICIAN);

        verify(userRepository).findById(2L);
        verify(userRepository).existsByEmailIgnoreCase(
                "anna.schmidt@example.com"
        );
        verify(userRepository, never()).save(user);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldNotCheckDuplicateWhenEmailIsUnchanged() {
        User user = createUser(
                "Anna Müller",
                "anna.mueller@example.com",
                Role.REQUESTER
        );

        UserEditForm form = new UserEditForm(
                "Anna Schmidt",
                "  ANNA.MUELLER@EXAMPLE.COM  ",
                Role.TECHNICIAN
        );

        when(userRepository.findById(3L))
                .thenReturn(Optional.of(user));

        userService.updateUser(3L, form);

        assertThat(user.getFullName())
                .isEqualTo("Anna Schmidt");
        assertThat(user.getEmail())
                .isEqualTo("anna.mueller@example.com");
        assertThat(user.getRole())
                .isEqualTo(Role.TECHNICIAN);

        verify(userRepository, never())
                .existsByEmailIgnoreCase(anyString());
        verify(userRepository, never()).save(user);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectDuplicateEmailWithoutChangingUser() {
        User user = createUser(
                "Anna Müller",
                "anna.mueller@example.com",
                Role.REQUESTER
        );

        UserEditForm form = new UserEditForm(
                "Anna Schmidt",
                "admin@example.com",
                Role.TECHNICIAN
        );

        when(userRepository.findById(4L))
                .thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase(
                "admin@example.com"
        )).thenReturn(true);

        assertThatThrownBy(
                () -> userService.updateUser(4L, form)
        )
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("admin@example.com");

        assertThat(user.getFullName())
                .isEqualTo("Anna Müller");
        assertThat(user.getEmail())
                .isEqualTo("anna.mueller@example.com");
        assertThat(user.getRole())
                .isEqualTo(Role.REQUESTER);

        verify(userRepository, never()).save(user);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectRoleChangeForLastActiveAdmin() {
        User administrator = createUser(
                "System Administrator",
                "admin@example.com",
                Role.ADMIN
        );

        UserEditForm form = new UserEditForm(
                "System Administrator",
                "admin@example.com",
                Role.REQUESTER
        );

        when(userRepository.findById(5L))
                .thenReturn(Optional.of(administrator));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN))
                .thenReturn(1L);

        assertThatThrownBy(
                () -> userService.updateUser(5L, form)
        ).isInstanceOf(LastActiveAdminException.class);

        assertThat(administrator.getRole())
                .isEqualTo(Role.ADMIN);

        verify(userRepository)
                .countByRoleAndActiveTrue(Role.ADMIN);
        verify(userRepository, never()).save(administrator);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldAllowAdminRoleChangeWhenAnotherActiveAdminExists() {
        User administrator = createUser(
                "System Administrator",
                "admin@example.com",
                Role.ADMIN
        );

        UserEditForm form = new UserEditForm(
                "System Administrator",
                "admin@example.com",
                Role.REQUESTER
        );

        when(userRepository.findById(6L))
                .thenReturn(Optional.of(administrator));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN))
                .thenReturn(2L);

        userService.updateUser(6L, form);

        assertThat(administrator.getRole())
                .isEqualTo(Role.REQUESTER);

        verify(userRepository)
                .countByRoleAndActiveTrue(Role.ADMIN);
        verify(userRepository, never()).save(administrator);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectUpdateForUnknownUser() {
        UserEditForm form = new UserEditForm(
                "Unknown User",
                "unknown@example.com",
                Role.REQUESTER
        );

        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> userService.updateUser(99L, form)
        )
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");

        verify(userRepository).findById(99L);
        verify(userRepository, never())
                .existsByEmailIgnoreCase(anyString());
        verify(userRepository, never())
                .countByRoleAndActiveTrue(Role.ADMIN);
        verifyNoInteractions(passwordEncoder);
    }

    private User createUser(
            String fullName,
            String email,
            Role role
    ) {
        return new User(
                fullName,
                email,
                "{bcrypt}password-hash",
                role
        );
    }
}