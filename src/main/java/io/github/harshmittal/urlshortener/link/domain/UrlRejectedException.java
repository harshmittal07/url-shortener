package io.github.harshmittal.urlshortener.link.domain;

public final class UrlRejectedException extends RuntimeException {

    private final UrlRejectionReason reason;

    public UrlRejectedException(UrlRejectionReason reason) {
        super("Target URL rejected: " + reason);
        this.reason = reason;
    }

    public UrlRejectionReason reason() {
        return reason;
    }
}
