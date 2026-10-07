package io.github.harshmittal.urlshortener.shared.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An API key in the format {@code usk_<prefix>_<secret>} (plan §6). The prefix is the public key
 * ID used for lookup. {@link #toString()} never prints the secret.
 */
public record ApiKeyToken(String prefix, String secret) {

    private static final Pattern FORMAT = Pattern.compile("usk_([0-9A-Za-z]{12})_([A-Za-z0-9_-]{43})");

    public static Optional<ApiKeyToken> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(raw);
        return matcher.matches() ? Optional.of(new ApiKeyToken(matcher.group(1), matcher.group(2))) : Optional.empty();
    }

    public String value() {
        return "usk_" + prefix + "_" + secret;
    }

    /** Lowercase hex SHA-256 of the full key string, as stored in {@code key_hash}. */
    public String sha256Hex() {
        return sha256Hex(value());
    }

    static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    @Override
    public String toString() {
        return "ApiKeyToken[prefix=" + prefix + ", secret=<redacted>]";
    }
}
