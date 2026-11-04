package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.support.IntegrationTest;
import com.expertiz.sentinel.core.support.TestEndpoints;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Refus par défaut (spec §8.11, §13.4) : chaque route enregistrée par l'application a exactement une entrée dans la
 * matrice d'AuthorizationMatrixIT, et chaque entrée est une route. Une route ajoutée sans que son accès ait été décidé
 * et testé fait donc échouer la construction.
 *
 * <p>Hors matrice : le rendu des erreurs ({@code /error}), le contrat OpenAPI ({@code /v3/api-docs**}, public, voir
 * EndpointSecurityIT) et les endpoints propres aux tests ({@link TestEndpoints}).
 */
class DenyByDefaultIT extends IntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void everyMappingHasAMatrixEntry() {
        List<String> registered = new ArrayList<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            if (handler.getBeanType() == TestEndpoints.class) {
                return;
            }
            // Une route sans méthode déclarée accepte toutes les méthodes : « ANY » n'a pas d'entrée et fait échouer le test.
            var methods = info.getMethodsCondition().getMethods();
            List<String> names = methods.isEmpty() ? List.of("ANY") : methods.stream().map(RequestMethod::name).toList();
            for (String pattern : info.getPatternValues()) {
                if (pattern.equals("/error") || pattern.startsWith("/v3/api-docs")) {
                    continue;
                }
                names.forEach(method -> registered.add(method + " " + pattern));
            }
        });

        assertThat(registered).containsExactlyInAnyOrderElementsOf(AuthorizationMatrixIT.CASES_AS_KEYS);
    }
}
