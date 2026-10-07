package io.github.harshmittal.urlshortener.shared.identity.adapter.out.persistence;

import io.github.harshmittal.urlshortener.shared.identity.domain.ApiKey;
import io.github.harshmittal.urlshortener.shared.identity.domain.ApiKeyRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class JdbcApiKeyRepository implements ApiKeyRepository {

    private final JdbcClient jdbc;

    public JdbcApiKeyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ApiKey key) {
        jdbc.sql("""
                        INSERT INTO identity.api_keys (id, key_prefix, key_hash, created_at, revoked_at)
                        VALUES (:id, :prefix, :hash, :createdAt, :revokedAt)
                        """)
                .param("id", key.id())
                .param("prefix", key.prefix())
                .param("hash", key.keyHash())
                .param("createdAt", Timestamp.from(key.createdAt()))
                .param("revokedAt", key.revokedAt() == null ? null : Timestamp.from(key.revokedAt()))
                .update();
    }

    @Override
    public Optional<ApiKey> findByPrefix(String prefix) {
        return jdbc.sql("""
                        SELECT id, key_prefix, key_hash, created_at, revoked_at
                        FROM identity.api_keys WHERE key_prefix = :prefix
                        """)
                .param("prefix", prefix)
                .query(JdbcApiKeyRepository::toApiKey)
                .optional();
    }

    private static ApiKey toApiKey(ResultSet row, int rowNumber) throws SQLException {
        return new ApiKey(
                row.getObject("id", UUID.class),
                row.getString("key_prefix"),
                row.getString("key_hash"),
                row.getTimestamp("created_at").toInstant(),
                instantOrNull(row.getTimestamp("revoked_at")));
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
