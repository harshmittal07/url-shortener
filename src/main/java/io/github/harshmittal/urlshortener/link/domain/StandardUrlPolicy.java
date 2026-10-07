package io.github.harshmittal.urlshortener.link.domain;

import io.github.harshmittal.urlshortener.link.domain.UrlPolicyDecision.Accepted;
import io.github.harshmittal.urlshortener.link.domain.UrlPolicyDecision.Rejected;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The URL policy from SECURITY.md §4 (S-01 to S-05). Hosts are never DNS-resolved (S-02): literal
 * IPs are parsed as strings by {@link IpLiterals}, and names are only compared.
 */
public final class StandardUrlPolicy implements UrlPolicy {

    private static final int MAX_LENGTH = 2048;

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");
    private static final Pattern PORT = Pattern.compile(":(\\d{0,5})");
    private static final Pattern LABEL = Pattern.compile("[a-z0-9_-]{1,63}");
    private static final int MAX_PORT = 65_535;

    private final String publicHost;

    /** @param publicBaseUrl {@code PUBLIC_BASE_URL}; its host is the service's own (S-04) */
    public StandardUrlPolicy(String publicBaseUrl) {
        this.publicHost = publicHostOf(publicBaseUrl);
    }

    @Override
    public UrlPolicyDecision evaluate(String targetUrl) {
        String trimmed = trimSpaces(targetUrl);
        if (trimmed.length() > MAX_LENGTH) {
            return new Rejected(UrlRejectionReason.TOO_LONG);
        }
        if (trimmed.codePoints().anyMatch(StandardUrlPolicy::isControlOrSpace)) {
            return new Rejected(UrlRejectionReason.MALFORMED);
        }
        // The scheme is checked before full parsing, so javascript: and data: URLs are reported
        // as SCHEME_NOT_ALLOWED even when the rest would not parse.
        Matcher scheme = SCHEME.matcher(trimmed);
        if (!scheme.find() || !ALLOWED_SCHEMES.contains(scheme.group(1).toLowerCase(Locale.ROOT))) {
            return new Rejected(UrlRejectionReason.SCHEME_NOT_ALLOWED);
        }
        String authority;
        try {
            authority = new URI(trimmed).getRawAuthority();
        } catch (URISyntaxException e) {
            return new Rejected(UrlRejectionReason.MALFORMED);
        }
        if (authority == null) {
            return new Rejected(UrlRejectionReason.MALFORMED);
        }
        if (authority.indexOf('@') >= 0) {
            return new Rejected(UrlRejectionReason.USERINFO_NOT_ALLOWED);
        }
        // URI leaves the host null for IDN and numeric hosts such as 2130706433, so the authority
        // is split and the host classified here.
        HostAndPort hostAndPort = HostAndPort.split(authority);
        if (hostAndPort == null) {
            return new Rejected(UrlRejectionReason.MALFORMED);
        }
        Host host = Host.classify(hostAndPort.host());
        switch (host.kind()) {
            case INVALID -> {
                return new Rejected(UrlRejectionReason.MALFORMED);
            }
            case BLOCKED -> {
                return new Rejected(UrlRejectionReason.HOST_NOT_ALLOWED);
            }
            case ALLOWED -> {
                if (host.comparable().equals(publicHost)) {
                    return new Rejected(UrlRejectionReason.SELF_REFERENCE);
                }
            }
        }
        int authorityStart = scheme.end() + 2;
        String normalized = scheme.group(1).toLowerCase(Locale.ROOT)
                + "://"
                + host.normalized()
                + hostAndPort.port()
                + trimmed.substring(authorityStart + authority.length());
        if (normalized.length() > MAX_LENGTH) {
            return new Rejected(UrlRejectionReason.TOO_LONG);
        }
        return new Accepted(normalized);
    }

    private static String publicHostOf(String publicBaseUrl) {
        try {
            String authority = new URI(publicBaseUrl.trim()).getRawAuthority();
            HostAndPort hostAndPort = authority == null ? null : HostAndPort.split(authority);
            Host host = hostAndPort == null ? null : Host.classify(hostAndPort.host());
            if (host == null || host.kind() == Host.Kind.INVALID) {
                throw new IllegalArgumentException("PUBLIC_BASE_URL has no valid host");
            }
            return host.comparable();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("PUBLIC_BASE_URL is not a valid URL", e);
        }
    }

