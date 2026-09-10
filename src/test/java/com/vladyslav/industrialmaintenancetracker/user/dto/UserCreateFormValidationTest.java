package com.vladyslav.industrialmaintenancetracker.user.dto;

import com.vladyslav.industrialmaintenancetracker.user.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UserCreateFormValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAcceptValidForm() {
        UserCreateForm form = new UserCreateForm();
        form.setFullName("Anna Müller");
        form.setEmail("anna.mueller@example.com");
        form.setPassword("StrongPassword#2026");
        form.setRole(Role.REQUESTER);

        Set<ConstraintViolation<UserCreateForm>> violations =
                validator.validate(form);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectInvalidForm() {
        UserCreateForm form = new UserCreateForm();
        form.setFullName("");
        form.setEmail("invalid-email");
        form.setPassword("short");
        form.setRole(null);

        Set<String> invalidFields = validator.validate(form).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(invalidFields).containsExactlyInAnyOrder(
                "fullName",
                "email",
                "password",
                "role"
        );
    }
}