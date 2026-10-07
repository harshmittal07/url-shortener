package io.github.harshmittal.urlshortener.shared.identity.adapter.out.random;

import io.github.harshmittal.urlshortener.shared.identity.domain.KeyMaterial;
import io.github.harshmittal.urlshortener.shared.identity.domain.KeyMaterialGenerator;
import java.security.SecureRandom;
import java.util.Base64;

public final class SecureRandomKeyMaterialGenerator implements KeyMaterialGenerator {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int PREFIX_LENGTH = 12;
    private static final int SECRET_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public KeyMaterial next() {
        StringBuilder prefix = new StringBuilder(PREFIX_LENGTH);
        for (int i = 0; i < PREFIX_LENGTH; i++) {
            prefix.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return new KeyMaterial(
                prefix.toString(), Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
    }
}
