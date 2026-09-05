package com.solitaire.application;

import com.solitaire.domain.game.Board;
import com.solitaire.domain.game.Card;
import com.solitaire.domain.game.Location;
import com.solitaire.domain.game.Move;
import com.solitaire.domain.game.Pile;
import com.solitaire.domain.game.Suit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameApplicationServiceTest {

    private FakeGames games;
    private FakeMoves moves;
    private FakeResults results;
    private MutableClock clock;
    private GameApplicationService service;
    private UUID user;

    @BeforeEach
    void setUp() {
        games = new FakeGames();
        moves = new FakeMoves();
        results = new FakeResults();
        clock = new MutableClock(Instant.parse("2026-09-06T00:00:00Z"));
        service = new GameApplicationService(games, moves, results, clock, new SecureRandom(), m -> "{}");
        user = UUID.randomUUID();
    }

    @Test
    void versionConflictDoesNotUpdate() {
        GameRecord game = service.create(user, false);
        int version = game.getVersion();
        Board before = game.getBoard();
        game.setVersion(version + 5);
        games.update(game);
        AppException ex = assertThrows(
                AppException.class,
                () -> service.applyMove(user, game.getId(), version, Move.draw()));
        assertEquals(409, ex.status());
        assertEquals(version + 5, games.findByIdForUpdate(game.getId()).orElseThrow().getVersion());
        assertEquals(before.stock().size(), games.findByIdForUpdate(game.getId()).orElseThrow().getBoard().stock().size());
    }

    @Test
    void clearInsertsSingleResult() {
        GameRecord game = almostClear(user);
        games.insert(game);
        Move move = Move.relocate(new Location(Pile.TABLEAU, 0), new Location(Pile.FOUNDATION, 0), 1);
        GameRecord after = service.applyMove(user, game.getId(), 0, move);
        assertEquals("CLEARED", after.getStatus());
        assertEquals(1, results.rows.size());
        assertEquals(1, after.getMoveCount());
        assertEquals(after.getElapsedMs(), results.rows.get(0).elapsedMs());
    }

    @Test
    void undoRestoresMoveCountNotElapsed() {
        GameRecord game = service.create(user, false);
        clock.plusSeconds(10);
        service.applyMove(user, game.getId(), 0, Move.draw());
        GameRecord mid = games.findByIdForUpdate(game.getId()).orElseThrow();
        long elapsed = mid.getElapsedMs();
        int version = mid.getVersion();
        clock.plusSeconds(5);
        GameRecord undone = service.undo(user, game.getId(), version);
        assertEquals(0, undone.getMoveCount());
        assertTrue(undone.getElapsedMs() >= elapsed);
        assertEquals(2, undone.getVersion());
        assertTrue(undone.getElapsedMs() >= 10_000);
    }

    private GameRecord almostClear(UUID userId) {
        List<List<Card>> tableau = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            tableau.add(new ArrayList<>());
        }
        tableau.get(0).add(new Card("KS", true));
        Map<Suit, List<Card>> f = new EnumMap<>(Suit.class);
        for (Suit s : Suit.values()) {
            List<Card> col = new ArrayList<>();
            int max = s == Suit.S ? 12 : 13;
            for (int r = 1; r <= max; r++) {
                col.add(new Card(RankLabel.label(r) + s.name(), true));
            }
            f.put(s, col);
        }
        Board board = new Board(tableau, f, List.of(), List.of());
        GameRecord g = new GameRecord();
        g.setId(UUID.randomUUID());
        g.setUserId(userId);
        g.setStatus(GameApplicationService.IN_PROGRESS);
        g.setVersion(0);
        g.setMoveCount(0);
        g.setElapsedMs(0);
        g.setTimingStartedAt(clock.instant());
        g.setBoard(board);
        g.setStartedAt(clock.instant());
        g.setUpdatedAt(clock.instant());
        return g;
    }

    static final class RankLabel {
        static String label(int r) {
            return com.solitaire.domain.game.Rank.label(r);
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void plusSeconds(long s) {
            now = now.plusSeconds(s);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class FakeGames implements GameRepository {
        final Map<UUID, GameRecord> byId = new HashMap<>();

        @Override
        public void insert(GameRecord game) {
            byId.put(game.getId(), copy(game));
        }

        @Override
        public Optional<GameRecord> findByIdForUpdate(UUID id) {
            return Optional.ofNullable(byId.get(id)).map(FakeGames::copy);
        }

        @Override
        public Optional<GameRecord> findInProgressByUserForUpdate(UUID userId) {
            return findInProgressByUser(userId);
        }

        @Override
        public Optional<GameRecord> findInProgressByUser(UUID userId) {
            return byId.values().stream()
                    .filter(g -> g.getUserId().equals(userId) && "IN_PROGRESS".equals(g.getStatus()))
                    .findFirst()
                    .map(FakeGames::copy);
        }

        @Override
        public void update(GameRecord game) {
            byId.put(game.getId(), copy(game));
        }

        static GameRecord copy(GameRecord g) {
            GameRecord c = new GameRecord();
            c.setId(g.getId());
            c.setUserId(g.getUserId());
            c.setStatus(g.getStatus());
            c.setVersion(g.getVersion());
            c.setMoveCount(g.getMoveCount());
            c.setElapsedMs(g.getElapsedMs());
            c.setTimingStartedAt(g.getTimingStartedAt());
            c.setBoard(g.getBoard().copy());
            c.setStartedAt(g.getStartedAt());
            c.setUpdatedAt(g.getUpdatedAt());
            c.setClearedAt(g.getClearedAt());
            return c;
        }
    }

    static final class FakeMoves implements GameMoveRepository {
        final List<GameMoveRow> rows = new ArrayList<>();
        final AtomicInteger seq = new AtomicInteger();

        @Override
        public void insert(GameMoveRow row) {
            rows.add(row);
        }

        @Override
        public List<GameMoveRow> listByGameOrderBySeqDesc(UUID gameId) {
            return rows.stream()
                    .filter(r -> r.gameId().equals(gameId))
                    .sorted(Comparator.comparingInt(GameMoveRow::seq).reversed())
                    .toList();
        }

        @Override
        public int nextSeq(UUID gameId) {
            return seq.incrementAndGet();
        }
    }

    static final class FakeResults implements GameResultRepository {
        final List<GameResultRow> rows = new ArrayList<>();

        @Override
        public void insert(GameResultRow row) {
            rows.add(row);
        }

        @Override
        public List<GameResultRow> listByUserOrderByClearedAtDesc(UUID userId, int limit) {
            return rows.stream().filter(r -> r.userId().equals(userId)).limit(limit).toList();
        }
    }
}
