package io.github.harshmittal.urlshortener.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Common HTTP steps for integration tests. */
public final class ApiCalls {

    private ApiCalls() {}

    /** An issued owner key and its key ID. */
    public record Owner(UUID keyId, String key) {}

    public static String bearer(String key) {
        return "Bearer " + key;
    }

    /** Issues a new owner key with the test admin key. */
    public static Owner newOwner(MockMvc mvc) throws Exception {
        String body = mvc.perform(post("/api/keys")
                        .header("Authorization", bearer(TestAdminKey.key()))
                        .header("X-Request-Id", "setup-" + UUID.randomUUID()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Owner(UUID.fromString(JsonPath.read(body, "$.id")), JsonPath.read(body, "$.key"));
    }

    public static String newOwnerKey(MockMvc mvc) throws Exception {
        return newOwner(mvc).key();
    }

    /** {@code POST /api/links} with the given target. */
    public static ResultActions createLink(MockMvc mvc, String key, String targetUrl, String requestId)
            throws Exception {
        return mvc.perform(post("/api/links")
                .header("Authorization", bearer(key))
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUrl\":\"" + targetUrl + "\"}"));
    }

    /** Creates a link and returns its code. */
    public static String newLinkCode(MockMvc mvc, String key, String targetUrl) throws Exception {
        String body = createLink(mvc, key, targetUrl, "setup-" + UUID.randomUUID())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    /** The public prefix of a key, as stored in {@code identity.api_keys.key_prefix}. */
    public static String prefixOf(String key) {
        return key.substring("usk_".length(), "usk_".length() + 12);
    }
}
