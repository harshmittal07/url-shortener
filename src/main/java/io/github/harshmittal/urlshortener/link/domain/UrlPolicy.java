package io.github.harshmittal.urlshortener.link.domain;

/** Decides whether a target URL may be shortened. The rules live in SECURITY.md §4 only (R8). */
public interface UrlPolicy {

    UrlPolicyDecision evaluate(String targetUrl);
}
