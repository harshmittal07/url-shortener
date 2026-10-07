package io.github.harshmittal.urlshortener.shared.identity.adapter.out.random;

import io.github.harshmittal.urlshortener.shared.identity.domain.KeyMaterialGenerator;
import io.github.harshmittal.urlshortener.shared.identity.domain.KeyMaterialGeneratorContract;

class SecureRandomKeyMaterialGeneratorTest extends KeyMaterialGeneratorContract {

    private final SecureRandomKeyMaterialGenerator generator = new SecureRandomKeyMaterialGenerator();

    @Override
    protected KeyMaterialGenerator generator() {
        return generator;
    }
}
