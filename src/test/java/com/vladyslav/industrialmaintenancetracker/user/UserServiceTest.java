package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.exception.DuplicateEmailException;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserCreateForm;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserListItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldReturnUsersAsListItemsSortedByFullName() {
        User anna = new User(
                "Anna Müller",
                "anna.mueller@example.com",
                "{bcrypt}anna-password",
                Role.REQUESTER
        );

        User administrator = new User(
                "System Administrator",
                "admin@example.com",
                "{bcrypt}admin-password",
                Role.ADMIN
        );

        Sort sort = Sort.by(Sort.Direction.ASC, "fullName");

        when(userRepository.findAll(sort))
                .thenReturn(List.of(anna, administrator));

        List<UserListItem> users = userService.getAllUsers();

        assertThat(users)
                .extracting(UserListItem::getFullName)
                .containsExactly(
                        "Anna Müller",
                        "System Administrator"
                );

        assertThat(users)
                .extracting(UserListItem::getEmail)
                .containsExactly(
                        "anna.mueller@example.com",
                        "admin@example.com"
                );

        assertThat(users)
                .extracting(UserListItem::getRole)
                .containsExactly(Role.REQUESTER, Role.ADMIN);

        assertThat(users)
                .extracting(UserListItem::isActive)
                .containsExactly(true, true);

        verify(userRepository).findAll(sort);
        verifyNoInteractions(passwordEncoder);
    }
}