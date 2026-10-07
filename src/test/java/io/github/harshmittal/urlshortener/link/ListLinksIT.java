package io.github.harshmittal.urlshortener.link;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import io.github.harshmittal.urlshortener.link.domain.TestCodes;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.ApiCalls.Owner;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec 03: {@code GET /api/links} lists the caller's active links, newest first. Rows are inserted
 * directly for controlled creation times and volumes; the per-minute creation limit would block bulk
 * setup through the API.
 */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ListLinksIT {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private Owner owner;

    @BeforeEach
    void givenOwner() throws Exception {
        owner = ApiCalls.newOwner(mvc);
    }

    @Test
    @DisplayName("AC1 (spec 03): an owner gets 200 JSON with its links, each item as GET /api/links/{code} shows it")
    void ownerListsOwnLinks() throws Exception {
        String first = ApiCalls.newLinkCode(mvc, owner.key(), "https://example.com/list/one");
        String second = ApiCalls.newLinkCode(mvc, owner.key(), "https://example.com/list/two");

        MockHttpServletResponse response = list(owner.key(), "", "ac1-" + UUID.randomUUID());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        List<Map<String, Object>> items = JsonPath.read(response.getContentAsString(), "$.items");
        assertThat(items).containsExactlyInAnyOrder(singleRead(first), singleRead(second));
        assertThat(items).allSatisfy(item -> {
            assertThat(item).containsOnlyKeys("code", "shortUrl", "targetUrl", "status", "createdAt");
            assertThat(item).containsEntry("status", "ACTIVE");
        });
    }

    @Test
    @DisplayName("AC2 (spec 03), S-08: each owner sees only its own links; an owner with none gets an empty list")
    void ownersSeeOnlyTheirOwnLinks() throws Exception {
        Owner other = ApiCalls.newOwner(mvc);
        List<String> ownCodes = List.of(insert(owner, "ACTIVE", BASE), insert(owner, "ACTIVE", BASE.plusSeconds(1)));
        List<String> otherCodes = List.of(insert(other, "ACTIVE", BASE), insert(other, "ACTIVE", BASE.plusSeconds(2)));

        assertThat(codes(list(owner.key(), "", "ac2-" + UUID.randomUUID())))
                .containsExactlyInAnyOrderElementsOf(ownCodes);
        assertThat(codes(list(other.key(), "", "ac2-" + UUID.randomUUID())))
                .containsExactlyInAnyOrderElementsOf(otherCodes);

        MockHttpServletResponse empty = list(ApiCalls.newOwnerKey(mvc), "", "ac2-" + UUID.randomUUID());
        assertThat(empty.getStatus()).isEqualTo(200);
        assertThat(empty.getContentAsString()).isEqualTo("{\"items\":[]}");
    }

    @Test
    @DisplayName("AC3 (spec 03): deleted links are left out")
    void deletedLinksAreLeftOut() throws Exception {
        String active = insert(owner, "ACTIVE", BASE);
        insert(owner, "DELETED", BASE.plusSeconds(1));

        assertThat(codes(list(owner.key(), "", "ac3-" + UUID.randomUUID()))).containsExactly(active);
    }

    @Test
    @DisplayName("AC4 (spec 03): newest first; a creation-time tie is ordered by code, descending binary order")
    void newestFirstWithBinaryCodeTieBreak() throws Exception {
        String rest = TestCodes.random().value().substring(1);
        String oldest = insert(owner, "ACTIVE", BASE);
        String tiedUpper = insert(owner, "B" + rest, "ACTIVE", BASE.plusSeconds(10));
        String newest = insert(owner, "ACTIVE", BASE.plusSeconds(20));
        String tiedLower = insert(owner, "a" + rest, "ACTIVE", BASE.plusSeconds(10));

        assertThat(codes(list(owner.key(), "", "ac4-" + UUID.randomUUID())))
                .containsExactly(newest, tiedLower, tiedUpper, oldest);
    }

    @Test
    @DisplayName("AC5 (spec 03): without limit, the 50 newest of 51 links are listed")
    void defaultLimitIsFifty() throws Exception {
        List<String> newestFirst = insertMany(51);

        assertThat(codes(list(owner.key(), "", "ac5-" + UUID.randomUUID())))
                .containsExactlyElementsOf(newestFirst.subList(0, 50));
    }

    @Test
    @DisplayName("AC6 (spec 03): limit=100 lists the 100 newest of 101 links; limit=1 the newest")
    void limitSelectsTheNewest() throws Exception {
        List<String> newestFirst = insertMany(101);

        assertThat(codes(list(owner.key(), "?limit=100", "ac6-" + UUID.randomUUID())))
                .containsExactlyElementsOf(newestFirst.subList(0, 100));
        assertThat(codes(list(owner.key(), "?limit=1", "ac6-" + UUID.randomUUID())))
                .containsExactly(newestFirst.getFirst());
    }

    @ParameterizedTest(name = "limit={0}")
    @ValueSource(strings = {"0", "101", "-1", "abc"})
    @DisplayName("AC7 (spec 03), S-16: an invalid limit gets 400 validation-failed and no audit row")
    void invalidLimitIsRefused(String limit) throws Exception {
        String requestId = "ac7-" + UUID.randomUUID();

        MockHttpServletResponse response = list(owner.key(), "?limit=" + limit, requestId);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> problem = JsonPath.read(response.getContentAsString(), "$");
        assertThat(problem)
                .containsEntry("code", "validation-failed")
                .containsEntry("type", "urn:url-shortener:problem:validation-failed")
                .containsEntry("status", 400)
                .containsEntry("requestId", requestId)
                .doesNotContainKey("detail");
        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
    }

    @Test
    @DisplayName("AC8 (spec 03): unknown query parameters are ignored")
    void unknownParametersAreIgnored() throws Exception {
        insert(owner, "ACTIVE", BASE);
        insert(owner, "DELETED", BASE.plusSeconds(1));

        MockHttpServletResponse plain = list(owner.key(), "", "ac8-" + UUID.randomUUID());
        MockHttpServletResponse withUnknown =
                list(owner.key(), "?cursor=xyz&status=DELETED", "ac8-" + UUID.randomUUID());

        assertThat(withUnknown.getStatus()).isEqualTo(200);
        assertThat(withUnknown.getContentAsString()).isEqualTo(plain.getContentAsString());
    }

    @Test
    @DisplayName("AC10 (spec 03), S-10: a successful list writes no audit row")
    void successfulListIsNotAudited() throws Exception {
        insert(owner, "ACTIVE", BASE);
        String requestId = "ac10-" + UUID.randomUUID();

        assertThat(list(owner.key(), "", requestId).getStatus()).isEqualTo(200);

        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
    }

    @Test
    @DisplayName("AC11 (spec 03), S-12: a list logs the key ID and count, never the listed target URLs")
    void listLogsKeyIdAndCountOnly(CapturedOutput output) throws Exception {
        String secret = "secret-" + UUID.randomUUID();
        insert(owner, TestCodes.random().value(), "ACTIVE", BASE, "https://example.com/private?token=" + secret);

        assertThat(list(owner.key(), "", "ac11-" + UUID.randomUUID()).getStatus())
                .isEqualTo(200);

        assertThat(output.getOut())
                .contains("Links listed: count=1 actorKeyId=" + owner.keyId())
                .doesNotContain(secret);
    }

    @Test
    @DisplayName("AC12 (spec 03), D6: the OpenAPI document describes listLinks, its limit and the LinkList schema")
    void openApiDescribesTheListOperation() throws Exception {
        String document =
                mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();

        String operation = "$.paths['/api/links'].get";
        assertThat((String) JsonPath.read(document, operation + ".operationId")).isEqualTo("listLinks");
        Map<String, Object> limit = JsonPath.read(document, operation + ".parameters[0]");
        assertThat(limit).containsEntry("name", "limit").containsEntry("in", "query");
        assertThat(JsonPath.<Map<String, Object>>read(document, operation + ".parameters[0].schema"))
                .containsEntry("type", "integer")
                .containsEntry("minimum", 1)
                .containsEntry("maximum", 100)
                .containsEntry("default", 50);
        assertThat((String) JsonPath.read(
                        document, operation + ".responses['200'].content['application/json'].schema.$ref"))
                .isEqualTo("#/components/schemas/LinkList");
        assertThat(JsonPath.<List<String>>read(document, "$.components.schemas.LinkList.required"))
                .containsExactly("items");
        assertThat((String) JsonPath.read(document, "$.components.schemas.LinkList.properties.items.items.$ref"))
                .isEqualTo("#/components/schemas/Link");
    }

    private MockHttpServletResponse list(String key, String query, String requestId) throws Exception {
        return mvc.perform(get("/api/links" + query)
                        .header("Authorization", bearer(key))
                        .header("X-Request-Id", requestId))
                .andReturn()
                .getResponse();
    }

    private Map<String, Object> singleRead(String code) throws Exception {
        String body = mvc.perform(get("/api/links/" + code)
                        .header("Authorization", bearer(owner.key()))
                        .header("X-Request-Id", "setup-" + UUID.randomUUID()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$");
    }

    private static List<String> codes(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(200);
        return JsonPath.read(response.getContentAsString(), "$.items[*].code");
    }

    /** Inserts {@code count} active links one second apart; returns their codes, newest first. */
    private List<String> insertMany(int count) {
        List<String> newestFirst = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            newestFirst.addFirst(insert(owner, "ACTIVE", BASE.plusSeconds(i)));
        }
        return newestFirst;
    }

    private String insert(Owner linkOwner, String status, Instant createdAt) {
        return insert(linkOwner, TestCodes.random().value(), status, createdAt);
    }

    private String insert(Owner linkOwner, String code, String status, Instant createdAt) {
        return insert(linkOwner, code, status, createdAt, "https://example.com/list/" + code);
    }

    private String insert(Owner linkOwner, String code, String status, Instant createdAt, String targetUrl) {
        jdbc.sql("""
                        INSERT INTO link.links (id, code, target_url, owner_key_id, status, created_at)
                        VALUES (:id, :code, :targetUrl, :ownerKeyId, :status, :createdAt)
                        """)
                .param("id", UUID.randomUUID())
                .param("code", code)
                .param("targetUrl", targetUrl)
                .param("ownerKeyId", linkOwner.keyId())
                .param("status", status)
                .param("createdAt", Timestamp.from(createdAt))
                .update();
        return code;
    }
}
