package com.expertiz.sentinel.core.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrat OpenAPI (spec §10, §13.6), servi par springdoc en dev et en test, et exporté dans
 * {@code contracts/openapi/core-api.yaml}, d'où le frontend génère ses types. Le fichier commité est comparé octet par
 * octet : rien n'y dépend du port d'écoute (serveur relatif) ni de l'ordre de découverte des contrôleurs (identifiants
 * d'opération dérivés des noms, clés triées).
 *
 * <ul>
 *   <li>Chaque opération exige un Bearer JWT (schéma {@code bearer}), sauf celles qui déclarent un
 *       {@code @SecurityRequirements()} vide : les endpoints de session.
 *   <li>Toute erreur est un Problem Details (spec §11) : réponse {@code default} de chaque opération.
 *   <li>springdoc ne voit pas le statut d'un {@code ResponseEntity} : les statuts autres que 200 sont déclarés par
 *       {@code @ResponseStatus}.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearer";
    private static final String PROBLEM = "Problem";

    @Bean
    OpenAPI sentinelOpenApi() {
        return new OpenAPI()
                .info(new Info().title("VPS Sentinel Core API").version("1"))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** {@code <ressource><Méthode>}, par exemple {@code serversCreate} : stable, et unique tant qu'un contrôleur ne surcharge pas ses méthodes. */
    @Bean
    OperationCustomizer operationIdsFromNames() {
        return (operation, handler) -> {
            String resource = handler.getBeanType().getSimpleName().replaceFirst("Controller$", "");
            String method = handler.getMethod().getName();
            return operation.operationId(Character.toLowerCase(resource.charAt(0)) + resource.substring(1)
                    + Character.toUpperCase(method.charAt(0)) + method.substring(1));
        };
    }

    /**
     * Le schéma est ajouté ici, avec ses références : springdoc retire les schémas non référencés avant d'appeler les
     * customizers, il effacerait un schéma déclaré dans {@link #sentinelOpenApi()}.
     */
    @Bean
    OpenApiCustomizer problemResponses() {
        ApiResponse problem = new ApiResponse()
                .description("Erreur au format Problem Details (RFC 9457)")
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))));
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM, problemSchema());
            openApi.getPaths().values().forEach(path -> path.readOperations()
                    .forEach(operation -> operation.getResponses().addApiResponse("default", problem)));
        };
    }

    /** Champs produits par {@code Problems.create} ; {@code errors} n'est présent que pour les erreurs de champ. */
    private static ObjectSchema problemSchema() {
        ObjectSchema violation = new ObjectSchema();
        violation.addProperty("field", new StringSchema());
        violation.addProperty("message", new StringSchema());
        violation.setRequired(List.of("field", "message"));
        ObjectSchema problem = new ObjectSchema();
        problem.addProperty("type", new StringSchema().description("urn:sentinel:problem:<code> (spec §11)"));
        problem.addProperty("title", new StringSchema());
        problem.addProperty("status", new IntegerSchema());
        problem.addProperty("detail", new StringSchema());
        problem.addProperty("instance", new StringSchema());
        problem.addProperty("correlationId", new StringSchema());
        problem.addProperty("errors", new ArraySchema().items(violation));
        problem.setRequired(List.of("type", "title", "status"));
        return problem;
    }
}
