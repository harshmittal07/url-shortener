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
                .body(new CreatedLinkResponse(
                        link.code().value(),
                        publicBaseUrl + "/" + link.code().value(),
                        link.targetUrl(),
                        link.createdAt()));
    }

    /** Unknown fields are ignored (AGENTS.md §7). */
    record CreateLinkRequest(@NotNull String targetUrl) {}

    record CreatedLinkResponse(String code, String shortUrl, String targetUrl, Instant createdAt) {}
}
