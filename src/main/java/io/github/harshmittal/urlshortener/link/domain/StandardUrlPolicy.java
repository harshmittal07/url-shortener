package io.github.harshmittal.urlshortener.link.domain;

import io.github.harshmittal.urlshortener.link.domain.UrlPolicyDecision.Accepted;
import io.github.harshmittal.urlshortener.link.domain.UrlPolicyDecision.Rejected;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The URL policy from SECURITY.md §4. Spec 01 T2 implements S-01 (scheme allowlist) only; the
 * host, userinfo, self-reference, length and normalization rules (S-02 to S-05) arrive in T4.
 */
public final class StandardUrlPolicy implements UrlPolicy {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");

    @Override
    public UrlPolicyDecision evaluate(String targetUrl) {
        String trimmed = targetUrl.trim();
        // The scheme is checked before full parsing, so javascript: and data: URLs are reported
        // as SCHEME_NOT_ALLOWED even when the rest would not parse.
        Matcher scheme = SCHEME.matcher(trimmed);
        if (!scheme.find() || !ALLOWED_SCHEMES.contains(scheme.group(1).toLowerCase(Locale.ROOT))) {
            return new Rejected(UrlRejectionReason.SCHEME_NOT_ALLOWED);
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return new Rejected(UrlRejectionReason.MALFORMED);
        }
        if (uri.getHost() == null) {
            return new Rejected(UrlRejectionReason.MALFORMED);
        }
        String lowerScheme = scheme.group(1).toLowerCase(Locale.ROOT);
        return new Accepted(lowerScheme + trimmed.substring(lowerScheme.length()));
    }
}
