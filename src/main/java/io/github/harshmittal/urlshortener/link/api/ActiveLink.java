package io.github.harshmittal.urlshortener.link.api;

/** What a caller outside the link module may know about an active link. */
public record ActiveLink(String code, String targetUrl) {}
