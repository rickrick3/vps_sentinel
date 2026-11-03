package com.expertiz.sentinel.core.identity.web;

import com.expertiz.sentinel.core.identity.internal.session.AuthTokens;
import com.expertiz.sentinel.core.identity.internal.session.ChangePasswordOutcome;
import com.expertiz.sentinel.core.identity.internal.session.ChangePasswordService;
import com.expertiz.sentinel.core.identity.internal.session.LoginOutcome;
import com.expertiz.sentinel.core.identity.internal.session.LoginService;
import com.expertiz.sentinel.core.identity.internal.session.RefreshCookies;
import com.expertiz.sentinel.core.identity.internal.session.RefreshOutcome;
import com.expertiz.sentinel.core.identity.internal.session.RefreshTokenService;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.web.RequestInfo;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de session (spec §8.2 à §8.5). Login, refresh et logout sont publics mais gardés par l'{@code Origin}
 * (OriginCheckFilter) ; ils ignorent le Bearer. Les services renvoient un résultat typé, traduit ici en réponse d'erreur
 * après la validation de leur transaction.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String INVALID_CREDENTIALS = "Identifiants invalides.";
    static final String SESSION_EXPIRED = "Session expirée. Reconnectez-vous.";

    private final LoginService login;
    private final RefreshTokenService refreshTokens;
    private final ChangePasswordService changePasswords;
    private final RefreshCookies cookies;

    AuthController(LoginService login, RefreshTokenService refreshTokens, ChangePasswordService changePasswords,
            RefreshCookies cookies) {
        this.login = login;
        this.refreshTokens = refreshTokens;
        this.changePasswords = changePasswords;
        this.cookies = cookies;
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginOutcome outcome = login.login(request.email(), request.password(), RequestInfo.clientIp().orElse(null),
                RequestInfo.userAgent().orElse(null));
        return switch (outcome) {
            case LoginOutcome.Success success -> withSession(success.tokens());
            case LoginOutcome.InvalidCredentials ignored -> throw new ApiException(ProblemType.UNAUTHORIZED, INVALID_CREDENTIALS);
            case LoginOutcome.Throttled throttled -> throw throttled(throttled.retryAfter());
        };
    }

    /** Seul le cookie fait foi. Un refus efface le cookie, devenu inutilisable. */
    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        RefreshOutcome outcome = refreshTokens.rotate(refreshToken, RequestInfo.clientIp().orElse(null),
                RequestInfo.userAgent().orElse(null));
        return switch (outcome) {
            case RefreshOutcome.Rotated rotated -> withSession(rotated.tokens());
            case RefreshOutcome.Rejected ignored -> {
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.SET_COOKIE, cookies.clear().toString());
                throw new ApiException(ProblemType.UNAUTHORIZED, SESSION_EXPIRED, List.of(), headers);
            }
        };
    }

    /** 204 dans tous les cas, cookie absent ou inconnu compris ; fonctionne avec un access token expiré (spec §8.5). */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        refreshTokens.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    /**
     * Authentifié par Bearer, exempté du changement imposé (spec §8.1). Il émet un nouveau cookie mais ignore celui qu'il
     * reçoit (spec §8.9). Un mauvais mot de passe actuel est une erreur de champ, pas un 401 : le client tenterait un
     * refresh (spec §8.5).
     */
    @PostMapping(path = "/change-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        ChangePasswordOutcome outcome = changePasswords.change(UUID.fromString(jwt.getSubject()), request.currentPassword(),
                request.newPassword(), RequestInfo.clientIp().orElse(null), RequestInfo.userAgent().orElse(null));
        return switch (outcome) {
            case ChangePasswordOutcome.Changed changed -> withSession(changed.tokens());
            case ChangePasswordOutcome.WrongCurrentPassword ignored -> throw new ApiException(ProblemType.VALIDATION_ERROR,
                    "Un ou plusieurs champs sont invalides.",
                    List.of(new FieldViolation("currentPassword", "Mot de passe actuel incorrect.")));
            case ChangePasswordOutcome.Throttled throttled -> throw throttled(throttled.retryAfter());
        };
    }

    private ResponseEntity<AuthResponse> withSession(AuthTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(tokens.refresh()).toString())
                .body(AuthResponse.of(tokens));
    }

    /** 429 avec {@code Retry-After} en secondes entières, arrondies au supérieur (spec §8.4). */
    static ApiException throttled(Duration retryAfter) {
        long seconds = Math.max(1, retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0));
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        return new ApiException(ProblemType.LOGIN_THROTTLED, "Trop de tentatives. Réessayez dans " + seconds + " secondes.",
                List.of(), headers);
    }
}
