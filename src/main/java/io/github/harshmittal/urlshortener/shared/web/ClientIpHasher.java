package io.github.harshmittal.urlshortener.shared.web;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 of the client IP keyed with {@code IP_HASH_SALT}, as lowercase hex (A6, plan §6). */
public final class ClientIpHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public ClientIpHasher(String salt) {
        if (salt == null || salt.isEmpty()) {
            throw new IllegalArgumentException("IP_HASH_SALT must not be empty");
        }
        this.key = new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String ip) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is required by every Java platform", e);
        }
    }
}
