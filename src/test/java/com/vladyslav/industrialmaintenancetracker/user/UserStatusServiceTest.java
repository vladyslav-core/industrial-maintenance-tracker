package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.exception.LastActiveAdminException;
import com.vladyslav.industrialmaintenancetracker.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStatusServiceTest {

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
    void shouldActivateInactiveUser() {
        User user = createUser(Role.TECHNICIAN);
        user.deactivate();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        userService.activateUser(1L);

        assertThat(user.isActive()).isTrue();

        verify(userRepository).findById(1L);
        verify(userRepository, never()).save(user);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldDeactivateActiveNonAdminUser() {
        User user = createUser(Role.REQUESTER);

        when(userRepository.findById(2L))
                .thenReturn(Optional.of(user));

        userService.deactivateUser(2L);

        assertThat(user.isActive()).isFalse();

        verify(userRepository).findById(2L);
        verify(userRepository, never())
                .countByRoleAndActiveTrue(Role.ADMIN);
        verify(userRepository, never()).save(user);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldDeactivateAdminWhenAnotherActiveAdminExists() {
        User administrator = createUser(Role.ADMIN);

        when(userRepository.findById(3L))
                .thenReturn(Optional.of(administrator));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN))
                .thenReturn(2L);

        userService.deactivateUser(3L);

        assertThat(administrator.isActive()).isFalse();

        verify(userRepository)
                .countByRoleAndActiveTrue(Role.ADMIN);
        verify(userRepository, never()).save(administrator);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectDeactivationOfLastActiveAdmin() {
        User administrator = createUser(Role.ADMIN);

        when(userRepository.findById(4L))
                .thenReturn(Optional.of(administrator));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN))
                .thenReturn(1L);

        assertThatThrownBy(
                () -> userService.deactivateUser(4L)
        )
                .isInstanceOf(LastActiveAdminException.class)
                .hasMessageContaining(
                        "last active administrator"
                );

        assertThat(administrator.isActive()).isTrue();

        verify(userRepository)
                .countByRoleAndActiveTrue(Role.ADMIN);
        verify(userRepository, never()).save(administrator);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldDoNothingWhenUserIsAlreadyInactive() {
        User user = createUser(Role.ADMIN);
        user.deactivate();

        when(userRepository.findById(5L))
                .thenReturn(Optional.of(user));

        userService.deactivateUser(5L);

        assertThat(user.isActive()).isFalse();

        verify(userRepository, never())
                .countByRoleAndActiveTrue(Role.ADMIN);
        verify(userRepository, never()).save(user);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectStatusChangeForUnknownUser() {
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> userService.deactivateUser(99L)
        )
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");

        verify(userRepository).findById(99L);
        verify(userRepository, never())
                .countByRoleAndActiveTrue(Role.ADMIN);
        verifyNoInteractions(passwordEncoder);
    }

    private User createUser(Role role) {
        return new User(
                "Test User",
                "test@example.com",
                "{bcrypt}password-hash",
                role
        );
    }
}