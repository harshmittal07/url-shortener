package io.github.harshmittal.urlshortener.redirect.domain;

import io.github.harshmittal.urlshortener.link.api.ActiveLink;
import io.github.harshmittal.urlshortener.link.api.LinkLookup;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves a code to its target through {@link LinkLookup} only (R14). Malformed codes are
 * rejected here, before any lookup. Performs no writes.
 */
public final class RedirectService {

    private static final Pattern CODE = Pattern.compile("[0-9A-Za-z]{7}");

    private final LinkLookup links;

    public RedirectService(LinkLookup links) {
        this.links = links;
    }

    /** The target URL for an active link; empty for unknown, deleted or malformed codes (R13). */
    public Optional<String> resolve(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            return Optional.empty();
        }
        return links.findActive(code).map(ActiveLink::targetUrl);
    }
}
