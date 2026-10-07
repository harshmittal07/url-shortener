package io.github.harshmittal.urlshortener.redirect.adapter.in.web;

import io.github.harshmittal.urlshortener.redirect.domain.RedirectService;
import io.github.harshmittal.urlshortener.shared.web.ProblemDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /{code}}: public. Always {@code 302}, never {@code 301} (D5). */
@RestController
@Tag(name = "redirect")
public class RedirectController {

    private final RedirectService redirects;

    public RedirectController(RedirectService redirects) {
        this.redirects = redirects;
    }

    @GetMapping("/{code}")
    @Operation(operationId = "redirect", summary = "Follow a short link (public)")
    @ApiResponse(
            responseCode = "302",
            description = "Found",
            headers = @Header(name = "Location", schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "404", ref = ProblemDetails.OPENAPI_RESPONSE)
    @ApiResponse(responseCode = "503", ref = ProblemDetails.OPENAPI_RESPONSE)
    ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = redirects.resolve(code).orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND));
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, target)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
