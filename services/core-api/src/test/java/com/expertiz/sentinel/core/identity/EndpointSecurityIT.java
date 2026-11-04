package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

/**
 * Refus par défaut (spec §8.11) : sans jeton, toute route répond 401, sauf la liste publique ci-dessous, et chaque
 * contrôleur de l'application déclare un {@code @PreAuthorize}. Les endpoints de session (login, refresh, logout) sont
 * publics mais gardés par l'origine : voir OriginCheckIT.
 */
class EndpointSecurityIT extends IntegrationTest {

    /** Liste exhaustive des routes enregistrées accessibles sans jeton. */
    static final Set<String> PUBLIC = Set.of("GET /actuator/health", "GET /v3/api-docs", "GET /v3/api-docs.yaml");

    /** Rendu des erreurs pour les dispatchs d'erreur du conteneur ; en requête directe, il exige un jeton. */
    static final String ERROR_PATH = "/error";

    @Autowired
    ApplicationContext context;

    record Route(String method, String pattern, HandlerMethod handler) {

        String key() {
            return method + " " + pattern;
        }
    }

    private List<Route> routes(Stream<RequestMappingInfoHandlerMapping> mappings) {
        List<Route> routes = new ArrayList<>();
        mappings.forEach(mapping -> mapping.getHandlerMethods().forEach((info, handler) -> {
            for (String method : methods(info)) {
                for (String pattern : info.getPatternValues()) {
                    routes.add(new Route(method, pattern, handler));
                }
            }
        }));
        return routes;
    }

    private static List<String> methods(RequestMappingInfo info) {
        var methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty() ? List.of("GET") : methods.stream().map(Enum::name).toList();
    }

    private List<Route> allRoutes() {
        return routes(context.getBeansOfType(RequestMappingInfoHandlerMapping.class).values().stream());
    }

    @Test
    void everyApplicationHandlerDeclaresPreAuthorize() {
        RequestMappingInfoHandlerMapping application =
                context.getBean("requestMappingHandlerMapping", RequestMappingInfoHandlerMapping.class);

        List<String> unprotected = routes(Stream.of(application)).stream()
                .filter(route -> !PUBLIC.contains(route.key()) && !route.pattern().equals(ERROR_PATH))
                .filter(route -> !AnnotatedElementUtils.hasAnnotation(route.handler().getMethod(), PreAuthorize.class)
                        && !AnnotatedElementUtils.hasAnnotation(route.handler().getBeanType(), PreAuthorize.class))
                .map(Route::key)
                .toList();

        assertThat(unprotected).as("handlers sans @PreAuthorize ni entrée publique").isEmpty();
    }

    @Test
    void onlyThePublicRoutesAnswerWithoutAToken() {
        Map<String, Integer> statuses = new LinkedHashMap<>();
        for (Route route : allRoutes()) {
            statuses.put(route.key(), callWithoutToken(route.method(), concrete(route.pattern())).getStatus().value());
        }

        assertThat(statuses).as("routes enregistrées").isNotEmpty().containsKeys(PUBLIC.toArray(String[]::new));
        assertThat(statuses.entrySet().stream().filter(e -> e.getValue() != 401).map(Map.Entry::getKey))
                .as("routes accessibles sans jeton")
                .containsExactlyInAnyOrderElementsOf(PUBLIC);
    }

    @Test
    void protectedRoutesAnswerWithAProblem() {
        for (Route route : allRoutes()) {
            if (PUBLIC.contains(route.key())) {
                continue;
            }
            EntityExchangeResult<byte[]> result = callWithoutToken(route.method(), concrete(route.pattern()));
            assertThat(result.getResponseHeaders().getContentType())
                    .as(route.key())
                    .matches(type -> type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            assertThat(new String(result.getResponseBody(), StandardCharsets.UTF_8))
                    .as(route.key())
                    .contains("urn:sentinel:problem:unauthorized");
        }
    }

    @Test
    void publicRoutesAnswer() {
        http.get().uri("/actuator/health").exchange().expectStatus().isOk();
        http.get().uri("/v3/api-docs").exchange().expectStatus().isOk();
    }

    @ParameterizedTest
    @CsvSource({
        "GET,    /",
        "GET,    /index.html",
        "GET,    /api/v1/unknown",
        "POST,   /api/v1/servers",
        "DELETE, /api/v1/users/42",
        "GET,    /actuator",
        "GET,    /actuator/env",
        "GET,    /actuator/beans",
        "GET,    /actuator/health/liveness",
        "HEAD,   /actuator/health",
        "POST,   /actuator/health",
        "POST,   /v3/api-docs",
        // Points d'entrée par défaut de Spring Security, désactivés : aucun filtre ne doit y répondre.
        "GET,    /login",
        "POST,   /login",
        "GET,    /logout",
        "POST,   /logout"
    })
    void unregisteredRoutesRequireAToken(String method, String path) {
        assertThat(callWithoutToken(method, path).getStatus().value()).isEqualTo(401);
    }

    @Test
    void noUserDetailsServiceIsConfigured() {
        // Pas d'utilisateur en mémoire généré par Spring Boot : seule l'authentification par Bearer existe.
        assertThat(context.getBeanProvider(UserDetailsService.class).getIfAvailable()).isNull();
    }

    private EntityExchangeResult<byte[]> callWithoutToken(String method, String path) {
        var request = http.method(HttpMethod.valueOf(method)).uri(path).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(method)) {
            request.contentType(MediaType.APPLICATION_JSON).body("{}");
        }
        return request.exchange().expectBody().returnResult();
    }

    /** Chemin concret pour un motif : variables remplacées par un UUID, jokers par un segment. */
    private static String concrete(String pattern) {
        return pattern.replaceAll("\\{\\*[^}]+}", "x")
                .replaceAll("\\{[^}]+}", UUID.randomUUID().toString())
                .replace("**", "x")
                .replace("*", "x");
    }
}
