package io.github.harshmittal.urlshortener.link.domain;

import java.security.SecureRandom;

/** Random valid codes, so tests sharing one database never collide by accident. */
public final class TestCodes {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TestCodes() {}

    public static ShortCode random() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            code.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return new ShortCode(code.toString());
    }
}
