package com.expertiz.sentinel.core.identity.internal.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de sécurité ({@code sentinel.security.*}, spec §6.6).
 *
 * @param allowedOrigins origines autorisées sur les endpoints de session ({@code SENTINEL_ALLOWED_ORIGINS})
 * @param accessTokenTtl durée de validité des access tokens (15 min, spec §8.1)
 */
@ConfigurationProperties("sentinel.security")
record SentinelSecurityProperties(List<String> allowedOrigins, Duration accessTokenTtl) {

    SentinelSecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
