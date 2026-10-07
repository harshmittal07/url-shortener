package io.github.harshmittal.urlshortener.shared.identity.domain;

import java.util.UUID;

/** An authenticated caller. The admin uses the reserved {@link #ADMIN_KEY_ID} (A3). */
public record Principal(UUID keyId, Role role) {

    public static final UUID ADMIN_KEY_ID = new UUID(0L, 0L);

    public static Principal admin() {
        return new Principal(ADMIN_KEY_ID, Role.ADMIN);
    }
}
