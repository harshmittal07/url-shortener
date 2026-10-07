package io.github.harshmittal.urlshortener.link.domain;

public sealed interface UrlPolicyDecision {

    /** @param normalizedUrl the form that is stored and returned (R8) */
    record Accepted(String normalizedUrl) implements UrlPolicyDecision {}

    record Rejected(UrlRejectionReason reason) implements UrlPolicyDecision {}
}
