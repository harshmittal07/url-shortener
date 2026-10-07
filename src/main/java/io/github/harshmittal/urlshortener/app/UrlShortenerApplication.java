package io.github.harshmittal.urlshortener.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Composition root. Component scanning covers only {@code app}; every module bean is declared in an
 * {@code app} configuration class (AGENTS.md §6). Spring Security's default in-memory user is
 * excluded: callers authenticate with API keys only, and that user logged a generated password.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
