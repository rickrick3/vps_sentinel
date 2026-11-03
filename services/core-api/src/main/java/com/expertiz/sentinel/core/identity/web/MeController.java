package com.expertiz.sentinel.core.identity.web;

import com.expertiz.sentinel.core.identity.internal.model.PermissionMatrix;
import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.model.UserRepository;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class MeController {

    private final UserRepository users;
    private final PermissionMatrix matrix;

    MeController(UserRepository users, PermissionMatrix matrix) {
        this.users = users;
        this.matrix = matrix;
    }

    /**
     * Profil lu en base, comme dans la réponse au login : il reflète l'état actuel du compte. Un compte supprimé ou
     * désactivé depuis l'émission du jeton reçoit 401, ce qui renvoie le client vers le login.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        User user = users.findById(UUID.fromString(jwt.getSubject()))
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ProblemType.UNAUTHORIZED, "Authentification requise."));
        return MeResponse.of(user, matrix.permissionsOf(user.getRole().getCode()));
    }
}
