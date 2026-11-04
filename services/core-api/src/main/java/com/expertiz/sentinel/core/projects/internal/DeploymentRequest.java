package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.text.TextRules;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code POST /api/v1/environments/{id}/deployments} (spec §10.1, §10.3) ; le rôle est facultatif. */
public record DeploymentRequest(
        @NotNull UUID serverId,
        @Size(max = 50, message = "50 caractères maximum")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 ._-]*$",
                message = "lettres, chiffres, espaces, « . », « _ » ou « - », en commençant par une lettre ou un chiffre")
        String role) {

    public DeploymentRequest {
        role = TextRules.clean(role);
    }
}
