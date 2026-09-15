package com.vladyslav.industrialmaintenancetracker;

import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=initial.admin@example.com",
        "APP_ADMIN_PASSWORD=StrongPassword#2026"
})
class IndustrialMaintenanceTrackerApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextLoadsAndCreatesInitialAdmin() {
        User admin = userRepository
                .findByEmailIgnoreCase("initial.admin@example.com")
                .orElseThrow();

        assertThat(admin.getFullName())
                .isEqualTo("System Administrator");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();

        assertThat(admin.getPasswordHash())
                .isNotEqualTo("StrongPassword#2026");

        assertThat(passwordEncoder.matches(
                "StrongPassword#2026",
                admin.getPasswordHash()
        )).isTrue();
    }
}