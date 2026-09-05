package com.solitaire.infra.db;

import com.solitaire.application.GameMoveRow;
import com.solitaire.application.GameMoveRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcGameMoveRepository implements GameMoveRepository {

    private final JdbcTemplate jdbc;
    private final BoardJsonCodec codec;

    public JdbcGameMoveRepository(JdbcTemplate jdbc, BoardJsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public void insert(GameMoveRow row) {
        jdbc.update(
                """
                INSERT INTO game_moves (game_id, seq, kind, move_json, board_before_json,
                  move_count_before, elapsed_ms_before, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.gameId(),
                row.seq(),
                row.kind(),
                row.moveJson(),
                codec.writeBoard(row.boardBefore()),
                row.moveCountBefore(),
                row.elapsedMsBefore(),
                Timestamp.from(row.createdAt()));
    }

    @Override
    public List<GameMoveRow> listByGameOrderBySeqDesc(UUID gameId) {
        return jdbc.query(
                """
                SELECT game_id, seq, kind, move_json, board_before_json, move_count_before, elapsed_ms_before, created_at
                FROM game_moves WHERE game_id = ? ORDER BY seq DESC
                """,
                (rs, i) -> new GameMoveRow(
                        rs.getObject("game_id", UUID.class),
                        rs.getInt("seq"),
                        rs.getString("kind"),
                        rs.getString("move_json"),
                        codec.readBoard(rs.getString("board_before_json")),
                        rs.getInt("move_count_before"),
                        rs.getLong("elapsed_ms_before"),
                        rs.getTimestamp("created_at").toInstant()),
                gameId);
    }

    @Override
    public int nextSeq(UUID gameId) {
        Integer max = jdbc.queryForObject(
                "SELECT MAX(seq) FROM game_moves WHERE game_id = ?", Integer.class, gameId);
        return (max == null ? 0 : max) + 1;
    }
}
