package com.expertiz.sentinel.core.infrastructure.internal;

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

/** VPS (spec §10.1) : lecture {@code server:read}, écriture {@code server:write}, suppression {@code server:delete}. */
@RestController
@RequestMapping("/api/v1/servers")
class ServersController {

    private static final String READ = "hasAuthority('" + Permissions.SERVER_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permissions.SERVER_WRITE + "')";
    private static final String DELETE = "hasAuthority('" + Permissions.SERVER_DELETE + "')";

    private final ServerService servers;

    ServersController(ServerService servers) {
        this.servers = servers;
    }

    @GetMapping
    @PreAuthorize(READ)
    PageResponse<ServerResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String adminStatus,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return servers.list(q, tag, provider, region, adminStatus, page, size, sort);
    }

    @GetMapping("/facets")
    @PreAuthorize(READ)
    ServerFacetsResponse facets() {
        return servers.facets();
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    ServerResponse get(@PathVariable UUID id) {
        return servers.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(WRITE)
    ResponseEntity<ServerResponse> create(@Validated({Default.class, ValidationGroups.Create.class}) @RequestBody ServerRequest request) {
        ServerResponse created = servers.create(request);
        return ResponseEntity.created(URI.create("/api/v1/servers/" + created.id())).body(created);
    }

    /** Remplacement complet, avec {@code version} ; un passage en {@code DECOMMISSIONED} est refusé si le VPS est utilisé. */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(WRITE)
    ServerResponse replace(@PathVariable UUID id,
            @Validated({Default.class, ValidationGroups.Update.class}) @RequestBody ServerRequest request) {
        return servers.replace(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(DELETE)
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        servers.delete(id);
        return ResponseEntity.noContent().build();
    }
}
