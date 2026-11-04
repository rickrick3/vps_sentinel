package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/**
 * Changement de mot de passe imposé (spec §8.1) : avec {@code pcr = true}, seuls {@code POST /auth/change-password},
 * {@code POST /auth/logout} et {@code GET /me} restent accessibles. Toute autre requête, même voisine, répond 403.
 */
class PasswordChangeRequiredIT extends IntegrationTest {

    TestUser user;
    String pcrToken;

    @BeforeEach
    void pcrUser() {
        user = fixtures.createUser(RoleCode.VIEWER);
        pcrToken = fixtures.accessTokenWithPcr(user);
    }

    @Test
    void pcrAllowsMe() {
        http.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(pcrToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mustChangePassword").isEqualTo(true);
    }

    @Test
    void pcrBlocksOtherEndpoints() {
        http.get()
                .uri("/test/secured")
                .header(HttpHeaders.AUTHORIZATION, bearer(pcrToken))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:password-change-required")
                .jsonPath("$.detail").isEqualTo("Changez votre mot de passe pour continuer.")
                .jsonPath("$.instance").isEqualTo("/test/secured");
    }

    @Test
    void pcrComesBeforeThePermissionCheck() {
        // VIEWER n'a pas user:manage : le client doit apprendre qu'il faut changer de mot de passe, pas qu'il manque un droit.
        http.get()
                .uri("/test/permission/user:manage")
                .header(HttpHeaders.AUTHORIZATION, bearer(pcrToken))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:password-change-required");
    }

    @Test
    void pcrBlocksUnknownPaths() {
        http.get()
                .uri("/api/v1/servers")
                .header(HttpHeaders.AUTHORIZATION, bearer(pcrToken))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:password-change-required");
    }

    /** Voisins des trois exemptions : autre méthode, autre casse, segment en plus ou en moins, barre finale. */
    @ParameterizedTest
    @CsvSource({
        "POST,   /api/v1/me",
        "PUT,    /api/v1/me",
        "DELETE, /api/v1/me",
        "PATCH,  /api/v1/me",
        "GET,    /api/v1/me/",
        "GET,    /api/v1/me/permissions",
        "GET,    /api/v1/mex",
        "GET,    /api/v1/ME",
        "GET,    /api/v2/me",
        "GET,    /me",
        "GET,    /api/v1/auth/change-password",
        "PUT,    /api/v1/auth/change-password",
        "POST,   /api/v1/auth/change-password/",
        "POST,   /api/v1/auth/change-password/x",
        "POST,   /api/v1/auth/change-passwordx",
        "POST,   /api/v1/auth/Change-Password",
        "GET,    /api/v1/auth/logout",
        "POST,   /api/v1/auth/logout/x",
        "POST,   /api/v1/auth/login/x",
        "GET,    /api/v1/auth/refresh"
    })
    void nearMissesOfTheExemptionsAreBlocked(String method, String path) {
        http.method(HttpMethod.valueOf(method))
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(pcrToken))
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:password-change-required");
    }

    @Test
    void headOnMeIsBlocked() {
        http.head()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(pcrToken))
                .exchange()
                .expectStatus().isForbidden();
    }

    /** Les endpoints exemptés ne sont pas encore implémentés (M4) : la requête doit seulement franchir le filtre. */
    @ParameterizedTest
    @CsvSource({"/api/v1/auth/change-password", "/api/v1/auth/logout"})
    void exemptEndpointsPassTheFilter(String path) {
        int status = http.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(pcrToken))
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .returnResult()
                .getStatus()
                .value();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void withoutPcrNothingIsBlocked() {
        http.get()
                .uri("/test/secured")
                .header(HttpHeaders.AUTHORIZATION, bearer(fixtures.accessToken(fixtures.createUser(RoleCode.VIEWER))))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void theTokenClaimGovernsUntilANewTokenIsIssued() {
        // Le mot de passe a été changé ailleurs : le jeton pcr reste limité jusqu'à ce qu'un nouveau jeton soit émis.
        appJdbc().sql("UPDATE users SET must_change_password = false WHERE id = :id").param("id", user.id()).update();

        http.get()
                .uri("/test/secured")
                .header(HttpHeaders.AUTHORIZATION, bearer(pcrToken))
                .exchange()
                .expectStatus().isForbidden();
        http.get()
                .uri("/test/secured")
                .header(HttpHeaders.AUTHORIZATION, bearer(fixtures.accessToken(user)))
                .exchange()
                .expectStatus().isOk();
    }
}
