package io.github.harshmittal.urlshortener.support;

import java.nio.charset.StandardCharsets;

/** Request bodies of an exact size, for the 8 KB cap (R30). */
public final class RequestBodies {

    private RequestBodies() {}

    /**
     * A valid {@code POST /api/links} body of exactly {@code totalBytes} bytes: the target plus an
     * unknown {@code pad} field, which the API ignores (AGENTS.md §7).
     */
    public static byte[] createLinkOfSize(String targetUrl, int totalBytes) {
        String prefix = "{\"targetUrl\":\"" + targetUrl + "\",\"pad\":\"";
        String suffix = "\"}";
        int padding = totalBytes - prefix.length() - suffix.length();
        if (padding < 0) {
            throw new IllegalArgumentException("Target too long for " + totalBytes + " bytes");
        }
        byte[] body = (prefix + "a".repeat(padding) + suffix).getBytes(StandardCharsets.UTF_8);
        if (body.length != totalBytes) {
            throw new IllegalStateException("Body is " + body.length + " bytes, not " + totalBytes);
        }
        return body;
    }
}
