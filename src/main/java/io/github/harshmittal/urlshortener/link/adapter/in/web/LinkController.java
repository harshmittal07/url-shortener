package io.github.harshmittal.urlshortener.link.adapter.in.web;

import io.github.harshmittal.urlshortener.link.domain.Link;
import io.github.harshmittal.urlshortener.link.domain.LinkService;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditContext;
import io.github.harshmittal.urlshortener.shared.web.ProblemDetails;
import io.github.harshmittal.urlshortener.shared.web.RequestAuditContexts;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "links")
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
    @Operation(operationId = "createLink", summary = "Create a short link (owner only)")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            headers = @Header(name = "Location", schema = @Schema(type = "string")),
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreatedLinkResponse.class)))
    @ApiResponse(responseCode = "400", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "401", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "403", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "413", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(
            responseCode = "429",
            description = "Creation limit reached",
            headers = @Header(name = "Retry-After", schema = @Schema(type = "integer")),
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(ref = "#/components/schemas/Problem")))
    @ApiResponse(responseCode = "503", ref = ProblemDetails.OPENAPI_RESPONSE)
    ResponseEntity<CreatedLinkResponse> create(@Valid @RequestBody CreateLinkRequest body, HttpServletRequest request) {
        AuditContext context = auditContexts.of(request);
        Link link = links.create(body.targetUrl(), context.actorKeyId(), context);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/links/" + link.code().value()))
                .body(new CreatedLinkResponse(link.code().value(), shortUrl(link), link.targetUrl(), link.createdAt()));
    }

    /** Another key's, deleted, unknown and malformed codes all get the same {@code 404} (R11). */
    @GetMapping("/api/links/{code}")
    @Operation(operationId = "getLink", summary = "Read one of the caller's links")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LinkResponse.class)))
    @ApiResponse(responseCode = "401", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "403", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "404", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "503", ref = ProblemDetails.OPENAPI_RESPONSE)
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
    @Operation(operationId = "deleteLink", summary = "Delete one of the caller's links")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "401", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "403", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "404", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "503", ref = ProblemDetails.OPENAPI_RESPONSE)
    ResponseEntity<Void> delete(@PathVariable String code, HttpServletRequest request) {
        AuditContext context = auditContexts.of(request);
        links.delete(code, context.actorKeyId(), context);
        return ResponseEntity.noContent().build();
    }

    private String shortUrl(Link link) {
        return publicBaseUrl + "/" + link.code().value();
    }

    /** Unknown fields are ignored (AGENTS.md §7). The length limit is enforced by the URL policy (S-05). */
    record CreateLinkRequest(
            @NotNull @Schema(maxLength = 2048) String targetUrl) {}

    @Schema(
            name = "CreatedLink",
            requiredProperties = {"code", "shortUrl", "targetUrl", "createdAt"})
    record CreatedLinkResponse(
            String code,
            @Schema(format = "uri") String shortUrl,
            @Schema(format = "uri") String targetUrl,
            Instant createdAt) {}

    /**
     * {@code status} is always {@code ACTIVE} in spec 01; spec 02 adds {@code DISABLED} (A7). It is an
     * {@code x-extensible-enum}, not an {@code enum}, so adding a value is not a breaking change (D6).
     */
    @Schema(
            name = "Link",
            requiredProperties = {"code", "shortUrl", "targetUrl", "status", "createdAt"})
    record LinkResponse(
            String code,
            @Schema(format = "uri") String shortUrl,
            @Schema(format = "uri") String targetUrl,

            @Schema(
                    description = "Clients must tolerate values not listed here",
                    extensions =
                            @Extension(
                                    properties =
                                            @ExtensionProperty(
                                                    name = "x-extensible-enum",
                                                    value = "[\"ACTIVE\"]",
                                                    parseValue = true)))
            String status,

            Instant createdAt) {}
}
