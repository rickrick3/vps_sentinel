package com.expertiz.sentinel.core.projects.internal;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Déploiements (spec §10.1) : rattachement et retrait avec {@code project:write}. */
@RestController
class DeploymentsController {

    private final DeploymentService deployments;

    DeploymentsController(DeploymentService deployments) {
        this.deployments = deployments;
    }

    /** 409 {@code server-not-deployable} si le VPS est supprimé ou décommissionné, {@code deployment-exists} si déjà lié. */
    @PostMapping(path = "/api/v1/environments/{environmentId}/deployments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ProjectsController.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<DeploymentResponse> attach(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID environmentId,
            @Valid @RequestBody DeploymentRequest request) {
        DeploymentResponse attached = deployments.attach(environmentId, request, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/v1/deployments/" + attached.id())).body(attached);
    }

    @DeleteMapping("/api/v1/deployments/{id}")
    @PreAuthorize(ProjectsController.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void detach(@PathVariable UUID id) {
        deployments.detach(id);
    }
}
