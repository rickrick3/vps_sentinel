package com.expertiz.sentinel.core.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Empreintes HMAC-SHA256 tronquées à 128 bits (spec §8.4 et §9.5) : clés de limitation et identifiants saisis au
 * login, sans jamais stocker la valeur brute. Les préfixes {@code id:} et {@code ip:} séparent les deux domaines.
 */
public final class Fingerprints {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int FINGERPRINT_BYTES = 16;

    private final SecretKeySpec key;

    public Fingerprints(byte[] key) {
        this.key = new SecretKeySpec(key.clone(), ALGORITHM);
    }

    public String identifier(String raw) {
        return hmac("id:" + normalizeIdentifier(raw));
    }

    public String ip(String ip) {
        return hmac("ip:" + ip);
    }

    /** Identifiant tel que comparé et stocké : forme Unicode NFKC, sans espaces aux extrémités, en minuscules. */
    public static String normalizeIdentifier(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }

    private String hmac(String input) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, FINGERPRINT_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }
}
