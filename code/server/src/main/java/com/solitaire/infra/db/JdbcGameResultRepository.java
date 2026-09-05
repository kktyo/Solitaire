package com.solitaire.infra.db;

import com.solitaire.application.GameResultRow;
import com.solitaire.application.GameResultRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcGameResultRepository implements GameResultRepository {

    private final JdbcTemplate jdbc;

    public JdbcGameResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(GameResultRow row) {
        jdbc.update(
                """
                INSERT INTO game_results (game_id, user_id, move_count, elapsed_ms, cleared_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                row.gameId(),
                row.userId(),
                row.moveCount(),
                row.elapsedMs(),
                Timestamp.from(row.clearedAt()));
    }

    @Override
    public List<GameResultRow> listByUserOrderByClearedAtDesc(UUID userId, int limit) {
        return jdbc.query(
                """
                SELECT TOP %d game_id, user_id, move_count, elapsed_ms, cleared_at
                FROM game_results WHERE user_id = ? ORDER BY cleared_at DESC
                """.formatted(limit),
                (rs, i) -> new GameResultRow(
                        rs.getObject("game_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getInt("move_count"),
                        rs.getLong("elapsed_ms"),
                        rs.getTimestamp("cleared_at").toInstant()),
                userId);
    }
}
