package com.expertiz.sentinel.core.identity.internal.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Évalue le {@code @PreAuthorize} du contrôleur avant la lecture de la requête. Sans cela, Spring MVC lit et valide le
 * corps, les paramètres et les variables de chemin avant d'appeler la méthode protégée : un appelant sans la permission
 * recevrait 400 sur une requête invalide, et découvrirait ainsi les règles de validation d'un endpoint qui lui est
 * interdit. Ici, il reçoit 403 {@code forbidden}, quelle que soit sa requête.
 *
 * <p>La sécurité des méthodes reste en place et réévalue l'expression à l'appel. Une expression qui lit un argument
 * ({@code #nom}) ne peut être évaluée qu'une fois la requête lue : elle est laissée à ce seul contrôle. Aucun contrôleur
 * de l'application n'en utilise ; AuthorizationMatrixIT exige 403 avant 400 sur chaque endpoint.
 */
final class AuthorizeBeforeBindingInterceptor implements HandlerInterceptor {

    private final PreAuthorizeAuthorizationManager preAuthorize = new PreAuthorizeAuthorizationManager();

    AuthorizeBeforeBindingInterceptor(ApplicationContext context) {
        preAuthorize.setApplicationContext(context);
    }

    /** Refus : {@code AuthorizationDeniedException}, rendue en 403 par le gestionnaire d'erreurs, comme pour la méthode. */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && !readsArguments(method)) {
            preAuthorize.verify(SecurityContextHolder.getContext()::getAuthentication,
                    new SimpleMethodInvocation(method.getBean(), method.getMethod()));
        }
        return true;
    }

    private static boolean readsArguments(HandlerMethod method) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), PreAuthorize.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), PreAuthorize.class);
        }
        return annotation != null && annotation.value().contains("#");
    }
}
