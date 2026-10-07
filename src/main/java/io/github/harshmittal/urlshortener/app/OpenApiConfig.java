package io.github.harshmittal.urlshortener.app;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The generated OpenAPI document (A11). It is checked against the committed {@code
 * api/openapi.yaml} by {@code ApiContractIT} (R34, D6), so it must not depend on the host or port it
 * was fetched from.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String API_KEY = "apiKey";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                // Informational only; the API is versionless (D6).
                .info(new Info().title("URL shortener").version("1"))
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes(
                                API_KEY,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .description("API key: Authorization: Bearer usk_<prefix>_<secret>"))
                        .addSchemas("Problem", problemSchema())
                        .addResponses("Problem", problemResponse()));
    }

    /** RFC 9457 body with the stable {@code code} and the {@code requestId} (R28). */
    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .addProperty("type", new StringSchema().description("urn:url-shortener:problem:<code>"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("code", new StringSchema().description("Stable error code"))
                .addProperty("requestId", new StringSchema())
                .addProperty("instance", new StringSchema())
                .addProperty("reason", new StringSchema().description("url-rejected only"))
                .required(List.of("type", "title", "status", "code", "requestId"));
    }

    private static ApiResponse problemResponse() {
        return new ApiResponse()
                .description("Error (RFC 9457 problem details)")
                .content(new Content()
                        .addMediaType(
                                "application/problem+json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/Problem"))));
    }

    /** {@code /api/**} needs a key; the redirect is public. */
    @Bean
    OpenApiCustomizer apiKeySecurity() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (path.startsWith("/api/")) {
                item.readOperations()
                        .forEach(operation -> operation.addSecurityItem(new SecurityRequirement().addList(API_KEY)));
            }
        });
    }
}
