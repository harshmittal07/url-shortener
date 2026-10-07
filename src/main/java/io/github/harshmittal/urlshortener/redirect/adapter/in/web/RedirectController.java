package io.github.harshmittal.urlshortener.redirect.adapter.in.web;

import io.github.harshmittal.urlshortener.redirect.domain.RedirectService;
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
public class RedirectController {

    private final RedirectService redirects;

    public RedirectController(RedirectService redirects) {
        this.redirects = redirects;
    }

    @GetMapping("/{code}")
    ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = redirects.resolve(code).orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND));
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, target)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
