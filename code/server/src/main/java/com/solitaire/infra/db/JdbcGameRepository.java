package com.solitaire.infra.db;

import com.solitaire.application.GameRecord;
import com.solitaire.application.GameRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcGameRepository implements GameRepository {

    private final JdbcTemplate jdbc;
    private final BoardJsonCodec codec;

    public JdbcGameRepository(JdbcTemplate jdbc, BoardJsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public void insert(GameRecord game) {
        jdbc.update(
                """
                INSERT INTO games (id, user_id, status, version, move_count, elapsed_ms, timing_started_at,
                  board_json, started_at, updated_at, cleared_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                game.getId(),
                game.getUserId(),
                game.getStatus(),
                game.getVersion(),
                game.getMoveCount(),
                game.getElapsedMs(),
                ts(game.getTimingStartedAt()),
                codec.writeBoard(game.getBoard()),
                ts(game.getStartedAt()),
                ts(game.getUpdatedAt()),
                ts(game.getClearedAt()));
    }

    @Override
    public Optional<GameRecord> findByIdForUpdate(UUID id) {
        return one(
                """
                SELECT * FROM games WITH (UPDLOCK, ROWLOCK) WHERE id = ?
                """,
                id);
    }

    @Override
    public Optional<GameRecord> findInProgressByUserForUpdate(UUID userId) {
        return one(
                """
                SELECT * FROM games WITH (UPDLOCK, ROWLOCK)
                WHERE user_id = ? AND status = N'IN_PROGRESS'
                """,
                userId);
    }

    @Override
    public Optional<GameRecord> findInProgressByUser(UUID userId) {
        return one(
                """
                SELECT * FROM games WHERE user_id = ? AND status = N'IN_PROGRESS'
                """,
                userId);
    }

    @Override
    public void update(GameRecord game) {
        jdbc.update(
                """
                UPDATE games SET status=?, version=?, move_count=?, elapsed_ms=?, timing_started_at=?,
                  board_json=?, updated_at=?, cleared_at=? WHERE id=?
                """,
                game.getStatus(),
                game.getVersion(),
                game.getMoveCount(),
                game.getElapsedMs(),
                ts(game.getTimingStartedAt()),
                codec.writeBoard(game.getBoard()),
                ts(game.getUpdatedAt()),
                ts(game.getClearedAt()),
                game.getId());
    }

    private Optional<GameRecord> one(String sql, Object arg) {
        List<GameRecord> rows = jdbc.query(sql, (rs, i) -> map(rs), arg);
        return rows.stream().findFirst();
    }

    private GameRecord map(ResultSet rs) throws SQLException {
        GameRecord g = new GameRecord();
        g.setId(rs.getObject("id", UUID.class));
        g.setUserId(rs.getObject("user_id", UUID.class));
        g.setStatus(rs.getString("status"));
        g.setVersion(rs.getInt("version"));
        g.setMoveCount(rs.getInt("move_count"));
        g.setElapsedMs(rs.getLong("elapsed_ms"));
        var timing = rs.getTimestamp("timing_started_at");
        g.setTimingStartedAt(timing == null ? null : timing.toInstant());
        g.setBoard(codec.readBoard(rs.getString("board_json")));
        g.setStartedAt(rs.getTimestamp("started_at").toInstant());
        g.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        var cleared = rs.getTimestamp("cleared_at");
        g.setClearedAt(cleared == null ? null : cleared.toInstant());
        return g;
    }

    private static Timestamp ts(java.time.Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
