package com.expertiz.sentinel.core.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Informations sur le client de la requête en cours. L'adresse est celle que la valve RemoteIp de Tomcat a retenue
 * (spec §8.10), mise sous forme canonique : PostgreSQL ({@code inet}) et les empreintes d'IP voient toujours la même
 * écriture.
 */
public final class RequestInfo {

    private static final int USER_AGENT_MAX_LENGTH = 255;

    private RequestInfo() {}

    public static Optional<String> clientIp() {
        return currentRequest().map(HttpServletRequest::getRemoteAddr).flatMap(RequestInfo::canonicalIp);
    }

    public static Optional<String> userAgent() {
        return currentRequest()
                .map(request -> request.getHeader("User-Agent"))
                .map(ua -> ua.length() > USER_AGENT_MAX_LENGTH ? ua.substring(0, USER_AGENT_MAX_LENGTH) : ua);
    }

    private static Optional<String> canonicalIp(String address) {
        int scope = address.indexOf('%');
        String literal = scope >= 0 ? address.substring(0, scope) : address;
        try {
            return Optional.of(InetAddress.ofLiteral(literal).getHostAddress());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<HttpServletRequest> currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? Optional.of(attributes.getRequest())
                : Optional.empty();
    }
}
