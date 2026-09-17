package com.vladyslav.industrialmaintenancetracker.user.dto;

import com.vladyslav.industrialmaintenancetracker.user.Role;

import java.time.Instant;

public class UserListItem {

    private final Long id;
    private final String fullName;
    private final String email;
    private final Role role;
    private final boolean active;
    private final Instant createdAt;

    public UserListItem(
            Long id,
            String fullName,
            String email,
            Role role,
            boolean active,
            Instant createdAt
    ) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}