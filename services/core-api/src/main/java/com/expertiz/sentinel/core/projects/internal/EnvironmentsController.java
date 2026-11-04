package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.web.ValidationGroups;
import jakarta.validation.groups.Default;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Environnements (spec §10.1) : écriture {@code project:write}, suppression {@code project:delete}. */
@RestController
class EnvironmentsController {

    private final EnvironmentService environments;

    EnvironmentsController(EnvironmentService environments) {
        this.environments = environments;
    }

    @PostMapping(path = "/api/v1/projects/{projectId}/environments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ProjectsController.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<EnvironmentResponse> create(@PathVariable UUID projectId,
            @Validated({Default.class, ValidationGroups.Create.class}) @RequestBody EnvironmentRequest request) {
        EnvironmentResponse created = environments.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId)).body(created);
    }

    @PutMapping(path = "/api/v1/environments/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ProjectsController.WRITE)
    EnvironmentResponse replace(@PathVariable UUID id,
            @Validated({Default.class, ValidationGroups.Update.class}) @RequestBody EnvironmentRequest request) {
        return environments.replace(id, request);
    }

    /** Supprime aussi ses domaines et ses déploiements (spec §9.6). */
    @DeleteMapping("/api/v1/environments/{id}")
    @PreAuthorize(ProjectsController.DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        environments.delete(id);
    }
}
