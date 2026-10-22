package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestRepository;
import com.vladyslav.industrialmaintenancetracker.exception.DuplicateEmailException;
import com.vladyslav.industrialmaintenancetracker.exception.LastActiveAdminException;
import com.vladyslav.industrialmaintenancetracker.exception.UserNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.TechnicianHasOpenRequestsException;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserEditForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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

    @Mock
    private RepairRequestRepository requestRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                passwordEncoder,
                requestRepository
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

        when(userRepository.findByIdForUpdate(2L))
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

        verify(userRepository).findByIdForUpdate(2L);
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

        when(userRepository.findByIdForUpdate(3L))
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

        when(userRepository.findByIdForUpdate(4L))
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

        when(userRepository.findByIdForUpdate(5L))
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

        when(userRepository.findByIdForUpdate(6L))
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

        when(userRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> userService.updateUser(99L, form)
        )
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");

        verify(userRepository).findByIdForUpdate(99L);
        verify(userRepository, never())
                .existsByEmailIgnoreCase(anyString());
        verify(userRepository, never())
                .countByRoleAndActiveTrue(Role.ADMIN);
        verifyNoInteractions(passwordEncoder);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "REQUESTER"})
    void shouldRejectTechnicianRoleChangeWithoutPartlyUpdatingProfile(Role targetRole) {
        User technician = createUser("Technician", "tech@example.com", Role.TECHNICIAN);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(technician));
        when(requestRepository.existsByAssignedTechnician_IdAndStatusIn(eq(7L), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser(7L,
                new UserEditForm("Changed name", "changed@example.com", targetRole)))
                .isInstanceOf(TechnicianHasOpenRequestsException.class);
        assertThat(technician.getRole()).isEqualTo(Role.TECHNICIAN);
        assertThat(technician.getFullName()).isEqualTo("Technician");
        assertThat(technician.getEmail()).isEqualTo("tech@example.com");
        var order = inOrder(userRepository, requestRepository);
        order.verify(userRepository).findByIdForUpdate(7L);
        order.verify(requestRepository).existsByAssignedTechnician_IdAndStatusIn(eq(7L), anyCollection());
    }

    @Test
    void shouldAllowTechnicianProfileEditWithoutCheckingOpenAssignmentsWhenRoleIsUnchanged() {
        User technician = createUser("Technician", "tech@example.com", Role.TECHNICIAN);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(technician));
        userService.updateUser(7L, new UserEditForm("Updated name", "updated@example.com", Role.TECHNICIAN));
        assertThat(technician.getFullName()).isEqualTo("Updated name");
        assertThat(technician.getEmail()).isEqualTo("updated@example.com");
        assertThat(technician.getRole()).isEqualTo(Role.TECHNICIAN);
        verifyNoInteractions(requestRepository);
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
