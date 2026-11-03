package com.expertiz.sentinel.core.identity.internal.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.security.SentinelSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/** Cookie {@code sentinel_rt} (spec §8.2) : attributs fixes, {@code Max-Age} = min(24 h, temps restant avant 7 jours). */
class RefreshCookiesTest {

    static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private static RefreshCookies cookies(boolean secure) {
        var properties = new SentinelSecurityProperties(List.of(), secure, Duration.ofMinutes(15), Duration.ofHours(24),
                Duration.ofDays(7));
        return new RefreshCookies(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static NewRefreshToken token(Instant sessionStartedAt) {
        return new NewRefreshToken("raw-value", UUID.randomUUID(), UUID.randomUUID(), NOW.plus(Duration.ofHours(24)),
                sessionStartedAt);
    }

    @Test
    void newSessionCookie() {
        ResponseCookie cookie = cookies(true).issue(token(NOW));

        assertThat(cookie.getName()).isEqualTo("sentinel_rt");
        assertThat(cookie.getValue()).isEqualTo("raw-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void maxAgeStopsAtTheAbsoluteLimit() {
        assertThat(cookies(true).issue(token(NOW.minus(Duration.ofDays(6)).minus(Duration.ofHours(1)))).getMaxAge())
                .isEqualTo(Duration.ofHours(23));
        assertThat(cookies(true).issue(token(NOW.minus(Duration.ofDays(7)).plusSeconds(10))).getMaxAge())
                .isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void clearedCookieExpiresImmediately() {
        ResponseCookie cookie = cookies(true).clear();

        assertThat(cookie.getName()).isEqualTo("sentinel_rt");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }

    @Test
    void secureFlagFollowsConfiguration() {
        assertThat(cookies(false).issue(token(NOW)).isSecure()).isFalse();
        assertThat(cookies(false).clear().isSecure()).isFalse();
    }
}
