package com.solitaire.infra.db;

import com.solitaire.application.UserRecord;
import com.solitaire.application.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUserRepository implements UserRepository {

    private final JdbcTemplate jdbc;

    public JdbcUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UserRecord user) {
        jdbc.update(
                """
                INSERT INTO users (id, email, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                user.id(),
                user.email(),
                user.passwordHash(),
                ts(user.createdAt()),
                ts(user.updatedAt()));
    }

    @Override
    public Optional<UserRecord> findByNormalizedEmail(String email) {
        List<UserRecord> rows = jdbc.query(
                "SELECT id, email, password_hash, created_at, updated_at FROM users WHERE email = ?",
                (rs, i) -> map(rs),
                email);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<UserRecord> findById(UUID id) {
        List<UserRecord> rows = jdbc.query(
                "SELECT id, email, password_hash, created_at, updated_at FROM users WHERE id = ?",
                (rs, i) -> map(rs),
                id);
        return rows.stream().findFirst();
    }

    private static UserRecord map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserRecord(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }
}
