package io.github.harshmittal.urlshortener.link.adapter.out.random;

import io.github.harshmittal.urlshortener.link.domain.ShortCode;
import io.github.harshmittal.urlshortener.link.domain.ShortCodeGenerator;
import java.security.SecureRandom;

/** 7 Base62 characters from {@code SecureRandom}: about 3.5 × 10¹² codes (S-06). */
public final class SecureRandomShortCodeGenerator implements ShortCodeGenerator {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    @Override
    public ShortCode next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        return new ShortCode(code.toString());
    }
}
