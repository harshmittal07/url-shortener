package io.github.harshmittal.urlshortener.link.adapter.in.web;

import io.github.harshmittal.urlshortener.link.domain.Link;
import io.github.harshmittal.urlshortener.link.domain.LinkService;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditContext;
import io.github.harshmittal.urlshortener.shared.web.RequestAuditContexts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Owner link endpoints (owner role enforced by the security configuration). */
@RestController
public class LinkController {

    private final LinkService links;
    private final RequestAuditContexts auditContexts;
    private final String publicBaseUrl;

    public LinkController(LinkService links, RequestAuditContexts auditContexts, String publicBaseUrl) {
        this.links = links;
        this.auditContexts = auditContexts;
        this.publicBaseUrl =
                publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @PostMapping("/api/links")
    ResponseEntity<CreatedLinkResponse> create(@Valid @RequestBody CreateLinkRequest body, HttpServletRequest request) {
        AuditContext context = auditContexts.of(request);
        Link link = links.create(body.targetUrl(), context.actorKeyId(), context);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/links/" + link.code().value()))
                .body(new CreatedLinkResponse(link.code().value(), shortUrl(link), link.targetUrl(), link.createdAt()));
    }

    /** Another key's, deleted, unknown and malformed codes all get the same {@code 404} (R11). */
    @GetMapping("/api/links/{code}")
    LinkResponse get(@PathVariable String code, HttpServletRequest request) {
        AuditContext context = auditContexts.of(request);
        Link link = links.get(code, context.actorKeyId(), context);
        return new LinkResponse(
                link.code().value(),
                shortUrl(link),
                link.targetUrl(),
                link.status().name(),
                link.createdAt());
    }

    /** Soft delete (R10); answers like {@link #get} for a code the caller may not see. */
    @DeleteMapping("/api/links/{code}")
    ResponseEntity<Void> delete(@PathVariable String code, HttpServletRequest request) {
        AuditContext context = auditContexts.of(request);
        links.delete(code, context.actorKeyId(), context);
        return ResponseEntity.noContent().build();
    }

    private String shortUrl(Link link) {
        return publicBaseUrl + "/" + link.code().value();
    }

    /** Unknown fields are ignored (AGENTS.md §7). */
    record CreateLinkRequest(@NotNull String targetUrl) {}

    record CreatedLinkResponse(String code, String shortUrl, String targetUrl, Instant createdAt) {}

    record LinkResponse(String code, String shortUrl, String targetUrl, String status, Instant createdAt) {}
}
