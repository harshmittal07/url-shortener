package io.github.harshmittal.urlshortener.app;

import io.github.harshmittal.urlshortener.support.GeneratedRequiredVariables;
import io.github.harshmittal.urlshortener.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestUrlShortenerApplication {

    public static void main(String[] args) {
        GeneratedRequiredVariables.values().forEach((name, value) -> System.setProperty(name, value.toString()));
        SpringApplication.from(UrlShortenerApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("integration")
                .run(args);
    }
}
