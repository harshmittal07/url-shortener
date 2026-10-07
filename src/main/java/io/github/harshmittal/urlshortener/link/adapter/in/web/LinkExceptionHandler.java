package io.github.harshmittal.urlshortener.link.adapter.in.web;

import io.github.harshmittal.urlshortener.link.domain.CodeGenerationFailedException;
import io.github.harshmittal.urlshortener.link.domain.LinkNotFoundException;
import io.github.harshmittal.urlshortener.link.domain.UrlRejectedException;
import io.github.harshmittal.urlshortener.shared.web.ProblemDetails;
import io.github.harshmittal.urlshortener.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps link-module exceptions to their spec error codes. Ordered before the shared catch-all. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LinkExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LinkExceptionHandler.class);

    @ExceptionHandler(UrlRejectedException.class)
    ResponseEntity<ProblemDetail> urlRejected(UrlRejectedException e, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetails.of(HttpStatus.BAD_REQUEST, "url-rejected", RequestIdFilter.requestIdOf(request));
        problem.setProperty("reason", e.reason().name());
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    /** The same body for unknown, malformed, deleted and other owners' codes (R11, S-08). */
    @ExceptionHandler(LinkNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(LinkNotFoundException e, HttpServletRequest request) {
        return respond(
                HttpStatus.NOT_FOUND,
                ProblemDetails.of(HttpStatus.NOT_FOUND, "not-found", RequestIdFilter.requestIdOf(request)));
    }

    @ExceptionHandler(CodeGenerationFailedException.class)
    ResponseEntity<ProblemDetail> codeGenerationFailed(CodeGenerationFailedException e, HttpServletRequest request) {
        log.error("Short code generation failed", e);
        return respond(
                HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetails.of(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "code-generation-failed",
                        RequestIdFilter.requestIdOf(request)));
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
