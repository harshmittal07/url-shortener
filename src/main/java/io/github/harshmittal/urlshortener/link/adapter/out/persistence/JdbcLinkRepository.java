package io.github.harshmittal.urlshortener.link.adapter.out.persistence;

import io.github.harshmittal.urlshortener.link.domain.Link;
import io.github.harshmittal.urlshortener.link.domain.LinkRepository;
import io.github.harshmittal.urlshortener.link.domain.LinkStatus;
import io.github.harshmittal.urlshortener.link.domain.ShortCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class JdbcLinkRepository implements LinkRepository {

    private final JdbcClient jdbc;

    public JdbcLinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@code ON CONFLICT (code) DO NOTHING} reports a collision as zero rows. A failed insert would
     * abort the Postgres transaction; this keeps it usable for the retry (plan §1).
     */
    @Override
    public boolean insertIfCodeFree(Link link) {
        int inserted = jdbc.sql("""
                        INSERT INTO link.links (id, code, target_url, owner_key_id, status, created_at)
                        VALUES (:id, :code, :targetUrl, :ownerKeyId, :status, :createdAt)
                        ON CONFLICT (code) DO NOTHING
                        """)
                .param("id", link.id())
                .param("code", link.code().value())
                .param("targetUrl", link.targetUrl())
                .param("ownerKeyId", link.ownerKeyId())
                .param("status", link.status().name())
                .param("createdAt", Timestamp.from(link.createdAt()))
                .update();
        return inserted == 1;
    }

    @Override
    public Optional<Link> findByCode(ShortCode code) {
        return jdbc.sql("""
                        SELECT id, code, target_url, owner_key_id, status, created_at
                        FROM link.links WHERE code = :code
                        """)
                .param("code", code.value())
                .query(JdbcLinkRepository::toLink)
                .optional();
    }

    private static Link toLink(ResultSet row, int rowNumber) throws SQLException {
        return new Link(
                row.getObject("id", UUID.class),
                new ShortCode(row.getString("code")),
                row.getString("target_url"),
                row.getObject("owner_key_id", UUID.class),
                LinkStatus.valueOf(row.getString("status")),
                row.getTimestamp("created_at").toInstant());
    }
}
