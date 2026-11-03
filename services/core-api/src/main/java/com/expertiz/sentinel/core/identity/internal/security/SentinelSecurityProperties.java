package com.expertiz.sentinel.core.identity.internal.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de sécurité ({@code sentinel.security.*}, spec §6.6).
 *
 * @param allowedOrigins origines autorisées sur les endpoints de session ({@code SENTINEL_ALLOWED_ORIGINS})
 * @param refreshCookieSecure attribut {@code Secure} du cookie de session ({@code SENTINEL_REFRESH_COOKIE_SECURE})
 * @param accessTokenTtl durée de validité des access tokens (15 min, spec §8.1)
 * @param refreshIdleTtl durée de validité d'un refresh token, renouvelée à chaque rotation (24 h, spec §8.2)
 * @param sessionMaxAge durée absolue d'une session depuis le login (7 jours, spec §8.2)
 */
@ConfigurationProperties("sentinel.security")
public record SentinelSecurityProperties(List<String> allowedOrigins, boolean refreshCookieSecure, Duration accessTokenTtl,
        Duration refreshIdleTtl, Duration sessionMaxAge) {

    public SentinelSecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
