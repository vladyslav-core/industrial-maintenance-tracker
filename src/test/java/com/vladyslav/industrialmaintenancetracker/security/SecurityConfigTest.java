package com.vladyslav.industrialmaintenancetracker.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final PasswordEncoder passwordEncoder =
            new SecurityConfig().passwordEncoder();

    @Test
    void shouldEncodeAndVerifyPassword() {
        String rawPassword = "StrongPassword#2026";

        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertThat(encodedPassword).isNotEqualTo(rawPassword);
        assertThat(encodedPassword).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", encodedPassword)).isFalse();
    }
}