package com.expertiz.sentinel.core.infrastructure;

import java.util.UUID;

/**
 * Implémentée par les modules qui déploient sur des VPS (spec §7.2). {@code infrastructure} la consulte, sous le verrou
 * du VPS, avant de le supprimer ou de le décommissionner, sans dépendre de ces modules.
 */
public interface ServerUsageChecker {

    boolean hasDeployments(UUID serverId);
}
