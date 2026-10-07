package io.github.harshmittal.urlshortener.link.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Parses and classifies literal IP hosts (S-02) without touching DNS: no {@code InetAddress}, only
 * string parsing. IPv4 follows the WHATWG URL host parser that browsers use, so every encoding a
 * browser accepts (decimal, octal, hex, short forms) is seen as the address it really is.
 */
final class IpLiterals {

    private static final long IPV4_MAX = 0xFFFF_FFFFL;

    /**
     * Blocked IPv4 ranges: unspecified, private (RFC 1918), CGNAT, loopback, link-local,
     * benchmarking, multicast and limited broadcast.
     */
    private static final List<Ipv4Range> BLOCKED_IPV4 = List.of(
            Ipv4Range.of(0, 0, 0, 0, 8),
            Ipv4Range.of(10, 0, 0, 0, 8),
            Ipv4Range.of(100, 64, 0, 0, 10),
            Ipv4Range.of(127, 0, 0, 0, 8),
            Ipv4Range.of(169, 254, 0, 0, 16),
            Ipv4Range.of(172, 16, 0, 0, 12),
            Ipv4Range.of(192, 168, 0, 0, 16),
            Ipv4Range.of(198, 18, 0, 0, 15),
            Ipv4Range.of(224, 0, 0, 0, 4),
            Ipv4Range.of(255, 255, 255, 255, 32));

    private IpLiterals() {}

    /**
     * Whether a host must be parsed as IPv4: its last label is a number (WHATWG "ends in a number").
     * Such a host is either a valid IPv4 address or invalid; it is never a domain name.
     */
    static boolean endsInNumber(String host) {
        String last = host.substring(host.lastIndexOf('.') + 1);
        if (!last.isEmpty() && last.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return true;
        }
        return hasHexPrefix(last) && last.substring(2).chars().allMatch(c -> Character.digit(c, 16) >= 0);
    }

