package com.expertiz.sentinel.core.support;

import com.jayway.jsonpath.JsonPath;
import java.net.HttpCookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Appels aux endpoints de session comme un navigateur les émet : {@code Origin} autorisée, IP du client portée par
 * {@code X-Forwarded-For} (le test joue le gateway), identifiant de corrélation propre à chaque appel.
 */
public final class AuthClient {

    public static final String COOKIE = "sentinel_rt";

    private final RestTestClient http;

    AuthClient(RestTestClient http) {
        this.http = http;
    }

    public static String newRequestId() {
        return "t-" + UUID.randomUUID();
    }

    public Response login(String email, String password, String ip) {
        return login(email, password, ip, newRequestId());
    }

    public Response login(String email, String password, String ip, String requestId) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return exchange(http.post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ApplicationTestBase.ALLOWED_ORIGIN)
                .header("X-Forwarded-For", ip)
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectBody(String.class)
                .returnResult());
    }

    public Response refresh(String cookie, String ip) {
        return refresh(cookie, ip, newRequestId());
    }

    public Response refresh(String cookie, String ip, String requestId) {
        var request = http.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.ORIGIN, ApplicationTestBase.ALLOWED_ORIGIN)
                .header("X-Forwarded-For", ip)
                .header("X-Request-Id", requestId);
        if (cookie != null) {
            request.cookie(COOKIE, cookie);
        }
        return exchange(request.exchange().expectBody(String.class).returnResult());
    }

    public Response logout(String cookie, String ip) {
        return logout(cookie, ip, newRequestId());
    }

    public Response logout(String cookie, String ip, String requestId) {
        var request = http.post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.ORIGIN, ApplicationTestBase.ALLOWED_ORIGIN)
                .header("X-Forwarded-For", ip)
                .header("X-Request-Id", requestId);
        if (cookie != null) {
            request.cookie(COOKIE, cookie);
        }
        return exchange(request.exchange().expectBody(String.class).returnResult());
    }

    public Response changePassword(String accessToken, String current, String next, String ip) {
        return changePassword(accessToken, current, next, ip, newRequestId());
    }

    public Response changePassword(String accessToken, String current, String next, String ip, String requestId) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("currentPassword", current);
        body.put("newPassword", next);
        return exchange(http.post()
                .uri("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Forwarded-For", ip)
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectBody(String.class)
                .returnResult());
    }

    private static Response exchange(EntityExchangeResult<String> result) {
        return new Response(result.getStatus().value(), result.getResponseHeaders(), result.getResponseBody());
    }

    public record Response(int status, HttpHeaders headers, String body) {

        /** En-tête {@code Set-Cookie} du cookie de session, ou {@code null}. */
        public String setCookie() {
            List<String> cookies = headers.getOrEmpty(HttpHeaders.SET_COOKIE);
            return cookies.stream().filter(c -> c.startsWith(COOKIE + "=")).findFirst().orElse(null);
        }

        /** Valeur du cookie de session émis, ou {@code null}. */
        public String refreshCookie() {
            String header = setCookie();
            return header == null ? null : HttpCookie.parse(header).getFirst().getValue();
        }

        public String accessToken() {
            return json("$.accessToken");
        }

        public <T> T json(String path) {
            return JsonPath.read(body, path);
        }
    }
}
