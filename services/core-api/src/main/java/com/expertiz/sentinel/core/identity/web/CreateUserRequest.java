package com.expertiz.sentinel.core.identity.web;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.text.EmailFormat;
import com.expertiz.sentinel.core.shared.text.NoControlChars;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/users} (spec §10.1, §10.3). L'email est stocké sous sa forme normalisée. */
public record CreateUserRequest(
        @NotBlank @EmailFormat String email,
        @NotBlank @Size(max = 120, message = "120 caractères maximum") @NoControlChars String fullName,
        @NotBlank @Pattern(regexp = RoleCode.PATTERN, message = "rôle inconnu") String role) {}
