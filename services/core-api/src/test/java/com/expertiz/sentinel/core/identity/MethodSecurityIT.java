package com.expertiz.sentinel.core.identity;

import static com.expertiz.sentinel.core.support.TestTokens.signRs256;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.secrets.SentinelSecrets;
import com.expertiz.sentinel.core.shared.security.Permissions;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import com.expertiz.sentinel.core.support.TestTokens;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Autorités issues du claim {@code perms}, sans préfixe (spec §8.1) : pour chaque rôle et chaque permission, un
 * {@code @PreAuthorize("hasAuthority(…)")} laisse passer ou répond 403 {@code forbidden}, selon la matrice du spec §8.12.
 */
class MethodSecurityIT extends IntegrationTest {

    @Autowired
    SentinelSecrets secrets;

    static Stream<Arguments> rolesAndPermissions() {
        return Arrays.stream(RoleCode.values())
                .flatMap(role -> Permissions.ALL.stream().sorted().map(permission -> Arguments.of(role, permission)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rolesAndPermissions")
    void authorityFollowsTheMatrix(RoleCode role, String permission) {
        String token = fixtures.accessToken(fixtures.createUser(role));

        var response = http.get()
                .uri("/test/permission/{p}", permission)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();

        if (PermissionMatrixIT.EXPECTED.get(role).contains(permission)) {
            response.expectStatus().isOk();
        } else {
            response.expectStatus().isForbidden()
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.type").isEqualTo("urn:sentinel:problem:forbidden")
                    .jsonPath("$.detail").isEqualTo("Permission insuffisante.");
        }
    }

    @Test
    void authoritiesComeFromPermsNotFromRole() {
        // Jeton signé avec la vraie clé : rôle SUPER_ADMIN, mais aucune permission.
        TestUser user = fixtures.createUser(RoleCode.SUPER_ADMIN);
        String token = signRs256(secrets.jwtPrivateKey(), TestTokens.validClaims(user, clock.instant(), List.of()).build());

        for (String probe : List.of(Permissions.USER_MANAGE, "SUPER_ADMIN", "ROLE_SUPER_ADMIN")) {
            http.get()
                    .uri("/test/permission/{p}", probe)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Test
    void authoritiesAreNotPrefixed() {
        String token = fixtures.accessToken(fixtures.createUser(RoleCode.VIEWER));

        for (String prefixed : List.of("SCOPE_server:read", "ROLE_server:read")) {
            http.get()
                    .uri("/test/permission/{p}", prefixed)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }
}
