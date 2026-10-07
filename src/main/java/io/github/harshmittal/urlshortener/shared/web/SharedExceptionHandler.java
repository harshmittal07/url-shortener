package io.github.harshmittal.urlshortener.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception that reaches Spring MVC into problem+json with a stable code. Responses
 * never carry exception messages, class names or stack traces (S-16); those go to the server log.
 * Ordered last so module handlers for their own exceptions win over the catch-all.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class SharedExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SharedExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request failed with status {}", status.value(), ex);
        }
        // The framework's body may carry an internal detail message, so it is replaced, not enriched.
        var problem = ProblemDetails.of(status, ProblemDetails.codeFor(status), requestIdOf(request));
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static String requestIdOf(WebRequest request) {
        if (request instanceof NativeWebRequest nativeRequest
                && nativeRequest.getNativeRequest(HttpServletRequest.class) instanceof HttpServletRequest servlet) {
            return RequestIdFilter.requestIdOf(servlet);
        }
        return null;
    }
}
