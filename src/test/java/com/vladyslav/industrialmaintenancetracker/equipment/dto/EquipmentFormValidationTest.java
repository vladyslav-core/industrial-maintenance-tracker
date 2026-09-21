package com.vladyslav.industrialmaintenancetracker.equipment.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentFormValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAcceptValidCreateForm() {
        EquipmentCreateForm form = new EquipmentCreateForm();
        form.setName("Hydraulic press");
        form.setInventoryNumber("PRESS-001");
        form.setDescription("Main production press");
        form.setLocation("Workshop A");

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void shouldRejectInvalidCreateForm() {
        EquipmentCreateForm form = new EquipmentCreateForm();
        form.setName("A");
        form.setInventoryNumber("1");
        form.setDescription("x".repeat(1001));
        form.setLocation("");

        assertThat(invalidFields(form)).containsExactlyInAnyOrder(
                "name",
                "inventoryNumber",
                "description",
                "location"
        );
    }

    @Test
    void shouldAcceptValidEditForm() {
        EquipmentEditForm form = new EquipmentEditForm(
                "CNC lathe",
                "LATHE-002",
                null,
                "Workshop B"
        );

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void shouldRejectInvalidEditForm() {
        EquipmentEditForm form = new EquipmentEditForm(
                "A",
                "1",
                "x".repeat(1001),
                ""
        );

        assertThat(invalidFields(form)).containsExactlyInAnyOrder(
                "name",
                "inventoryNumber",
                "description",
                "location"
        );
    }

    private <T> Set<String> invalidFields(T form) {
        return validator.validate(form).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}