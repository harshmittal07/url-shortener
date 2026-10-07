package io.github.harshmittal.urlshortener.link.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/** Seven Base62 characters (R7, S-06). */
public record ShortCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9A-Za-z]{7}");

    public ShortCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("A short code is 7 Base62 characters");
        }
    }

    public static Optional<ShortCode> parse(String value) {
        return value != null && FORMAT.matcher(value).matches() ? Optional.of(new ShortCode(value)) : Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}
