package io.github.harshmittal.urlshortener.support;

import io.github.harshmittal.urlshortener.app.UrlShortenerApplication;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Like {@link IntegrationTest}, but on a real embedded server with random public and management
 * ports, for what MockMvc cannot show: chunked bodies and the management port. MockMvc is available
 * for setup steps.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@SpringBootTest(
        classes = UrlShortenerApplication.class,
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("integration")
public @interface ServerIntegrationTest {}
