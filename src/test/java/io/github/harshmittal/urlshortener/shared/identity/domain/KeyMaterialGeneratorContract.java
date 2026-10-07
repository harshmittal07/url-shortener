package io.github.harshmittal.urlshortener.shared.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract for every {@link KeyMaterialGenerator} adapter. */
public abstract class KeyMaterialGeneratorContract {

    protected abstract KeyMaterialGenerator generator();

    @Test
    @DisplayName("S-07: key material has a 12-character Base62 prefix and a 43-character base64url secret")
    void producesKeyFormat() {
        KeyMaterial material = generator().next();

        assertThat(material.prefix()).matches("[0-9A-Za-z]{12}");
        assertThat(material.secret()).matches("[A-Za-z0-9_-]{43}");
        assertThat(ApiKeyToken.parse(new ApiKeyToken(material.prefix(), material.secret()).value()))
                .isPresent();
    }

    @Test
    @DisplayName("S-07: key material does not repeat across a sample")
    void doesNotRepeat() {
        Set<String> prefixes = new HashSet<>();
        Set<String> secrets = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            KeyMaterial material = generator().next();
            prefixes.add(material.prefix());
            secrets.add(material.secret());
        }

        assertThat(prefixes).hasSize(1_000);
        assertThat(secrets).hasSize(1_000);
    }

    @Test
    @DisplayName("S-12: key material never prints its secret")
    void hidesSecretInToString() {
        KeyMaterial material = generator().next();

        assertThat(material.toString()).doesNotContain(material.secret());
    }
}
