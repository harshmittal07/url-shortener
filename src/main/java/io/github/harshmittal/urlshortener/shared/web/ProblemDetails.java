package io.github.harshmittal.urlshortener.shared.web;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 bodies with a stable {@code code} and the {@code requestId} (R28). */
public final class ProblemDetails {

    private static final String TYPE_PREFIX = "urn:url-shortener:problem:";

    private ProblemDetails() {}

    public static ProblemDetail of(HttpStatusCode status, String code, String requestId) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(reasonPhrase(status));
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestId);
        return problem;
    }

    /** The spec's error code for a status raised by the framework rather than by a use case. */
    public static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "validation-failed";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not-found";
            case 413 -> "payload-too-large";
            case 500 -> "internal-error";
            case 503 -> "service-unavailable";
            // Not in the spec's table (e.g. 405, 415): a stable code derived from the status.
            default -> reasonPhrase(status).toLowerCase(Locale.ROOT).replace(' ', '-');
        };
    }

    private static String reasonPhrase(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.getReasonPhrase() : "Error";
    }
}
