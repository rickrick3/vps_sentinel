package com.expertiz.sentinel.core.shared.text;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Adresses IP littérales (spec §10.3) : IPv4 en quatre nombres décimaux sans zéro initial, IPv6 sans identifiant de
 * zone. Jamais de résolution de nom. Affichage canonique du RFC 5952 (celui de PostgreSQL) : une même adresse a
 * toujours la même écriture.
 */
public final class IpAddresses {

    private static final Pattern IPV4 = Pattern.compile("((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]+");

    private IpAddresses() {}

    public static Optional<InetAddress> parse(String literal) {
        if (literal == null) {
            return Optional.empty();
        }
        boolean ipv6 = literal.indexOf(':') >= 0;
        if (ipv6 ? !IPV6_CHARS.matcher(literal).matches() : !IPV4.matcher(literal).matches()) {
            return Optional.empty();
        }
        try {
            InetAddress address = InetAddress.ofLiteral(literal);
            // « ::ffff:a.b.c.d » donne une Inet4Address : elle reste admise, sous sa forme IPv4.
            return Optional.of(address);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** RFC 5952 : hexadécimal en minuscules sans zéro initial, plus longue suite de groupes nuls (au moins deux) en « :: ». */
    public static String format(InetAddress address) {
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        }
        byte[] bytes = ((Inet6Address) address).getAddress();
        int[] groups = new int[8];
        for (int i = 0; i < 8; i++) {
            groups[i] = ((bytes[2 * i] & 0xff) << 8) | (bytes[2 * i + 1] & 0xff);
        }
        int bestStart = -1;
        int bestLength = 1;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int start = i;
            while (i < 8 && groups[i] == 0) {
                i++;
            }
            if (i - start > bestLength) {
                bestStart = start;
                bestLength = i - start;
            }
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                text.append("::");
                i += bestLength - 1;
                continue;
            }
            if (!text.isEmpty() && text.charAt(text.length() - 1) != ':') {
                text.append(':');
            }
            text.append(Integer.toHexString(groups[i]));
        }
        return text.toString();
    }
}
