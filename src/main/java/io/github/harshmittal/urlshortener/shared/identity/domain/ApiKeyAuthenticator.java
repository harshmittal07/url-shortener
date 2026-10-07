package io.github.harshmittal.urlshortener.shared.identity.domain;

import io.github.harshmittal.urlshortener.shared.identity.domain.AuthenticationResult.Authenticated;
import io.github.harshmittal.urlshortener.shared.identity.domain.AuthenticationResult.Failed;
import io.github.harshmittal.urlshortener.shared.identity.domain.AuthenticationResult.Reason;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Authenticates API keys by SHA-256 hash with constant-time comparison (S-07, plan §6). The admin
 * key is known only by the hash in {@code BOOTSTRAP_ADMIN_KEY_HASH} (R1).
 */
public final class ApiKeyAuthenticator implements Authenticator {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    // Compared when no key has the presented prefix, so unknown and wrong keys take the same path.
    private static final byte[] DUMMY_HASH = ascii("0".repeat(64));

    private final ApiKeyRepository keys;
    private final byte[] adminKeyHash;
    private final HashComparison comparison;

    public ApiKeyAuthenticator(ApiKeyRepository keys, String adminKeyHash) {
        this(keys, adminKeyHash, MessageDigest::isEqual);
    }

    ApiKeyAuthenticator(ApiKeyRepository keys, String adminKeyHash, HashComparison comparison) {
        if (adminKeyHash == null || !SHA256_HEX.matcher(adminKeyHash).matches()) {
            throw new IllegalArgumentException("BOOTSTRAP_ADMIN_KEY_HASH must be 64 lowercase hex characters");
        }
        this.keys = keys;
        this.adminKeyHash = ascii(adminKeyHash);
        this.comparison = comparison;
    }

    @Override
    public AuthenticationResult authenticate(String bearerToken) {
        if (bearerToken == null || bearerToken.isEmpty()) {
            return new Failed(Reason.MISSING);
        }
        Optional<ApiKeyToken> token = ApiKeyToken.parse(bearerToken);
        if (token.isEmpty()) {
            return new Failed(Reason.MALFORMED);
        }
        byte[] presented = ascii(token.get().sha256Hex());
        if (comparison.equal(presented, adminKeyHash)) {
            return new Authenticated(Principal.admin());
        }
        Optional<ApiKey> stored = keys.findByPrefix(token.get().prefix());
        boolean hashMatches = comparison.equal(
                presented, stored.map(key -> ascii(key.keyHash())).orElse(DUMMY_HASH));
        if (stored.isEmpty() || !hashMatches) {
            return new Failed(Reason.UNKNOWN);
        }
        if (stored.get().isRevoked()) {
            return new Failed(Reason.REVOKED);
        }
        return new Authenticated(new Principal(stored.get().id(), Role.OWNER));
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    /** Constant-time equality; a seam so tests can prove the comparison always runs. */
    @FunctionalInterface
    interface HashComparison {
        boolean equal(byte[] presented, byte[] expected);
    }
}
