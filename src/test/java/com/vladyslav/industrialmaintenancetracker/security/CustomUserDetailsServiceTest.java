package com.vladyslav.industrialmaintenancetracker.security;

import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService =
                new CustomUserDetailsService(userRepository);
    }

    @Test
    void shouldLoadUserByNormalizedEmail() {
        User user = new User(
                "System Administrator",
                "admin@example.com",
                "{bcrypt}encoded-password",
                Role.ADMIN
        );

        when(userRepository.findByEmailIgnoreCase(
                "admin@example.com"
        )).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService
                .loadUserByUsername("  ADMIN@EXAMPLE.COM  ");

        assertThat(userDetails)
                .isInstanceOf(CustomUserDetails.class);
        assertThat(userDetails.getUsername())
                .isEqualTo("admin@example.com");
        assertThat(userDetails.getPassword())
                .isEqualTo("{bcrypt}encoded-password");

        verify(userRepository)
                .findByEmailIgnoreCase("admin@example.com");
    }

    @Test
    void shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase(
                "unknown@example.com"
        )).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(
                        "  UNKNOWN@EXAMPLE.COM  "
                )
        );

        assertThat(exception.getName())
                .isEqualTo("unknown@example.com");

        verify(userRepository)
                .findByEmailIgnoreCase("unknown@example.com");
    }
}