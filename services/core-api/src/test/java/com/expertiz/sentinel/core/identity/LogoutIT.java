package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.AuthClient;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** {@code POST /api/v1/auth/logout} (spec §8.5) : révoque la famille du cookie et l'efface ; 204 dans tous les cas. */
class LogoutIT extends IntegrationTest {

    TestUser user;
    String ip;
    Response login;

    @BeforeEach
    void session() {
        user = fixtures.createUser(RoleCode.VIEWER);
        ip = Fixtures.uniqueIp();
        login = auth.login(user.email(), user.password(), ip);
    }

    private long logoutAudits() {
        return ownerJdbc().sql("SELECT count(*) FROM audit_logs WHERE target_id = :id AND action = 'LOGOUT'")
                .param("id", user.id())
                .query(Long.class)
                .single();
    }

    private long activeTokens() {
        return ownerJdbc().sql("SELECT count(*) FROM refresh_tokens WHERE user_id = :id AND revoked_at IS NULL")
                .param("id", user.id())
                .query(Long.class)
                .single();
    }

    private static void assertClearsCookie(Response response) {
        assertThat(response.status()).isEqualTo(204);
        assertThat(response.setCookie()).startsWith("sentinel_rt=;").contains("Max-Age=0", "Path=/api/v1/auth", "HttpOnly");
    }

    @Test
    void revokesFamilyAndClearsCookie() throws Exception {
        String current = auth.refresh(login.refreshCookie(), ip).refreshCookie();
        String correlationId = AuthClient.newRequestId();

        assertClearsCookie(auth.logout(current, ip, correlationId));

        assertThat(activeTokens()).isZero();
        assertThat(ownerJdbc().sql("SELECT revoked_reason FROM refresh_tokens WHERE token_hash = :hash")
                        .param("hash", RefreshIT.sha256(current))
                        .query(String.class)
                        .single())
                .isEqualTo("LOGOUT");
        assertThat(auth.refresh(current, ip).status()).isEqualTo(401);
        Map<String, Object> audit = ownerJdbc()
                .sql("SELECT actor_type, actor_user_id, target_id FROM audit_logs WHERE action = 'LOGOUT' AND correlation_id = :c")
                .param("c", correlationId)
                .query()
                .singleRow();
        assertThat(audit).containsEntry("actor_type", "USER").containsEntry("actor_user_id", user.id()).containsEntry("target_id", user.id());
    }

    @Test
    void noCookieIs204() {
        assertClearsCookie(auth.logout(null, ip));

        assertThat(activeTokens()).isEqualTo(1);
        assertThat(logoutAudits()).isZero();
    }

    @Test
    void unknownCookieIs204() {
        assertClearsCookie(auth.logout("dGhpcy1pcy1ub3QtYS1yZWFsLXJlZnJlc2gtdG9rZW4", ip));

        assertThat(activeTokens()).isEqualTo(1);
    }

    @Test
    void secondLogoutIsHarmlessAndNotAudited() {
        auth.logout(login.refreshCookie(), ip);

        assertClearsCookie(auth.logout(login.refreshCookie(), ip));

        assertThat(logoutAudits()).isEqualTo(1);
    }

    @Test
    void worksWithAnExpiredAccessToken() {
        clock.advance(Duration.ofMinutes(20));

        assertClearsCookie(logoutWithBearer(login.accessToken(), login.refreshCookie()));
        assertThat(activeTokens()).isZero();
    }

    @Test
    void originRequiredAndSessionKept() {
        http.post()
                .uri("/api/v1/auth/logout")
                .cookie(AuthClient.COOKIE, login.refreshCookie())
                .exchange()
                .expectStatus().isForbidden();

        assertThat(activeTokens()).isEqualTo(1);
    }

    private Response logoutWithBearer(String accessToken, String cookie) {
        var result = http.post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .cookie(AuthClient.COOKIE, cookie)
                .exchange()
                .expectBody(String.class)
                .returnResult();
        return new Response(result.getStatus().value(), result.getResponseHeaders(), result.getResponseBody());
    }
}
