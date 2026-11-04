package com.expertiz.sentinel.core.identity.internal.security;

import com.expertiz.sentinel.core.shared.problem.ProblemWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security (spec §8.11) : sans état, refus par défaut, authentification par Bearer uniquement.
 *
 * <ul>
 *   <li>Accès sans jeton : les endpoints de session ({@link SessionEndpoints}), {@code GET /actuator/health} et le
 *       contrat OpenAPI (actif en dev et en test seulement). Tout le reste exige un jeton valide, et chaque contrôleur
 *       déclare son {@code @PreAuthorize}.
 *   <li>Aucune session HTTP, aucun CSRF par jeton ni CORS (spec §8.9), aucun formulaire de login, Basic ou logout de
 *       Spring Security : pas de route implicite.
 *   <li>Jeton lu dans l'en-tête {@code Authorization} seulement, jamais dans l'URL ni le corps.
 *   <li>Ordre : contrôle d'origine, Bearer, changement de mot de passe imposé, autorisation.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SentinelSecurityProperties.class)
class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemWriter problems, SentinelSecurityProperties properties) {
        var entryPoint = new ProblemAuthenticationEntryPoint(problems);
        var accessDenied = new ProblemAccessDeniedHandler(problems);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        // Rendu d'une erreur déjà survenue : la requête d'origine a été contrôlée. Une requête directe
                        // sur /error reste soumise à la règle générale.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(SessionEndpoints.MATCHER).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .addFilterBefore(new OriginCheckFilter(properties.allowedOrigins(), problems), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new PasswordChangeRequiredFilter(problems), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** Les endpoints de session ignorent le Bearer : un access token expiré ne doit pas bloquer un refresh ou un logout. */
    private static BearerTokenResolver bearerTokenResolver() {
        var header = new DefaultBearerTokenResolver();
        return request -> SessionEndpoints.MATCHER.matches(request) ? null : header.resolve(request);
    }

    /** Autorités = claim {@code perms}, sans préfixe : {@code hasAuthority('server:write')}. */
    private static JwtAuthenticationConverter authenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtClaims.PERMS);
        authorities.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
