package io.github.harshmittal.urlshortener.support;

import io.github.harshmittal.urlshortener.app.UrlShortenerApplication;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Full application context on Testcontainers Postgres. Runs in the {@code integrationTest} task. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@SpringBootTest(classes = UrlShortenerApplication.class)
@Import(TestcontainersConfiguration.class)
@ContextConfiguration(initializers = GeneratedRequiredVariables.class)
@ActiveProfiles("integration")
public @interface IntegrationTest {}
