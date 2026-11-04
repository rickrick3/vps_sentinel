package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.text.Hostname;
import com.expertiz.sentinel.core.shared.text.TextRules;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/environments/{id}/domains} (spec §10.1, §10.3) : règles du hostname d'un VPS, avec au moins un
 * point, sans point final ni joker ; les noms internationalisés en punycode seulement.
 */
public record DomainRequest(@NotBlank @Hostname(requireDot = true) String hostname, Boolean isPrimary) {

    public DomainRequest {
        hostname = TextRules.clean(hostname);
    }
}
