package io.github.harshmittal.urlshortener.architecture.fixture.link.domain;

import org.springframework.util.Assert;

public class SpringAwareDomain {
    void check(Object value) {
        Assert.notNull(value, "value");
    }
}
