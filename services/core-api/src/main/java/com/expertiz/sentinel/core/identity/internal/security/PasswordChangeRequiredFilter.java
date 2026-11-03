package com.expertiz.sentinel.core.identity.internal.security;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.problem.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Changement de mot de passe imposé (spec §8.1) : un jeton {@code pcr = true} ne donne accès qu'à trois requêtes,
 * méthode et chemin exacts. Toute autre requête répond 403 {@code password-change-required}, avant le contrôle des
 * permissions et même pour une route inconnue.
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/change-password} : sortir de cet état ;
 *   <li>{@code GET /api/v1/me} : le client découvre qu'il doit changer de mot de passe ;
 *   <li>{@code POST /api/v1/auth/logout} : renoncer. Cet endpoint ignore le Bearer, il ne passe donc jamais ici avec
 *       un jeton ; l'exemption est gardée pour que la règle reste vraie si cela changeait.
 * </ul>
 *
 * La restriction suit le claim du jeton : un jeton {@code pcr} reste limité jusqu'à l'émission d'un nouveau jeton.
 */
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    static final RequestMatcher EXEMPT = new OrRequestMatcher(
            pathPattern(HttpMethod.POST, "/api/v1/auth/change-password"),
            pathPattern(HttpMethod.POST, "/api/v1/auth/logout"),
            pathPattern(HttpMethod.GET, "/api/v1/me"));

    private final ProblemWriter problems;

    PasswordChangeRequiredFilter(ProblemWriter problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                && Boolean.TRUE.equals(token.getToken().getClaimAsBoolean(JwtClaims.PCR))
                && !EXEMPT.matches(request)) {
            problems.write(request, response, ProblemType.PASSWORD_CHANGE_REQUIRED, "Changez votre mot de passe pour continuer.");
            return;
        }
        chain.doFilter(request, response);
    }
}
