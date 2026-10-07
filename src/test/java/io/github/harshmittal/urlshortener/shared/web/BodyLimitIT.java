package io.github.harshmittal.urlshortener.shared.web;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.ApiCalls.Owner;
import io.github.harshmittal.urlshortener.support.RequestBodies;
import io.github.harshmittal.urlshortener.support.ServerIntegrationTest;
import io.github.harshmittal.urlshortener.support.TestAdminKey;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * R30 on a real server: the 8 KB (8,192-byte) cap applies to declared and to chunked bodies, and
 * nothing is created. Sizes are literals on purpose, not the production constant.
 */
@ServerIntegrationTest
class BodyLimitIT {

    private final HttpClient http =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("AC44: a body of exactly 8,192 bytes is accepted")
    void acceptsExactlyTheCap() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        byte[] body = RequestBodies.createLinkOfSize("https://example.com/ac44-exact", 8192);

        HttpResponse<String> response = post("/api/links", owner.key(), BodyPublishers.ofByteArray(body));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(linksOf(owner)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC44: a declared body of 8,193 bytes gets 413 payload-too-large and creates no link")
    void rejectsOneByteOverTheCap() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        byte[] body = RequestBodies.createLinkOfSize("https://example.com/ac44-over", 8193);

        HttpResponse<String> response = post("/api/links", owner.key(), BodyPublishers.ofByteArray(body));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"payload-too-large\"");
        assertThat(linksOf(owner)).isZero();
    }

    @Test
    @DisplayName("AC44: a chunked body with no Content-Length that exceeds 8,192 bytes gets 413 and creates no link")
    void rejectsOversizedChunkedBody() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        byte[] body = RequestBodies.createLinkOfSize("https://example.com/ac44-chunked", 8193);

        HttpResponse<String> response =
                post("/api/links", owner.key(), BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"payload-too-large\"");
        assertThat(linksOf(owner)).isZero();
    }

    @Test
    @DisplayName("AC44: a chunked body within the cap is accepted")
    void acceptsChunkedBodyWithinTheCap() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        byte[] body = RequestBodies.createLinkOfSize("https://example.com/ac44-chunked-ok", 8192);

        HttpResponse<String> response =
                post("/api/links", owner.key(), BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(linksOf(owner)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC44: an oversized body on POST /api/keys gets 413 and creates no key")
    void rejectsOversizedKeyRequest() throws Exception {
        long keysBefore = keyRows();
        byte[] body = new byte[8193];

        HttpResponse<String> response = post("/api/keys", TestAdminKey.key(), BodyPublishers.ofByteArray(body));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(keyRows()).isEqualTo(keysBefore);
    }

    private HttpResponse<String> post(String path, String key, BodyPublisher body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", bearer(key))
                .header("Content-Type", "application/json")
                .POST(body)
                .build();
        return http.send(request, BodyHandlers.ofString());
    }

    private int linksOf(Owner owner) {
        return jdbc.sql("SELECT count(*) FROM link.links WHERE owner_key_id = :owner")
                .param("owner", owner.keyId())
                .query(Integer.class)
                .single();
    }

    private long keyRows() {
        return jdbc.sql("SELECT count(*) FROM identity.api_keys")
                .query(Long.class)
                .single();
    }
}
