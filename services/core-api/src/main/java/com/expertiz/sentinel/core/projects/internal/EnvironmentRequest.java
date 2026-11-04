package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.text.TextRules;
import com.expertiz.sentinel.core.shared.web.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Corps de {@code POST} et {@code PUT} d'un environnement (spec §10.3). */
public record EnvironmentRequest(
        @NotBlank
        @Size(max = 50, message = "50 caractères maximum")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
                message = "lettres, chiffres, « . », « _ » ou « - », en commençant par une lettre ou un chiffre")
        String name,
        @NotNull @Pattern(regexp = EnvironmentKind.PATTERN, message = "nature inconnue") String kind,
        @Null(groups = ValidationGroups.Create.class, message = "ne doit pas être fourni à la création")
        @NotNull(groups = ValidationGroups.Update.class)
        @PositiveOrZero
        Long version) {

    public EnvironmentRequest {
        name = TextRules.clean(name);
        kind = TextRules.clean(kind);
    }
}
