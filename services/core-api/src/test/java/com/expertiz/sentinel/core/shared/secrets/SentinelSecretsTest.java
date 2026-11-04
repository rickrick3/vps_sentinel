package com.expertiz.sentinel.core.shared.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.support.TestSecrets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SentinelSecretsTest {

    @TempDir
    Path dir;

    @Test
    void loadsFromDirectory() throws Exception {
        KeyPair pair = TestSecrets.rsa(3072);
        byte[] hmac = TestSecrets.random(32);
        TestSecrets.write(dir, pair, hmac);

        SentinelSecrets secrets = new SentinelSecrets(dir, false);

        assertThat(secrets.jwtPrivateKey().getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
        assertThat(secrets.jwtPublicKey().getEncoded()).isEqualTo(pair.getPublic().getEncoded());
        assertThat(secrets.hmacKey()).isEqualTo(hmac);
    }

    @Test
    void toleratesCrlfLineEndings() throws Exception {
        TestSecrets.write(dir, TestSecrets.rsa(3072), TestSecrets.random(32));
        for (String name : new String[] {"jwt-private.pem", "jwt-public.pem", "hmac.key"}) {
            Path file = dir.resolve(name);
            Files.writeString(file, Files.readString(file).replace("\n", "\r\n"));
        }

        assertThat(new SentinelSecrets(dir, false).hmacKey()).hasSize(32);
    }

    @Test
    void rejectsHmacKeyUnder32Bytes() throws Exception {
        TestSecrets.write(dir, TestSecrets.rsa(3072), TestSecrets.random(16));

        assertThatThrownBy(() -> new SentinelSecrets(dir, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hmac.key")
                .hasMessageContaining("32 octets");
    }

    @Test
    void invalidContentIsNeverEchoed() throws Exception {
        TestSecrets.write(dir, TestSecrets.rsa(3072), TestSecrets.random(32));
        Files.writeString(dir.resolve("hmac.key"), "not-base64-SECRETVALUE!!");

        assertThatThrownBy(() -> new SentinelSecrets(dir, false))
                .hasMessageContaining("hmac.key")
                .hasMessageNotContaining("SECRETVALUE");
    }

    @Test
    void prodFailsWhenMissing() {
        assertThatThrownBy(() -> new SentinelSecrets(dir, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Secret manquant")
                .hasMessageContaining("jwt-private.pem");
    }

    @Test
    void unreadableSecretFailsWithItsName() throws Exception {
        TestSecrets.write(dir, TestSecrets.rsa(3072), TestSecrets.random(32));
        Files.delete(dir.resolve("jwt-private.pem"));
        Files.createDirectory(dir.resolve("jwt-private.pem"));

        assertThatThrownBy(() -> new SentinelSecrets(dir, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Secret illisible")
                .hasMessageContaining("jwt-private.pem");
    }

    @Test
    void rejectsMismatchedKeyPair() throws Exception {
        TestSecrets.write(dir, TestSecrets.rsa(3072), TestSecrets.random(32));
        Files.writeString(dir.resolve("jwt-public.pem"),
                TestSecrets.pem("PUBLIC KEY", TestSecrets.rsa(3072).getPublic().getEncoded()));

        assertThatThrownBy(() -> new SentinelSecrets(dir, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ne correspond pas");
    }

    @Test
    void rejectsRsaKeyShorterThan3072Bits() throws Exception {
        TestSecrets.write(dir, TestSecrets.rsa(2048), TestSecrets.random(32));

        assertThatThrownBy(() -> new SentinelSecrets(dir, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3072");
    }

    @Test
    void devGeneratesEphemeralSecretsWithoutWritingThem() throws Exception {
        SentinelSecrets secrets = new SentinelSecrets(dir, true);

        assertThat(secrets.jwtPublicKey().getModulus().bitLength()).isGreaterThanOrEqualTo(3072);
        assertThat(secrets.jwtPrivateKey().getModulus()).isEqualTo(secrets.jwtPublicKey().getModulus());
        assertThat(secrets.hmacKey()).hasSize(32);
        try (var files = Files.list(dir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void hmacKeyIsDefensivelyCopied() throws Exception {
        byte[] hmac = TestSecrets.random(32);
        TestSecrets.write(dir, TestSecrets.rsa(3072), hmac);
        SentinelSecrets secrets = new SentinelSecrets(dir, false);

        secrets.hmacKey()[0] ^= 1;

        assertThat(secrets.hmacKey()).isEqualTo(hmac);
    }
}
