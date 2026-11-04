package com.expertiz.sentinel.core.projects.internal;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Domaines (spec §10.1) : ajout et retrait avec {@code project:write}. */
@RestController
class DomainsController {

    private final DomainService domains;

    DomainsController(DomainService domains) {
        this.domains = domains;
    }

    /** Si {@code isPrimary}, l'ancien domaine principal perd ce statut dans la même transaction. */
    @PostMapping(path = "/api/v1/environments/{environmentId}/domains", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ProjectsController.WRITE)
    ResponseEntity<DomainResponse> add(@PathVariable UUID environmentId, @Valid @RequestBody DomainRequest request) {
        DomainResponse added = domains.add(environmentId, request);
        return ResponseEntity.created(URI.create("/api/v1/domains/" + added.id())).body(added);
    }

    @DeleteMapping("/api/v1/domains/{id}")
    @PreAuthorize(ProjectsController.WRITE)
    ResponseEntity<Void> remove(@PathVariable UUID id) {
        domains.remove(id);
        return ResponseEntity.noContent().build();
    }
}
