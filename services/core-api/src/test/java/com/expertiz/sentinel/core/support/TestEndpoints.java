package com.expertiz.sentinel.core.support;

import com.expertiz.sentinel.core.shared.web.RequestInfo;
import java.util.Map;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de test, protégés comme ceux de l'application : ils servent à éprouver la chaîne de sécurité. */
@TestComponent
@RestController
@RequestMapping("/test")
public class TestEndpoints {

    @GetMapping("/secured")
    @PreAuthorize("isAuthenticated()")
    Map<String, String> secured() {
        return Map.of("status", "ok");
    }

    /** Autorisé si le jeton porte exactement cette autorité. */
    @GetMapping("/permission/{permission}")
    @PreAuthorize("hasAuthority(#permission)")
    Map<String, String> permission(@PathVariable String permission) {
        return Map.of("permission", permission);
    }

    /** IP du client retenue par la valve RemoteIp (spec §8.10). */
    @GetMapping("/ip")
    @PreAuthorize("isAuthenticated()")
    Map<String, String> ip() {
        return Map.of("ip", RequestInfo.clientIp().orElse(""));
    }
}
