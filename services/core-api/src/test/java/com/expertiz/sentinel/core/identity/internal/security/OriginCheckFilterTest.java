package com.expertiz.sentinel.core.identity.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.shared.problem.ProblemWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** Contrôle de l'{@code Origin} sur les endpoints de session (spec §8.9), et validation de la liste configurée. */
class OriginCheckFilterTest {

    private final ProblemWriter problems = new ProblemWriter(JsonMapper.builder().build());

    private final OriginCheckFilter filter =
            new OriginCheckFilter(List.of("http://localhost:5173", "HTTPS://Sentinel.Example.COM:443"), problems);

    private static MockHttpServletRequest request(String method, String path, String... origins) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        for (String origin : origins) {
            request.addHeader("Origin", origin);
        }
        return request;
    }

    /** Vrai si la requête a franchi le filtre. */
    private boolean passes(OriginCheckFilter target, MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        var chain = new MockFilterChain();
        target.doFilter(request, response, chain);
        return chain.getRequest() != null;
    }

    private boolean passes(MockHttpServletRequest request) throws Exception {
        return passes(filter, request, new MockHttpServletResponse());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
    void listedOriginPasses(String path) throws Exception {
        assertThat(passes(request("POST", path, "http://localhost:5173"))).isTrue();
    }

    @Test
    void configuredOriginsAreNormalizedLikeBrowsersSendThem() throws Exception {
        assertThat(passes(request("POST", "/api/v1/auth/login", "https://sentinel.example.com"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
    void missingOriginIs403(String path) throws Exception {
        var response = new MockHttpServletResponse();

        assertThat(passes(filter, request("POST", path), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("urn:sentinel:problem:origin-not-allowed")
                .contains("Origine non autorisée.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://evil.example",
        "null",
        "",
        "http://localhost:5174",
        "https://localhost:5173",
        "http://localhost:5173/",
        "http://LOCALHOST:5173",
        "http://localhost:5173.evil.example",
        "http://evil.example/http://localhost:5173",
        "https://sentinel.example.com:8443"
    })
    void foreignOriginIs403(String origin) throws Exception {
        var response = new MockHttpServletResponse();

        assertThat(passes(filter, request("POST", "/api/v1/auth/login", origin), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void severalOriginHeadersAre403() throws Exception {
        assertThat(passes(request("POST", "/api/v1/auth/login", "http://localhost:5173", "https://evil.example"))).isFalse();
        assertThat(passes(request("POST", "/api/v1/auth/login", "http://localhost:5173", "http://localhost:5173"))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "GET,    /api/v1/auth/login",
        "PUT,    /api/v1/auth/logout",
        "POST,   /api/v1/auth/change-password",
        "POST,   /api/v1/auth/login/x",
        "POST,   /api/v1/servers",
        "GET,    /api/v1/me"
    })
    void otherRequestsAreNotChecked(String method, String path) throws Exception {
        assertThat(passes(request(method, path))).isTrue();
        assertThat(passes(request(method, path, "https://evil.example"))).isTrue();
    }

    @Test
    void emptyListRejectsEverySessionRequest() throws Exception {
        var closed = new OriginCheckFilter(List.of(), problems);

        assertThat(passes(closed, request("POST", "/api/v1/auth/login", "http://localhost:5173"), new MockHttpServletResponse()))
                .isFalse();
    }

    @Test
    void blankEntriesAreIgnored() throws Exception {
        var configured = new OriginCheckFilter(List.of(" ", "http://localhost:5173 "), problems);

        assertThat(passes(configured, request("POST", "/api/v1/auth/login", "http://localhost:5173"), new MockHttpServletResponse()))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "*",
        "null",
        "localhost:5173",
        "sentinel.example.com",
        "ftp://sentinel.example.com",
        "https://sentinel.example.com/",
        "https://sentinel.example.com/app",
        "https://sentinel.example.com?x=1",
        "https://sentinel.example.com#x",
        "https://user@sentinel.example.com",
        "https://",
        "https://*.example.com"
    })
    void invalidConfigurationPreventsStartup(String origin) {
        assertThatThrownBy(() -> new OriginCheckFilter(List.of(origin), problems))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SENTINEL_ALLOWED_ORIGINS");
    }
}
