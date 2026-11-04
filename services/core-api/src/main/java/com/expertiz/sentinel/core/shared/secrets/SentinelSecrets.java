package com.expertiz.sentinel.core.shared.secrets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Secrets fichiers de core-api (spec §6.6), lus au démarrage : paire RSA de signature des JWT et clé HMAC des
 * empreintes. Un secret absent, illisible ou invalide empêche le démarrage ; les messages nomment le fichier, jamais
 * son contenu. En profil dev uniquement, des secrets absents sont remplacés par des secrets éphémères.
 */
public final class SentinelSecrets {

    static final String PRIVATE_KEY = "jwt-private.pem";
    static final String PUBLIC_KEY = "jwt-public.pem";
    static final String HMAC_KEY = "hmac.key";

    private static final int MIN_RSA_BITS = 3072;
    private static final int MIN_HMAC_BYTES = 32;
    private static final Logger log = LoggerFactory.getLogger(SentinelSecrets.class);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final byte[] hmacKey;

    public SentinelSecrets(Path directory, boolean ephemeralWhenMissing) {
        List<String> missing = Stream.of(PRIVATE_KEY, PUBLIC_KEY, HMAC_KEY)
                .filter(name -> !Files.exists(directory.resolve(name)))
                .toList();
        if (missing.isEmpty()) {
            privateKey = privateKey(read(directory, PRIVATE_KEY));
            publicKey = publicKey(read(directory, PUBLIC_KEY));
            hmacKey = hmacKey(read(directory, HMAC_KEY));
        } else if (ephemeralWhenMissing) {
            log.warn("Secrets absents de {} : clés JWT et HMAC éphémères générées (profil dev). "
                    + "Les jetons émis ne survivront pas à un redémarrage.", directory);
            KeyPair pair = generateRsaPair();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
            hmacKey = new byte[MIN_HMAC_BYTES];
            new SecureRandom().nextBytes(hmacKey);
        } else {
            throw new IllegalStateException("Secret manquant : " + missing.getFirst() + " (dossier " + directory + ")");
        }
        if (publicKey.getModulus().bitLength() < MIN_RSA_BITS) {
            throw invalid(PUBLIC_KEY, "RSA " + MIN_RSA_BITS + " bits minimum");
        }
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw invalid(PUBLIC_KEY, "ne correspond pas à " + PRIVATE_KEY);
        }
    }

    public RSAPrivateKey jwtPrivateKey() {
        return privateKey;
    }

    public RSAPublicKey jwtPublicKey() {
        return publicKey;
    }

    public byte[] hmacKey() {
        return hmacKey.clone();
    }

    private static String read(Path directory, String name) {
        try {
            return Files.readString(directory.resolve(name), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Secret illisible : " + name + " (dossier " + directory + ")", e);
        }
    }

    private static RSAPrivateKey privateKey(String pem) {
        try {
            return (RSAPrivateKey) rsa().generatePrivate(new PKCS8EncodedKeySpec(pemBody(pem, "PRIVATE KEY", PRIVATE_KEY)));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw invalid(PRIVATE_KEY, "clé privée RSA PKCS#8 attendue");
        }
    }

    private static RSAPublicKey publicKey(String pem) {
        try {
            return (RSAPublicKey) rsa().generatePublic(new X509EncodedKeySpec(pemBody(pem, "PUBLIC KEY", PUBLIC_KEY)));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw invalid(PUBLIC_KEY, "clé publique RSA X.509 attendue");
        }
    }

    private static byte[] hmacKey(String content) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(content.strip());
        } catch (IllegalArgumentException e) {
            throw invalid(HMAC_KEY, "base64 attendu");
        }
        if (key.length < MIN_HMAC_BYTES) {
            throw invalid(HMAC_KEY, MIN_HMAC_BYTES + " octets minimum une fois décodé");
        }
        return key;
    }

    private static byte[] pemBody(String pem, String label, String name) {
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        int start = pem.indexOf(begin);
        int stop = pem.indexOf(end);
        if (start < 0 || stop < start) {
            throw invalid(name, "bloc PEM « " + label + " » attendu");
        }
        try {
            return Base64.getDecoder().decode(pem.substring(start + begin.length(), stop).replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw invalid(name, "base64 invalide");
        }
    }

    private static KeyPair generateRsaPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MIN_RSA_BITS);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Génération de la paire RSA impossible", e);
        }
    }

    private static KeyFactory rsa() throws GeneralSecurityException {
        return KeyFactory.getInstance("RSA");
    }

    private static IllegalStateException invalid(String name, String reason) {
        return new IllegalStateException("Secret invalide : " + name + " (" + reason + ")");
    }
}
