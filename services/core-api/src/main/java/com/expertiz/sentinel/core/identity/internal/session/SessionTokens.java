package com.expertiz.sentinel.core.identity.internal.session;

import com.expertiz.sentinel.core.identity.internal.model.PermissionMatrix;
import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.security.AccessTokenIssuer;
import com.expertiz.sentinel.core.identity.web.MeResponse;
import org.springframework.stereotype.Component;

/** Login, refresh et changement de mot de passe répondent de la même façon : access token et profil lus en base. */
@Component
class SessionTokens {

    private final AccessTokenIssuer issuer;
    private final PermissionMatrix matrix;

    SessionTokens(AccessTokenIssuer issuer, PermissionMatrix matrix) {
        this.issuer = issuer;
        this.matrix = matrix;
    }

    AuthTokens open(User user, NewRefreshToken refresh) {
        return new AuthTokens(issuer.issue(user), refresh, MeResponse.of(user, matrix.permissionsOf(user.getRole().getCode())));
    }
}