    /**
     * Trims plain spaces only. {@link String#trim()} would also drop a trailing NUL or CR/LF, which
     * must be rejected instead (S-05).
     */
    private static String trimSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == ' ') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(start, end);
    }

    /** CR/LF, tabs and other controls, Unicode whitespace, and invisible format characters (S-05). */
    private static boolean isControlOrSpace(int codePoint) {
        return Character.isISOControl(codePoint)
                || Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.getType(codePoint) == Character.FORMAT;
    }

    /** An authority without userinfo, split into host and the port suffix as written. */
    private record HostAndPort(String host, String port) {

        /** @return {@code null} if the authority is not {@code host[:port]} or {@code [ipv6][:port]} */
        static HostAndPort split(String authority) {
            int hostEnd;
            if (authority.startsWith("[")) {
                hostEnd = authority.indexOf(']') + 1;
                if (hostEnd == 0) {
                    return null;
                }
            } else {
                int colon = authority.indexOf(':');
                hostEnd = colon < 0 ? authority.length() : colon;
            }
            String host = authority.substring(0, hostEnd);
            String port = authority.substring(hostEnd);
            if (host.isEmpty() || !(port.isEmpty() || isValidPort(port))) {
                return null;
            }
            return new HostAndPort(host, port);
        }

        private static boolean isValidPort(String port) {
            Matcher matcher = PORT.matcher(port);
            return matcher.matches() && (matcher.group(1).isEmpty() || Integer.parseInt(matcher.group(1)) <= MAX_PORT);
        }
    }

    /**
     * A host as it is stored ({@code normalized}) and as it is compared with the public host
     * ({@code comparable}: lowercase ASCII, no trailing dot).
     */
    private record Host(Kind kind, String normalized, String comparable) {

        enum Kind {
            ALLOWED,
            BLOCKED,
            INVALID
        }

        private static final Host INVALID_HOST = new Host(Kind.INVALID, "", "");

        static Host classify(String raw) {
            if (raw.startsWith("[")) {
                String literal = raw.substring(1, raw.length() - 1);
                int[] address = IpLiterals.parseIpv6(literal);
                if (address == null) {
                    return INVALID_HOST;
                }
                String bracketed = "[" + literal.toLowerCase(Locale.ROOT) + "]";
                return new Host(IpLiterals.isBlockedIpv6(address) ? Kind.BLOCKED : Kind.ALLOWED, bracketed, bracketed);
            }
            // Browsers percent-decode hosts before parsing them, so an encoded host could hide an
            // address; it is rejected rather than decoded.
            if (raw.indexOf('%') >= 0) {
                return INVALID_HOST;
            }
            String ascii;
            try {
                // IDN mapping folds fullwidth characters and ideographic full stops, so it runs
                // before the IP and localhost checks.
                ascii = IDN.toASCII(raw).toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException e) {
                return INVALID_HOST;
            }
            String bare = ascii.endsWith(".") ? ascii.substring(0, ascii.length() - 1) : ascii;
            if (IpLiterals.endsInNumber(bare)) {
                OptionalLong address = IpLiterals.parseIpv4(bare);
                if (address.isEmpty()) {
                    return INVALID_HOST;
                }
                String dotted = IpLiterals.formatIpv4(address.getAsLong());
                return new Host(
                        IpLiterals.isBlockedIpv4(address.getAsLong()) ? Kind.BLOCKED : Kind.ALLOWED, dotted, dotted);
            }
            for (String label : bare.split("\\.", -1)) {
                if (!LABEL.matcher(label).matches()) {
                    return INVALID_HOST;
                }
            }
            return new Host(isLocalhost(bare) ? Kind.BLOCKED : Kind.ALLOWED, ascii, bare);
        }

        private static boolean isLocalhost(String host) {
            return host.equals("localhost") || host.endsWith(".localhost") || host.equals("localhost.localdomain");
        }
    }
}
