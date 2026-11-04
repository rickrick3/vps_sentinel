package com.expertiz.sentinel.core.infrastructure;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** API publique d'{@code infrastructure} pour les modules qui référencent des VPS (spec §7.2). */
public interface ServerCatalog {

    /**
     * Verrouille le VPS en partage jusqu'à la fin de la transaction de l'appelant, puis vérifie qu'il peut recevoir un
     * déploiement : 404 {@code not-found} s'il n'existe pas, 409 {@code server-not-deployable} s'il est supprimé ou
     * {@code DECOMMISSIONED}. Une suppression ou un décommissionnement concurrent attend la fin de cette transaction.
     */
    ServerSummary lockForDeployment(UUID serverId);

    /** VPS non supprimés parmi {@code ids}. */
    Map<UUID, ServerSummary> summaries(Collection<UUID> ids);
}
