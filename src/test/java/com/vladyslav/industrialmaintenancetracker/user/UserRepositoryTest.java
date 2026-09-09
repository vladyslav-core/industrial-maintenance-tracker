package com.vladyslav.industrialmaintenancetracker.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindUserByEmailIgnoringCase() {
        User user = new User(
                "Anna Müller",
                "anna.mueller@example.com",
                "encoded-password",
                Role.REQUESTER
        );

        userRepository.saveAndFlush(user);

        Optional<User> foundUser =
                userRepository.findByEmailIgnoreCase("ANNA.MUELLER@EXAMPLE.COM");

        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId()).isNotNull();
        assertThat(foundUser.get().getFullName()).isEqualTo("Anna Müller");
        assertThat(foundUser.get().getRole()).isEqualTo(Role.REQUESTER);
        assertThat(foundUser.get().isActive()).isTrue();
        assertThat(foundUser.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldCheckEmailExistenceAndCountActiveAdmins() {
        User admin = new User(
                "Laura Wagner",
                "laura.wagner@example.com",
                "encoded-password",
                Role.ADMIN
        );

        userRepository.saveAndFlush(admin);

        assertThat(
                userRepository.existsByEmailIgnoreCase("LAURA.WAGNER@EXAMPLE.COM")
        ).isTrue();

        assertThat(
                userRepository.existsByEmailIgnoreCase("unknown@example.com")
        ).isFalse();

        assertThat(
                userRepository.countByRoleAndActiveTrue(Role.ADMIN)
        ).isEqualTo(1);

        assertThat(
                userRepository.countByRoleAndActiveTrue(Role.TECHNICIAN)
        ).isZero();
    }
}