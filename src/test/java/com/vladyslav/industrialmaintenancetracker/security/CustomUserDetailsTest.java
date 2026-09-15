package com.vladyslav.industrialmaintenancetracker.security;

import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsTest {

    @Test
    void shouldAdaptUserForSpringSecurity() {
        User user = mock(User.class);

        when(user.getId()).thenReturn(42L);
        when(user.getFullName()).thenReturn("System Administrator");
        when(user.getEmail()).thenReturn("admin@example.com");
        when(user.getPasswordHash()).thenReturn("{bcrypt}encoded-password");
        when(user.getRole()).thenReturn(Role.ADMIN);
        when(user.isActive()).thenReturn(true);

        CustomUserDetails userDetails = new CustomUserDetails(user);

        assertThat(userDetails.getUserId()).isEqualTo(42L);
        assertThat(userDetails.getFullName())
                .isEqualTo("System Administrator");
        assertThat(userDetails.getUsername())
                .isEqualTo("admin@example.com");
        assertThat(userDetails.getPassword())
                .isEqualTo("{bcrypt}encoded-password");
        assertThat(userDetails.getRole()).isEqualTo(Role.ADMIN);
        assertThat(userDetails.isEnabled()).isTrue();

        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");

        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void shouldDisableInactiveUser() {
        User user = mock(User.class);

        when(user.isActive()).thenReturn(false);

        CustomUserDetails userDetails = new CustomUserDetails(user);

        assertThat(userDetails.isEnabled()).isFalse();
    }
}