package com.expertiz.sentinel.core.infrastructure.internal;

import com.expertiz.sentinel.core.shared.text.IpAddresses;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** VPS tel que renvoyé par l'API ; adresses IP sous leur forme canonique. */
public record ServerResponse(UUID id, String name, String hostname, String publicIp, String privateIp, String provider,
        String region, String osFamily, String osVersion, Integer cpuCores, Integer ramMb, Integer diskGb, String adminStatus,
        String description, List<String> tags, Instant createdAt, Instant updatedAt, long version) {

    static ServerResponse of(Server server) {
        return new ServerResponse(server.getId(), server.getName(), server.getHostname(),
                server.getPublicIp() == null ? null : IpAddresses.format(server.getPublicIp()),
                server.getPrivateIp() == null ? null : IpAddresses.format(server.getPrivateIp()),
                server.getProvider(), server.getRegion(),
                server.getOsFamily() == null ? null : server.getOsFamily().name(), server.getOsVersion(),
                server.getCpuCores() == null ? null : server.getCpuCores().intValue(), server.getRamMb(), server.getDiskGb(),
                server.getAdminStatus().name(), server.getDescription(), server.getTags(), server.getCreatedAt(),
                server.getUpdatedAt(), server.getVersion());
    }
}