    /** The 32-bit address, or empty if the host is not a valid IPv4 address in any encoding. */
    static OptionalLong parseIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length > 4) {
            return OptionalLong.empty();
        }
        long[] numbers = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            OptionalLong number = parseIpv4Number(parts[i]);
            if (number.isEmpty()) {
                return OptionalLong.empty();
            }
            numbers[i] = number.getAsLong();
        }
        // Short forms: every part but the last is one byte; the last fills the remaining bytes.
        long address = 0;
        for (int i = 0; i < numbers.length - 1; i++) {
            if (numbers[i] > 255) {
                return OptionalLong.empty();
            }
            address |= numbers[i] << (8 * (3 - i));
        }
        long last = numbers[numbers.length - 1];
        if (last >= 1L << (8 * (5 - numbers.length))) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(address | last);
    }

    private static OptionalLong parseIpv4Number(String part) {
        if (part.isEmpty()) {
            return OptionalLong.empty();
        }
        int radix = 10;
        String digits = part;
        if (hasHexPrefix(part)) {
            radix = 16;
            digits = part.substring(2);
        } else if (part.length() > 1 && part.charAt(0) == '0') {
            radix = 8;
            digits = part.substring(1);
        }
        long value = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = Character.digit(digits.charAt(i), radix);
            if (digit < 0) {
                return OptionalLong.empty();
            }
            value = value * radix + digit;
            if (value > IPV4_MAX) {
                return OptionalLong.empty();
            }
        }
        return OptionalLong.of(value);
    }

    private static boolean hasHexPrefix(String part) {
        return part.length() >= 2 && part.charAt(0) == '0' && (part.charAt(1) == 'x' || part.charAt(1) == 'X');
    }

    static boolean isBlockedIpv4(long address) {
        return BLOCKED_IPV4.stream().anyMatch(range -> range.contains(address));
    }

    static String formatIpv4(long address) {
        return (address >>> 24) + "." + ((address >>> 16) & 0xFF) + "." + ((address >>> 8) & 0xFF) + "."
                + (address & 0xFF);
    }

    /** The 16 bytes of an IPv6 literal (without brackets), or {@code null} if it is not valid. */
    static int[] parseIpv6(String literal) {
        String text = literal.toLowerCase(Locale.ROOT);
        String[] halves = text.split("::", -1);
        if (halves.length > 2) {
            return null;
        }
        boolean compressed = halves.length == 2;
        List<Integer> head = parseIpv6Groups(halves[0], !compressed);
        List<Integer> tail = compressed ? parseIpv6Groups(halves[1], true) : List.of();
        if (head == null || tail == null) {
            return null;
        }
        int groups = head.size() + tail.size();
        if (compressed ? groups > 7 : groups != 8) {
            return null;
        }
        int[] bytes = new int[16];
        for (int i = 0; i < head.size(); i++) {
            setGroup(bytes, i, head.get(i));
        }
        for (int i = 0; i < tail.size(); i++) {
            setGroup(bytes, 8 - tail.size() + i, tail.get(i));
        }
        return bytes;
    }

    /** 16-bit groups of one side of {@code ::}; the last group may be a dotted IPv4 address. */
    private static List<Integer> parseIpv6Groups(String side, boolean ipv4TailAllowed) {
        List<Integer> groups = new ArrayList<>();
        if (side.isEmpty()) {
            return groups;
        }
        String[] pieces = side.split(":", -1);
        for (int i = 0; i < pieces.length; i++) {
            String piece = pieces[i];
            if (i == pieces.length - 1 && ipv4TailAllowed && piece.contains(".")) {
                OptionalLong ipv4 = parseDottedDecimal(piece);
                if (ipv4.isEmpty()) {
                    return null;
                }
                groups.add((int) (ipv4.getAsLong() >>> 16));
                groups.add((int) (ipv4.getAsLong() & 0xFFFF));
            } else if (piece.length() >= 1
                    && piece.length() <= 4
                    && piece.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                groups.add(Integer.parseInt(piece, 16));
            } else {
                return null;
            }
        }
        return groups;
    }

    /** Strict four-part decimal, as allowed inside an IPv6 literal (no leading zeros, no short form). */
    private static OptionalLong parseDottedDecimal(String text) {
        String[] parts = text.split("\\.", -1);
        if (parts.length != 4) {
            return OptionalLong.empty();
        }
        long address = 0;
        for (String part : parts) {
            if (part.isEmpty()
                    || part.length() > 3
                    || (part.length() > 1 && part.charAt(0) == '0')
                    || !part.chars().allMatch(c -> c >= '0' && c <= '9')) {
                return OptionalLong.empty();
            }
            int value = Integer.parseInt(part);
            if (value > 255) {
                return OptionalLong.empty();
            }
            address = (address << 8) | value;
        }
        return OptionalLong.of(address);
    }

    private static void setGroup(int[] bytes, int group, int value) {
        bytes[2 * group] = value >>> 8;
        bytes[2 * group + 1] = value & 0xFF;
    }

    /**
     * Blocked IPv6: unspecified, loopback, unique-local (fc00::/7), link-local (fe80::/10),
     * site-local (fec0::/10), multicast (ff00::/8), and any address that carries a blocked IPv4:
     * IPv4-mapped (::ffff:0:0/96), IPv4-compatible (::/96), NAT64 (64:ff9b::/96, IPv4 in the last
     * 32 bits) and 6to4 (2002::/16, IPv4 in bits 16 to 47).
     */
    static boolean isBlockedIpv6(int[] bytes) {
        if (isZero(bytes, 0, 16) || (isZero(bytes, 0, 15) && bytes[15] == 1)) {
            return true;
        }
        if ((bytes[0] & 0xFE) == 0xFC) {
            return true;
        }
        if (bytes[0] == 0xFE && (bytes[1] & 0xC0) == 0x80) {
            return true;
        }
        if (bytes[0] == 0xFE && (bytes[1] & 0xC0) == 0xC0) {
            return true;
        }
        if (bytes[0] == 0xFF) {
            return true;
        }
        boolean mapped = isZero(bytes, 0, 10) && bytes[10] == 0xFF && bytes[11] == 0xFF;
        boolean compatible = isZero(bytes, 0, 12);
        boolean nat64 =
                bytes[0] == 0x00 && bytes[1] == 0x64 && bytes[2] == 0xFF && bytes[3] == 0x9B && isZero(bytes, 4, 12);
        if (mapped || compatible || nat64) {
            return isBlockedIpv4(embeddedIpv4(bytes, 12));
        }
        boolean sixToFour = bytes[0] == 0x20 && bytes[1] == 0x02;
        return sixToFour && isBlockedIpv4(embeddedIpv4(bytes, 2));
    }

    private static boolean isZero(int[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /** The IPv4 address in the four bytes starting at {@code offset}. */
    private static long embeddedIpv4(int[] bytes, int offset) {
        return ((long) bytes[offset] << 24) | (bytes[offset + 1] << 16) | (bytes[offset + 2] << 8) | bytes[offset + 3];
    }

    private record Ipv4Range(long network, long mask) {

        static Ipv4Range of(int a, int b, int c, int d, int prefixLength) {
            long mask = (IPV4_MAX << (32 - prefixLength)) & IPV4_MAX;
            return new Ipv4Range(((long) a << 24) | (b << 16) | (c << 8) | d, mask);
        }

        boolean contains(long address) {
            return (address & mask) == network;
        }
    }
}
