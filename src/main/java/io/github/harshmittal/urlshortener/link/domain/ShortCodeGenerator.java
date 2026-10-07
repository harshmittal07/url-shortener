package io.github.harshmittal.urlshortener.link.domain;

/** Random short codes from a cryptographically secure source (R7, S-06). */
public interface ShortCodeGenerator {

    ShortCode next();
}
