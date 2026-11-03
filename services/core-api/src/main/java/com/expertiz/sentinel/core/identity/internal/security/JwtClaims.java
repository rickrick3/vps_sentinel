package com.expertiz.sentinel.core.identity.internal.security;

/**
 * Access token (spec §8.1) : en-tête {@code typ}, émetteur, destinataire et claims propres à VPS Sentinel. Les claims
 * standard ({@code sub}, {@code iat}, {@code exp}, {@code jti}) gardent leur nom.
 */
public final class JwtClaims {

    public static final String ISSUER = "vps-sentinel";

    /** Seul destinataire en phase 1. Un futur service aura le sien : un jeton n'est accepté que par son destinataire. */
    public static final String AUDIENCE = "core-api";

    /** Type des access tokens au format JWT (RFC 9068) : aucun autre JWT signé par la même clé n'est accepté ici. */
    public static final String TYPE = "at+jwt";

    public static final String EMAIL = "email";
    public static final String ROLE = "role";
    public static final String PERMS = "perms";
    public static final String PCR = "pcr";

    private JwtClaims() {}
}
