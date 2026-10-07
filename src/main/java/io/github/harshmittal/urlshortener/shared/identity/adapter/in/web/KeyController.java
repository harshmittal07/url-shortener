package io.github.harshmittal.urlshortener.shared.identity.adapter.in.web;

import io.github.harshmittal.urlshortener.shared.identity.domain.ApiKeyIssuer;
import io.github.harshmittal.urlshortener.shared.identity.domain.IssuedApiKey;
import io.github.harshmittal.urlshortener.shared.web.ProblemDetails;
import io.github.harshmittal.urlshortener.shared.web.RequestAuditContexts;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/keys}: admin only (enforced by the security configuration). Takes no body (A9). */
@RestController
@Tag(name = "keys")
public class KeyController {

    private static final Logger log = LoggerFactory.getLogger(KeyController.class);

    private final ApiKeyIssuer issuer;
    private final RequestAuditContexts auditContexts;

    public KeyController(ApiKeyIssuer issuer, RequestAuditContexts auditContexts) {
        this.issuer = issuer;
        this.auditContexts = auditContexts;
    }

    @PostMapping("/api/keys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createApiKey", summary = "Issue an owner API key (admin only)")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreatedApiKeyResponse.class)))
    @ApiResponse(responseCode = "401", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "403", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "413", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "503", ref = ProblemDetails.OPENAPI_RESPONSE)
    CreatedApiKeyResponse create(HttpServletRequest request) {
        IssuedApiKey issued = issuer.issue(auditContexts.of(request));
        log.info("API key created: keyId={}", issued.id());
        return new CreatedApiKeyResponse(issued.id(), issued.key(), issued.createdAt());
    }

    /** The only response that ever contains a key (R2). */
    @Schema(
            name = "CreatedApiKey",
            requiredProperties = {"id", "key", "createdAt"})
    record CreatedApiKeyResponse(UUID id, String key, Instant createdAt) {
        @Override
        public String toString() {
            return "CreatedApiKeyResponse[id=" + id + ", key=<redacted>, createdAt=" + createdAt + "]";
        }
    }
}
