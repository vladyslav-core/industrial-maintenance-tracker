package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.user.dto.UserCreateForm;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class InitialAdminBootstrap implements ApplicationRunner {

    private static final String ADMIN_FULL_NAME = "System Administrator";

    private final UserRepository userRepository;
    private final UserService userService;
    private final Validator validator;
    private final String adminUsername;
    private final String adminPassword;

    public InitialAdminBootstrap(
            UserRepository userRepository,
            UserService userService,
            Validator validator,
            @Value("${APP_ADMIN_USERNAME:}") String adminUsername,
            @Value("${APP_ADMIN_PASSWORD:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.validator = validator;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.countByRoleAndActiveTrue(Role.ADMIN) > 0) {
            return;
        }

        UserCreateForm form = new UserCreateForm();
        form.setFullName(ADMIN_FULL_NAME);
        form.setEmail(adminUsername);
        form.setPassword(adminPassword);
        form.setRole(Role.ADMIN);

        Set<ConstraintViolation<UserCreateForm>> violations =
                validator.validate(form);

        if (!violations.isEmpty()) {
            String invalidFields = violations.stream()
                    .map(violation ->
                            violation.getPropertyPath().toString())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));

            throw new IllegalStateException(
                    "Initial administrator configuration is invalid. "
                            + "Invalid fields: "
                            + invalidFields
            );
        }

        userService.createUser(form);
    }
}