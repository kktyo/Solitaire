package com.solitaire.infra.db;

import com.solitaire.application.RefreshTokenRecord;
import com.solitaire.application.RefreshTokenRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final JdbcTemplate jdbc;

    public JdbcRefreshTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(RefreshTokenRecord token) {
        jdbc.update(
                """
                INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                token.id(),
                token.userId(),
                token.tokenHash(),
                Timestamp.from(token.expiresAt()),
                token.revokedAt() == null ? null : Timestamp.from(token.revokedAt()),
                Timestamp.from(token.createdAt()));
    }

    @Override
    public Optional<RefreshTokenRecord> findById(UUID id) {
        List<RefreshTokenRecord> rows = jdbc.query(
                """
                SELECT id, user_id, token_hash, expires_at, revoked_at, created_at
                FROM refresh_tokens WHERE id = ?
                """,
                (rs, i) -> new RefreshTokenRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("token_hash"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()),
                id);
        return rows.stream().findFirst();
    }

    @Override
    public void revoke(UUID id, Instant at) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = ? WHERE id = ?", Timestamp.from(at), id);
    }
}
