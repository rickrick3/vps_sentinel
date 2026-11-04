package com.expertiz.sentinel.core.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Adresses IP littérales (spec §10.3) : IPv4 en quatre nombres décimaux, IPv6 sans zone ; jamais un nom résolu.
 * Affichées sous la forme canonique du RFC 5952, celle de PostgreSQL.
 */
class IpAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "203.0.113.7", "0.0.0.0", "2001:db8::1", "::1", "::", "fe80::1", "2001:DB8::A"})
    void literalsAccepted(String literal) {
        assertThat(IpAddresses.parse(literal)).isPresent();
        assertThat(new IpAddressValidator().isValid(literal, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "localhost", "example.com", "999.1.1.1", "1.2.3", "10.0.0.1/24", "0x7f.0.0.1", "010.0.0.1", "fe80::1%eth0",
        "2001:db8::g", "1.2.3.4.5", "[::1]"
    })
    void nonLiteralsRejected(String value) {
        assertThat(IpAddresses.parse(value)).isEmpty();
        assertThat(new IpAddressValidator().isValid(value, null)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void absentIsLeftToOtherConstraints(String value) {
        assertThat(new IpAddressValidator().isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "10.0.0.1,                 10.0.0.1",
        "2001:db8:0:0:0:0:0:1,     2001:db8::1",
        "2001:0:0:1:0:0:0:1,       2001:0:0:1::1",
        "2001:db8:0:0:1:0:0:1,     2001:db8::1:0:0:1",
        "2001:db8:0:1:1:1:1:1,     2001:db8:0:1:1:1:1:1",
        "0:0:0:0:0:0:0:1,          ::1",
        "0:0:0:0:0:0:0:0,          ::",
        "1:0:0:0:0:0:0:0,          1::",
        "2001:DB8:0:0:0:0:0:A,     2001:db8::a"
    })
    void formatsCanonically(String input, String expected) throws Exception {
        assertThat(IpAddresses.format(InetAddress.ofLiteral(input))).isEqualTo(expected);
    }
}
