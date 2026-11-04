package com.expertiz.sentinel.core.identity.web;

import com.expertiz.sentinel.core.identity.internal.model.User;
import java.time.Instant;
import java.util.UUID;

/** Utilisateur vu par l'administration : jamais le hash ni le mot de passe temporaire. */
public record UserResponse(UUID id, String email, String fullName, String role, String status, boolean mustChangePassword,
        Instant passwordExpiresAt, Instant lastLoginAt, Instant createdAt, Instant updatedAt, long version) {

    static UserResponse of(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().getCode().name(),
                user.getStatus().name(), user.isMustChangePassword(), user.getPasswordExpiresAt(), user.getLastLoginAt(),
                user.getCreatedAt(), user.getUpdatedAt(), user.getVersion());
    }
}
