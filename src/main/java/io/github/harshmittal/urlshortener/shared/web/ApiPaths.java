package io.github.harshmittal.urlshortener.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/** Whether a request targets the management API ({@code /api} or {@code /api/**}). */
final class ApiPaths {

    private ApiPaths() {}

    static boolean isApi(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api") || path.startsWith("/api/");
    }
}
