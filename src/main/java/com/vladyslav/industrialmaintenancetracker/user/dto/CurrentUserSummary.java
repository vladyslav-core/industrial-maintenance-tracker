package com.vladyslav.industrialmaintenancetracker.user.dto;

import com.vladyslav.industrialmaintenancetracker.user.Role;

public record CurrentUserSummary(Long id, String fullName, Role role) {
}
