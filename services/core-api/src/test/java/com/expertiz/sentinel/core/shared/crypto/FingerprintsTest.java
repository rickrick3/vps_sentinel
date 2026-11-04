package com.expertiz.sentinel.core.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FingerprintsTest {

    private final Fingerprints fp = new Fingerprints("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    @Test
    void identifierIsCaseAndSpaceInsensitive() {
        assertThat(fp.identifier("  Alice@Example.COM ")).isEqualTo(fp.identifier("alice@example.com"));
    }

    @Test
    void identifierAppliesNfkc() {
        assertThat(fp.identifier("ａｌｉｃｅ@example.com")).isEqualTo(fp.identifier("alice@example.com"));
    }

    @Test
    void fingerprintFormat() {
        assertThat(fp.identifier("x")).matches("[0-9a-f]{32}");
        assertThat(fp.ip("10.0.0.1")).matches("[0-9a-f]{32}");
    }

    @Test
    void domainsSeparated() {
        assertThat(fp.identifier("10.0.0.1")).isNotEqualTo(fp.ip("10.0.0.1"));
    }

    @Test
    void keyMatters() {
        byte[] keyA = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] keyB = "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII);

        assertThat(new Fingerprints(keyA).identifier("a")).isNotEqualTo(new Fingerprints(keyB).identifier("a"));
    }

    @Test
    void normalizeIdentifier() {
        assertThat(Fingerprints.normalizeIdentifier("　 ＡＤＭＩＮ@Sentinel.LOCAL \t")).isEqualTo("admin@sentinel.local");
    }
}
