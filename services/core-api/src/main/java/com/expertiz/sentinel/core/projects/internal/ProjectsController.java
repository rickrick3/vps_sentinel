package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.security.Permissions;
import com.expertiz.sentinel.core.shared.web.PageResponse;
import com.expertiz.sentinel.core.shared.web.ValidationGroups;
import jakarta.validation.groups.Default;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Projets (spec §10.1) : lecture {@code project:read}, écriture {@code project:write}, suppression {@code project:delete}. */
@RestController
@RequestMapping("/api/v1/projects")
class ProjectsController {

    static final String READ = "hasAuthority('" + Permissions.PROJECT_READ + "')";
    static final String WRITE = "hasAuthority('" + Permissions.PROJECT_WRITE + "')";
    static final String DELETE = "hasAuthority('" + Permissions.PROJECT_DELETE + "')";

    private final ProjectService projects;

    ProjectsController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    @PreAuthorize(READ)
    PageResponse<ProjectResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return projects.list(q, page, size, sort);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    ProjectDetailResponse get(@PathVariable UUID id) {
        return projects.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(WRITE)
    ResponseEntity<ProjectDetailResponse> create(@Validated({Default.class, ValidationGroups.Create.class}) @RequestBody ProjectRequest request) {
        ProjectDetailResponse created = projects.create(request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(WRITE)
    ProjectDetailResponse replace(@PathVariable UUID id,
            @Validated({Default.class, ValidationGroups.Update.class}) @RequestBody ProjectRequest request) {
        return projects.replace(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(DELETE)
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        projects.delete(id);
        return ResponseEntity.noContent().build();
    }
}
