package com.expertiz.sentinel.core.infrastructure.internal;

import com.expertiz.sentinel.core.infrastructure.AdminStatus;
import com.expertiz.sentinel.core.shared.persistence.AssignedIdEntity;
import com.expertiz.sentinel.core.shared.text.IpAddresses;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.net.InetAddress;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/** VPS de l'inventaire (spec §9.3). Supprimé logiquement : un VPS supprimé n'est plus jamais chargé. */
@Entity
@Table(name = "servers")
@SQLRestriction("deleted_at IS NULL")
public class Server extends AssignedIdEntity {

    /** Champs modifiables, déjà validés et normalisés : un {@code PUT} les remplace tous (spec §10.2). */
    public record Data(String name, String hostname, InetAddress publicIp, InetAddress privateIp, String provider, String region,
            OsFamily osFamily, String osVersion, Short cpuCores, Integer ramMb, Integer diskGb, AdminStatus adminStatus,
            String description, List<String> tags) {}

    @Column(nullable = false)
    private String name;

    private String hostname;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "public_ip")
    private InetAddress publicIp;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "private_ip")
    private InetAddress privateIp;

    private String provider;

    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "os_family")
    private OsFamily osFamily;

    @Column(name = "os_version")
    private String osVersion;

    @Column(name = "cpu_cores")
    private Short cpuCores;

    @Column(name = "ram_mb")
    private Integer ramMb;

    @Column(name = "disk_gb")
    private Integer diskGb;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_status", nullable = false)
    private AdminStatus adminStatus;

    private String description;

    // Chargés par lots : une page de VPS ne déclenche pas une requête par VPS.
    @ElementCollection
    @CollectionTable(name = "server_tags", joinColumns = @JoinColumn(name = "server_id"))
    @Column(name = "tag", nullable = false)
    @BatchSize(size = 100)
    private Set<String> tags = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    protected Server() {}

    public Server(Data data, Instant now) {
        apply(data);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void replace(Data data, Instant now) {
        apply(data);
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    private void apply(Data data) {
        this.name = data.name();
        this.hostname = data.hostname();
        this.publicIp = data.publicIp();
        this.privateIp = data.privateIp();
        this.provider = data.provider();
        this.region = data.region();
        this.osFamily = data.osFamily();
        this.osVersion = data.osVersion();
        this.cpuCores = data.cpuCores();
        this.ramMb = data.ramMb();
        this.diskGb = data.diskGb();
        this.adminStatus = data.adminStatus();
        this.description = data.description();
        // Modifier la collection gérée, sans la remplacer : Hibernate n'écrit que les tags ajoutés ou retirés.
        this.tags.retainAll(data.tags());
        this.tags.addAll(data.tags());
    }

    /** Champs auditables (liste blanche, spec §9.5) : tous ceux du spec §9.3 sauf les dates, plus les tags. */
    public Map<String, Object> auditState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", name);
        state.put("hostname", hostname);
        state.put("publicIp", publicIp == null ? null : IpAddresses.format(publicIp));
        state.put("privateIp", privateIp == null ? null : IpAddresses.format(privateIp));
        state.put("provider", provider);
        state.put("region", region);
        state.put("osFamily", osFamily == null ? null : osFamily.name());
        state.put("osVersion", osVersion);
        state.put("cpuCores", cpuCores == null ? null : cpuCores.intValue());
        state.put("ramMb", ramMb);
        state.put("diskGb", diskGb);
        state.put("adminStatus", adminStatus.name());
        state.put("description", description);
        state.put("tags", getTags());
        return state;
    }

    public String getName() {
        return name;
    }

    public String getHostname() {
        return hostname;
    }

    public InetAddress getPublicIp() {
        return publicIp;
    }

    public InetAddress getPrivateIp() {
        return privateIp;
    }

    public String getProvider() {
        return provider;
    }

    public String getRegion() {
        return region;
    }

    public OsFamily getOsFamily() {
        return osFamily;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public Short getCpuCores() {
        return cpuCores;
    }

    public Integer getRamMb() {
        return ramMb;
    }

    public Integer getDiskGb() {
        return diskGb;
    }

    public AdminStatus getAdminStatus() {
        return adminStatus;
    }

    public String getDescription() {
        return description;
    }

    /** Tags triés. */
    public List<String> getTags() {
        return tags.stream().sorted().toList();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
