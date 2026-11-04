package com.expertiz.sentinel.core.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;

/** Secrets de test au format de {@code scripts/gen-dev-secrets.sh} : PEM PKCS#8 et X.509, clé HMAC en base64. */
public final class TestSecrets {

    private static Path directory;

    private TestSecrets() {}

    /** Dossier de secrets valides, créé une seule fois par JVM. */
    public static synchronized Path directory() {
        if (directory == null) {
            try {
                Path dir = Files.createTempDirectory("sentinel-secrets");
                write(dir, rsa(3072), random(32));
                directory = dir;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return directory;
    }

    public static KeyPair rsa(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] random(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return value;
    }

    public static void write(Path dir, KeyPair pair, byte[] hmacKey) throws IOException {
        Files.writeString(dir.resolve("jwt-private.pem"), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(dir.resolve("jwt-public.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        Files.writeString(dir.resolve("hmac.key"), Base64.getEncoder().encodeToString(hmacKey) + "\n");
    }

    public static String pem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }
}
