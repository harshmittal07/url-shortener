package io.github.harshmittal.urlshortener.link.domain;

/**
 * The link is unknown, malformed, deleted or owned by another key. One exception for all four, so
 * the response never reveals which (R11, S-08).
 */
public final class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException() {
        super("Link not found");
    }
}
