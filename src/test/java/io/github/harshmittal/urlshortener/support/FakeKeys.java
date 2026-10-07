package io.github.harshmittal.urlshortener.support;

/** Well-formed but synthetic API keys for negative tests. They match no stored key. */
public final class FakeKeys {

    private FakeKeys() {}

    /** {@code usk_<prefix>_<43 × fill>}; the prefix must be 12 Base62 characters. */
    public static String withPrefix(String prefix, char fill) {
        return "usk_" + prefix + "_" + String.valueOf(fill).repeat(43);
    }
}
