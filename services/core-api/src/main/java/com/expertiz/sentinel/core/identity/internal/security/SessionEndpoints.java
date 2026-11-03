package com.expertiz.sentinel.core.identity.internal.security;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Endpoints de session (spec §8.9) : {@code POST /api/v1/auth/login}, {@code refresh} et {@code logout}, chemins exacts.
 * Une seule définition pour les trois règles qui les concernent : accès sans jeton, {@code Origin} obligatoire, Bearer
 * ignoré (seul le cookie de session y fait foi).
 */
final class SessionEndpoints {

    static final RequestMatcher MATCHER = new OrRequestMatcher(
            pathPattern(HttpMethod.POST, "/api/v1/auth/login"),
            pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
            pathPattern(HttpMethod.POST, "/api/v1/auth/logout"));

    private SessionEndpoints() {}
}
