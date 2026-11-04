package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.text.HttpUrl;
import com.expertiz.sentinel.core.shared.text.NoControlChars;
import com.expertiz.sentinel.core.shared.text.TextRules;
import com.expertiz.sentinel.core.shared.web.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Corps de {@code POST} et {@code PUT} d'un projet (spec §10.3) ; en {@code PUT}, un champ facultatif absent est vidé. */
public record ProjectRequest(
        @NotBlank @Size(max = 100, message = "100 caractères maximum") @NoControlChars String name,
        @Size(max = 2000, message = "2000 caractères maximum") @NoControlChars(allowNewlines = true) String description,
        @Size(max = 500, message = "500 caractères maximum") @HttpUrl String repositoryUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = ValidationGroups.VERSION_DESCRIPTION)
        @Null(groups = ValidationGroups.Create.class, message = "ne doit pas être fourni à la création")
        @NotNull(groups = ValidationGroups.Update.class)
        @PositiveOrZero
        Long version) {

    public ProjectRequest {
        name = TextRules.clean(name);
        description = TextRules.clean(description);
        repositoryUrl = TextRules.clean(repositoryUrl);
    }
}
