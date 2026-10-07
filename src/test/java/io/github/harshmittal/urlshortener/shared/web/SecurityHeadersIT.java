package io.github.harshmittal.urlshortener.shared.web;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.RequestBodies;
import io.github.harshmittal.urlshortener.support.TestAdminKey;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/** R29: security headers on every /api/** response, success or error, and no-store on redirects. */
@IntegrationTest
@AutoConfigureMockMvc
class SecurityHeadersIT {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("AC43: every /api/** response carries nosniff, Cache-Control no-store and Referrer-Policy no-referrer")
    void apiResponsesCarrySecurityHeaders() throws Exception {
        String ownerKey = ApiCalls.newOwnerKey(mvc);
        String code = ApiCalls.newLinkCode(mvc, ownerKey, "https://example.com/ac43");

        Map<String, RequestBuilder> requests = new LinkedHashMap<>();
        requests.put("201 key issued", post("/api/keys").header("Authorization", bearer(TestAdminKey.key())));
        requests.put(
                "201 link created",
                post("/api/links")
                        .header("Authorization", bearer(ownerKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"https://example.com/ac43-created\"}"));
        requests.put("200 link read", get("/api/links/" + code).header("Authorization", bearer(ownerKey)));
        requests.put(
                "400 url-rejected",
                post("/api/links")
                        .header("Authorization", bearer(ownerKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"javascript:alert(1)\"}"));
        requests.put("401 no key", get("/api/links/" + code));
        requests.put("403 wrong role", post("/api/keys").header("Authorization", bearer(ownerKey)));
        requests.put("404 unknown code", get("/api/links/zzzzzzz").header("Authorization", bearer(ownerKey)));
        requests.put(
                "413 oversized body",
                post("/api/links")
                        .header("Authorization", bearer(ownerKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestBodies.createLinkOfSize("https://example.com/ac43-big", 9000)));
        requests.put("204 link deleted", delete("/api/links/" + code).header("Authorization", bearer(ownerKey)));

        for (Map.Entry<String, RequestBuilder> request : requests.entrySet()) {
            MockHttpServletResponse response =
                    mvc.perform(request.getValue()).andReturn().getResponse();

            assertThat(response.getStatus())
                    .as(request.getKey())
                    .isEqualTo(Integer.parseInt(request.getKey().substring(0, 3)));
            assertThat(response.getHeader("X-Content-Type-Options"))
                    .as(request.getKey())
                    .isEqualTo("nosniff");
            assertThat(response.getHeaders("Cache-Control"))
                    .as(request.getKey())
                    .containsExactly("no-store");
            assertThat(response.getHeader("Referrer-Policy"))
                    .as(request.getKey())
                    .isEqualTo("no-referrer");
        }
    }

    @Test
    @DisplayName("R29: a redirect carries Cache-Control no-store")
    void redirectIsNotCached() throws Exception {
        String ownerKey = ApiCalls.newOwnerKey(mvc);
        String code = ApiCalls.newLinkCode(mvc, ownerKey, "https://example.com/r29");

        MockHttpServletResponse response =
                mvc.perform(get("/" + code)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeaders("Cache-Control")).containsExactly("no-store");
    }
}
