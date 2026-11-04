package com.expertiz.sentinel.core.identity.internal.session;

import com.expertiz.sentinel.core.identity.internal.security.SentinelSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie de session {@code sentinel_rt} (spec §8.2) : {@code HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth}. Il
 * n'est envoyé qu'aux endpoints de session ; sa durée ne dépasse jamais la fin absolue de la session.
 */
@Component
public class RefreshCookies {

    public static final String NAME = "sentinel_rt";
    public static final String PATH = "/api/v1/auth";

    private final SentinelSecurityProperties properties;
    private final Clock clock;

    RefreshCookies(SentinelSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** {@code Max-Age} = min(24 h, fin absolue de la session − maintenant), en secondes entières. */
    public ResponseCookie issue(NewRefreshToken token) {
        Duration untilSessionEnd = Duration.between(clock.instant(), token.sessionStartedAt().plus(properties.sessionMaxAge()));
        Duration maxAge = untilSessionEnd.compareTo(properties.refreshIdleTtl()) < 0 ? untilSessionEnd : properties.refreshIdleTtl();
        return cookie(token.rawValue(), Duration.ofSeconds(Math.max(0, maxAge.toSeconds())));
    }

    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
