package com.expertiz.sentinel.core.audit.internal;

import com.expertiz.sentinel.core.shared.audit.AuditActor;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Acteur explicite s'il est fourni, sinon l'utilisateur du JWT (claims {@code sub} et {@code email}), sinon anonyme. */
@Component
class AuditActorResolver {

    AuditActor resolve(AuditActor explicit) {
        if (explicit != null) {
            return explicit;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return AuditActor.user(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"));
        }
        return AuditActor.anonymous();
    }
}
