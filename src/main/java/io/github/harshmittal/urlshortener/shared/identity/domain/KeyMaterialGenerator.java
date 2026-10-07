package io.github.harshmittal.urlshortener.shared.identity.domain;

/**
 * Produces a 12-character Base62 prefix and a 43-character base64url secret (256 bits, D4) from a
 * cryptographically secure source.
 */
public interface KeyMaterialGenerator {

    KeyMaterial next();
}
