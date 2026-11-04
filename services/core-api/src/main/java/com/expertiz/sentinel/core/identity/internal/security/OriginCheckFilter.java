package com.expertiz.sentinel.core.identity.internal.security;

import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.problem.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Endpoints de session (spec §8.9) : exactement un en-tête {@code Origin}, égal à une origine autorisée, sinon 403
 * {@code origin-not-allowed}. Contrôlé avant tout le reste, type de contenu compris. Les autres requêtes ne sont pas
 * concernées : elles ne s'authentifient que par Bearer, qu'un site tiers ne peut pas forger.
 */
class OriginCheckFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OriginCheckFilter.class);

    private final Set<String> allowed;
    private final ProblemWriter problems;

    /** Origines au format {@code scheme://hôte[:port]} ; une entrée invalide empêche le démarrage. */
    OriginCheckFilter(List<String> allowedOrigins, ProblemWriter problems) {
        this.allowed = allowedOrigins.stream()
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .map(OriginCheckFilter::canonical)
                .collect(Collectors.toUnmodifiableSet());
        this.problems = problems;
        if (allowed.isEmpty()) {
            log.warn("SENTINEL_ALLOWED_ORIGINS est vide : login, refresh et logout répondront 403 à toute requête.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !SessionEndpoints.MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        List<String> origins = Collections.list(request.getHeaders(HttpHeaders.ORIGIN));
        if (origins.size() != 1 || !allowed.contains(origins.getFirst())) {
            problems.write(request, response, ProblemType.ORIGIN_NOT_ALLOWED, "Origine non autorisée.");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Forme sérialisée par les navigateurs : schéma et hôte en minuscules, sans port par défaut ni chemin. */
    private static String canonical(String origin) {
        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException e) {
            throw invalid(origin);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean http = scheme.equals("http");
        if (!(http || scheme.equals("https"))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || !uri.getRawPath().isEmpty()
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw invalid(origin);
        }
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (http && port == 80) || (!http && port == 443);
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
    }

    private static IllegalStateException invalid(String origin) {
        return new IllegalStateException("SENTINEL_ALLOWED_ORIGINS : origine invalide « " + origin
                + " » (attendu : http(s)://hôte[:port], sans chemin ni joker)");
    }
}
