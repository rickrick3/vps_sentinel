package com.expertiz.sentinel.core.identity.web;

import com.expertiz.sentinel.core.identity.internal.model.User;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Profil du compte connecté et permissions de son rôle (spec §8.4, §10.1), triées par code. */
public record MeResponse(UUID id, String email, String fullName, String role, List<String> permissions,
        boolean mustChangePassword) {

    public static MeResponse of(User user, Set<String> permissions) {
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().getCode().name(),
                permissions.stream().sorted().toList(), user.isMustChangePassword());
    }
}
