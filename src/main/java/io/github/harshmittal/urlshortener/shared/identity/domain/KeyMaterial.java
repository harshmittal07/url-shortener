package io.github.harshmittal.urlshortener.shared.identity.domain;

/** Fresh random parts of a new key. {@link #toString()} never prints the secret. */
public record KeyMaterial(String prefix, String secret) {

    @Override
    public String toString() {
        return "KeyMaterial[prefix=" + prefix + ", secret=<redacted>]";
    }
}
