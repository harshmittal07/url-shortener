package io.github.harshmittal.urlshortener.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root. Component scanning covers only {@code app}; every module bean is declared in an
 * {@code app} configuration class (AGENTS.md §6).
 */
@SpringBootApplication
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
