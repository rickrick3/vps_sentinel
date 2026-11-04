package com.expertiz.sentinel.core.infrastructure.internal;

import com.expertiz.sentinel.core.infrastructure.AdminStatus;
import com.expertiz.sentinel.core.infrastructure.ServerCatalog;
import com.expertiz.sentinel.core.infrastructure.ServerSummary;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcServerCatalog implements ServerCatalog {

    private final JdbcClient jdbc;

    JdbcServerCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Row(ServerSummary summary, boolean deleted) {}

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ServerSummary lockForDeployment(UUID serverId) {
        // FOR SHARE, puis relecture de l'état : une suppression concurrente a pris FOR UPDATE et l'a déjà validée.
        Row row = jdbc.sql("SELECT id, name, admin_status, deleted_at FROM servers WHERE id = :id FOR SHARE")
                .param("id", serverId)
                .query((rs, n) -> new Row(new ServerSummary(rs.getObject("id", UUID.class), rs.getString("name"),
                        AdminStatus.valueOf(rs.getString("admin_status"))), rs.getTimestamp("deleted_at") != null))
                .optional()
                .orElseThrow(() -> new ApiException(ProblemType.NOT_FOUND, "VPS introuvable."));
        if (row.deleted() || row.summary().adminStatus() == AdminStatus.DECOMMISSIONED) {
            throw new ApiException(ProblemType.SERVER_NOT_DEPLOYABLE, "Ce VPS est supprimé ou décommissionné : aucun déploiement possible.");
        }
        return row.summary();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ServerSummary> summaries(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("SELECT id, name, admin_status FROM servers WHERE id IN (:ids) AND deleted_at IS NULL")
                .param("ids", ids)
                .query((rs, n) -> new ServerSummary(rs.getObject("id", UUID.class), rs.getString("name"),
                        AdminStatus.valueOf(rs.getString("admin_status"))))
                .list()
                .stream()
                .collect(Collectors.toUnmodifiableMap(ServerSummary::id, Function.identity()));
    }
}
