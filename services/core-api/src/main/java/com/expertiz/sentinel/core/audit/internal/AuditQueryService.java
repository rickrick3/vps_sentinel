package com.expertiz.sentinel.core.audit.internal;

import com.expertiz.sentinel.core.audit.AuditLogResponse;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.text.TextRules;
import com.expertiz.sentinel.core.shared.web.PageRequests;
import com.expertiz.sentinel.core.shared.web.PageResponse;
import com.expertiz.sentinel.core.shared.web.SortSpec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lecture du journal d'audit (spec §10.1, §10.2). Les filtres se combinent ; {@code from} est inclus et {@code to}
 * exclu, si bien que deux intervalles consécutifs ne comptent jamais deux fois la même ligne. Seul {@code occurredAt}
 * est triable ; les égalités de date sont départagées par id, dans le sens du tri.
 */
@Service
public class AuditQueryService {

    public static final SortSpec SORT = new SortSpec(Map.of("occurredAt", "occurred_at"), "occurredAt,desc");

    // Longueurs des colonnes (spec §9.5) : un filtre plus long ne pourrait rien trouver.
    private static final int ACTION_MAX = 64;
    private static final int TARGET_TYPE_MAX = 32;

    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditQueryService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(String actorUserId, String action, String targetType, String targetId,
            String from, String to, Integer page, Integer size, String sort) {
        List<FieldViolation> violations = new ArrayList<>();
        UUID actorFilter = uuid(actorUserId, "actorUserId", violations);
        String actionFilter = text(action, "action", ACTION_MAX, violations);
        String targetTypeFilter = text(targetType, "targetType", TARGET_TYPE_MAX, violations);
        UUID targetFilter = uuid(targetId, "targetId", violations);
        Instant fromFilter = instant(from, "from", violations);
        Instant toFilter = instant(to, "to", violations);
        if (fromFilter != null && toFilter != null && !toFilter.isAfter(fromFilter)) {
            violations.add(new FieldViolation("to", "doit être postérieure à from"));
        }
        if (!violations.isEmpty()) {
            throw new ApiException(ProblemType.VALIDATION_ERROR, "Un ou plusieurs filtres sont invalides.", violations);
        }
        Pageable pageable = PageRequests.of(page, size, sort, SORT);

        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        filter(conditions, params, "actor_user_id = :actor", "actor", actorFilter);
        filter(conditions, params, "action = :action", "action", actionFilter);
        filter(conditions, params, "target_type = :targetType", "targetType", targetTypeFilter);
        filter(conditions, params, "target_id = :target", "target", targetFilter);
        filter(conditions, params, "occurred_at >= :from", "from", utc(fromFilter));
        filter(conditions, params, "occurred_at < :to", "to", utc(toFilter));
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        // La colonne vient de la liste blanche SORT, jamais directement de la requête.
        Sort.Order order = pageable.getSort().iterator().next();
        String direction = order.isAscending() ? "ASC" : "DESC";
        List<AuditLogResponse> items = jdbc.sql("""
                        SELECT id, occurred_at, actor_type, actor_user_id, actor_email, action, target_type, target_id,
                               details::text AS details, host(ip) AS ip, correlation_id
                        FROM audit_logs""" + where
                        + " ORDER BY " + order.getProperty() + " " + direction + ", id " + direction
                        + " LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", pageable.getPageSize())
                .param("offset", pageable.getOffset())
                .query(this::row)
                .list();
        return PageResponse.from(PageableExecutionUtils.getPage(items, pageable,
                () -> jdbc.sql("SELECT count(*) FROM audit_logs" + where).params(params).query(Long.class).single()));
    }

    private AuditLogResponse row(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLogResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getString("actor_type"),
                rs.getObject("actor_user_id", UUID.class),
                rs.getString("actor_email"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getObject("target_id", UUID.class),
                json.readValue(rs.getString("details"), DETAILS),
                rs.getString("ip"),
                rs.getString("correlation_id"));
    }

    private static void filter(List<String> conditions, Map<String, Object> params, String condition, String name, Object value) {
        if (value != null) {
            conditions.add(condition);
            params.put(name, value);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static UUID uuid(String raw, String field, List<FieldViolation> violations) {
        String clean = TextRules.clean(raw);
        if (clean == null) {
            return null;
        }
        try {
            return UUID.fromString(clean);
        } catch (IllegalArgumentException e) {
            violations.add(new FieldViolation(field, "identifiant invalide"));
            return null;
        }
    }

    private static String text(String raw, String field, int max, List<FieldViolation> violations) {
        String clean = TextRules.clean(raw);
        if (clean != null && clean.codePointCount(0, clean.length()) > max) {
            violations.add(new FieldViolation(field, max + " caractères maximum"));
            return null;
        }
        return clean;
    }

    private static Instant instant(String raw, String field, List<FieldViolation> violations) {
        String clean = TextRules.clean(raw);
        if (clean == null) {
            return null;
        }
        try {
            return Instant.parse(clean);
        } catch (DateTimeParseException e) {
            violations.add(new FieldViolation(field, "date ISO-8601 attendue, par exemple 2026-11-02T10:30:00Z"));
            return null;
        }
    }
}
