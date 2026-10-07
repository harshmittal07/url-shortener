package io.github.harshmittal.urlshortener.link.adapter.out.random;

import io.github.harshmittal.urlshortener.link.domain.ShortCodeGenerator;
import io.github.harshmittal.urlshortener.link.domain.ShortCodeGeneratorContract;

class SecureRandomShortCodeGeneratorTest extends ShortCodeGeneratorContract {

    private final SecureRandomShortCodeGenerator generator = new SecureRandomShortCodeGenerator();

    @Override
    protected ShortCodeGenerator generator() {
        return generator;
    }
}
