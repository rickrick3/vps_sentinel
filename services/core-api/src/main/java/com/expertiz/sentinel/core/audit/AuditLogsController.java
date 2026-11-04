package com.expertiz.sentinel.core.audit;

import com.expertiz.sentinel.core.audit.internal.AuditQueryService;
import com.expertiz.sentinel.core.shared.security.Permissions;
import com.expertiz.sentinel.core.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Journal d'audit en lecture seule (spec §10.1) : permission {@code audit:read}. */
@RestController
@RequestMapping("/api/v1/audit-logs")
class AuditLogsController {

    private static final String READ = "hasAuthority('" + Permissions.AUDIT_READ + "')";

    private final AuditQueryService queries;

    AuditLogsController(AuditQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize(READ)
    PageResponse<AuditLogResponse> list(
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @Parameter(description = "Inclus. Instant ISO-8601, par exemple 2026-11-02T10:30:00Z.")
            @RequestParam(required = false) String from,
            @Parameter(description = "Exclu. Instant ISO-8601, postérieur à from.")
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return queries.list(actorUserId, action, targetType, targetId, from, to, page, size, sort);
    }
}
