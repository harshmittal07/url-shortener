package io.github.harshmittal.urlshortener.shared.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.harshmittal.urlshortener.shared.identity.domain.AuthenticationResult.Failed;
import io.github.harshmittal.urlshortener.shared.identity.domain.AuthenticationResult.Reason;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiKeyAuthenticatorTest {

    private static final ApiKeyToken ADMIN = new ApiKeyToken("AdminPrefix1", "a".repeat(43));
    private static final ApiKeyToken OWNER = new ApiKeyToken("OwnerPrefix1", "b".repeat(43));
    private static final ApiKeyToken REVOKED = new ApiKeyToken("RevokedPref1", "c".repeat(43));
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    private final InMemoryApiKeyRepository keys = new InMemoryApiKeyRepository();
    private final AtomicInteger comparisons = new AtomicInteger();
    private final ApiKeyAuthenticator authenticator;

    ApiKeyAuthenticatorTest() {
        Instant created = Instant.parse("2026-10-07T10:00:00Z");
        keys.insert(new ApiKey(OWNER_ID, OWNER.prefix(), OWNER.sha256Hex(), created, null));
        keys.insert(new ApiKey(UUID.randomUUID(), REVOKED.prefix(), REVOKED.sha256Hex(), created, created));
        authenticator = new ApiKeyAuthenticator(keys, ADMIN.sha256Hex(), (presented, expected) -> {
            comparisons.incrementAndGet();
            return MessageDigest.isEqual(presented, expected);
        });
    }

    @Test
    @DisplayName("AC1, R1: the bootstrap admin key authenticates as admin with the reserved actor ID")
    void authenticatesAdmin() {
        assertThat(authenticator.authenticate(ADMIN.value()).authenticatedPrincipal())
                .contains(Principal.admin());
    }

    @Test
    @DisplayName("R3: a stored, active owner key authenticates as that owner")
    void authenticatesOwner() {
        assertThat(authenticator.authenticate(OWNER.value()).authenticatedPrincipal())
                .contains(new Principal(OWNER_ID, Role.OWNER));
    }

    @Test
    @DisplayName("AC3: no key fails with MISSING")
    void missingKey() {
        assertThat(authenticator.authenticate(null)).isEqualTo(new Failed(Reason.MISSING));
        assertThat(authenticator.authenticate("")).isEqualTo(new Failed(Reason.MISSING));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "not-a-key",
                "usk_short_" + "b",
                "usk_OwnerPrefix1_tooShortSecret",
                "Basic Zm9v",
                "usk_Owner/refix1_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "USK_OwnerPrefix1_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
            })
    @DisplayName("AC3, S-07: a malformed key fails with MALFORMED")
    void malformedKey(String token) {
        assertThat(authenticator.authenticate(token)).isEqualTo(new Failed(Reason.MALFORMED));
    }

    @Test
    @DisplayName("AC3, S-07: a well-formed key with an unknown prefix fails with UNKNOWN")
    void unknownKey() {
        var unknown = new ApiKeyToken("UnknownPref1", "d".repeat(43));

        assertThat(authenticator.authenticate(unknown.value())).isEqualTo(new Failed(Reason.UNKNOWN));
    }

    @Test
    @DisplayName("AC3, S-07: a known prefix with the wrong secret fails with UNKNOWN")
    void wrongSecret() {
        var wrongSecret = new ApiKeyToken(OWNER.prefix(), "e".repeat(43));

        assertThat(authenticator.authenticate(wrongSecret.value())).isEqualTo(new Failed(Reason.UNKNOWN));
    }

    @Test
    @DisplayName("AC3: a revoked key fails with REVOKED")
    void revokedKey() {
        assertThat(authenticator.authenticate(REVOKED.value())).isEqualTo(new Failed(Reason.REVOKED));
    }

    @Test
    @DisplayName("S-07: an unknown prefix still runs the constant-time hash comparison")
    void unknownPrefixStillCompares() {
        var unknown = new ApiKeyToken("UnknownPref1", "d".repeat(43));

        authenticator.authenticate(unknown.value());

        // One comparison against the admin hash, one against the dummy hash.
        assertThat(comparisons).hasValue(2);
    }

    @Test
    @DisplayName("S-07: a known prefix runs the same number of comparisons as an unknown one")
    void knownPrefixComparesTheSame() {
        authenticator.authenticate(new ApiKeyToken(OWNER.prefix(), "e".repeat(43)).value());

        assertThat(comparisons).hasValue(2);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "ABCDEF", "not-hex-0000000000000000000000000000000000000000000000000000000000"})
    @DisplayName("R22: an invalid BOOTSTRAP_ADMIN_KEY_HASH is rejected without echoing its value")
    void rejectsInvalidAdminHash(String invalid) {
        assertThatThrownBy(() -> new ApiKeyAuthenticator(keys, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_KEY_HASH");
    }

    @Test
    @DisplayName("S-12: a parsed key never prints its secret")
    void tokenHidesSecret() {
        assertThat(OWNER.toString()).doesNotContain(OWNER.secret());
    }
}
