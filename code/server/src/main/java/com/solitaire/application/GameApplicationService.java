package com.solitaire.application;

import com.solitaire.domain.game.ApplyResult;
import com.solitaire.domain.game.Board;
import com.solitaire.domain.game.BoardInvariant;
import com.solitaire.domain.game.Deal;
import com.solitaire.domain.game.Move;
import com.solitaire.domain.game.Rules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class GameApplicationService {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String CLEARED = "CLEARED";
    public static final String ABANDONED = "ABANDONED";

    private final GameRepository games;
    private final GameMoveRepository moves;
    private final GameResultRepository results;
    private final Clock clock;
    private final SecureRandom random;
    private final MoveJson moveJson;

    public GameApplicationService(
            GameRepository games,
            GameMoveRepository moves,
            GameResultRepository results,
            Clock clock,
            SecureRandom random,
            MoveJson moveJson) {
        this.games = games;
        this.moves = moves;
        this.results = results;
        this.clock = clock;
        this.random = random;
        this.moveJson = moveJson;
    }

    public GameRecord getCurrent(UUID userId) {
        return games.findInProgressByUser(userId)
                .orElseThrow(() -> AppException.notFound("進行中の対局はありません。"));
    }

    public GameRecord resume(UUID userId, UUID gameId) {
        GameRecord game = loadOwnedInProgress(userId, gameId);
        Instant now = clock.instant();
        if (game.getTimingStartedAt() == null) {
            game.setTimingStartedAt(now);
        }
        game.setUpdatedAt(now);
        games.update(game);
        return game;
    }

    public GameRecord pause(UUID userId, UUID gameId) {
        GameRecord game = loadOwnedInProgress(userId, gameId);
        Instant now = clock.instant();
        accrue(game, now, false);
        game.setUpdatedAt(now);
        games.update(game);
        return game;
    }

    public GameRecord create(UUID userId, boolean abandonExisting) {
        Instant now = clock.instant();
        var existing = games.findInProgressByUserForUpdate(userId);
        if (existing.isPresent()) {
            if (!abandonExisting) {
                throw new AppException(
                        409,
                        "GAME_IN_PROGRESS",
                        "進行中の対局があります。",
                        Map.of("game", existing.get()));
            }
            GameRecord old = existing.get();
            old.setStatus(ABANDONED);
            old.setTimingStartedAt(null);
            old.setUpdatedAt(now);
            games.update(old);
        }
        Board board = Deal.initialBoard(random);
        BoardInvariant.assertFullDeck(board);
        GameRecord game = new GameRecord();
        game.setId(UUID.randomUUID());
        game.setUserId(userId);
        game.setStatus(IN_PROGRESS);
        game.setVersion(0);
        game.setMoveCount(0);
        game.setElapsedMs(0);
        game.setTimingStartedAt(now);
        game.setBoard(board);
        game.setStartedAt(now);
        game.setUpdatedAt(now);
        games.insert(game);
        return game;
    }

    public GameRecord applyMove(UUID userId, UUID gameId, int version, Move move) {
        GameRecord game = loadOwned(userId, gameId);
        requireInProgress(game);
        if (game.getVersion() != version) {
            throw conflict(game);
        }
        Instant now = clock.instant();
        Board before = game.getBoard();
        int moveCountBefore = game.getMoveCount();
        long elapsedBefore = game.getElapsedMs();
        ApplyResult result = Rules.apply(before, move);
        if (!(result instanceof ApplyResult.Ok ok)) {
            throw invalid(game);
        }
        boolean continueTiming = !ok.cleared();
        accrue(game, now, continueTiming);
        game.setBoard(ok.board());
        game.setMoveCount(game.getMoveCount() + 1);
        game.setVersion(game.getVersion() + 1);
        game.setUpdatedAt(now);
        if (ok.cleared()) {
            game.setStatus(CLEARED);
            game.setClearedAt(now);
            game.setTimingStartedAt(null);
        }
        games.update(game);
        moves.insert(new GameMoveRow(
                game.getId(),
                moves.nextSeq(game.getId()),
                "APPLY",
                moveJson.write(move),
                before,
                moveCountBefore,
                elapsedBefore,
                now));
        if (ok.cleared()) {
            results.insert(new GameResultRow(
                    game.getId(), userId, game.getMoveCount(), game.getElapsedMs(), now));
        }
        return game;
    }

    public GameRecord undo(UUID userId, UUID gameId, int version) {
        GameRecord game = loadOwned(userId, gameId);
        requireInProgress(game);
        if (game.getVersion() != version) {
            throw conflict(game);
        }
        List<GameMoveRow> history = moves.listByGameOrderBySeqDesc(game.getId());
        GameMoveRow target = findUndoTarget(history);
        if (target == null) {
            throw new AppException(422, "CANNOT_UNDO", "アンドゥできません。", Map.of());
        }
        Instant now = clock.instant();
        Board boardBeforeUndo = game.getBoard();
        int countBeforeUndo = game.getMoveCount();
        long elapsedBeforeUndo = game.getElapsedMs();
        accrue(game, now, true);
        game.setBoard(target.boardBefore());
        game.setMoveCount(target.moveCountBefore());
        game.setVersion(game.getVersion() + 1);
        game.setUpdatedAt(now);
        games.update(game);
        moves.insert(new GameMoveRow(
                game.getId(),
                moves.nextSeq(game.getId()),
                "UNDO",
                null,
                boardBeforeUndo,
                countBeforeUndo,
                elapsedBeforeUndo,
                now));
        return game;
    }

    public List<GameResultRow> listMyResults(UUID userId, int limit) {
        int n = limit <= 0 ? 1 : Math.min(limit, 20);
        return results.listByUserOrderByClearedAtDesc(userId, n);
    }

    public static GameMoveRow findUndoTarget(List<GameMoveRow> newestFirst) {
        int depth = 0;
        for (GameMoveRow row : newestFirst) {
            if ("UNDO".equals(row.kind())) {
                depth++;
            } else if ("APPLY".equals(row.kind())) {
                if (depth == 0) {
                    return row;
                }
                depth--;
            }
        }
        return null;
    }

    public static void accrue(GameRecord game, Instant now, boolean continueTiming) {
        if (game.getTimingStartedAt() != null) {
            long delta = ChronoUnit.MILLIS.between(game.getTimingStartedAt(), now);
            game.setElapsedMs(game.getElapsedMs() + Math.max(0, delta));
        }
        game.setTimingStartedAt(continueTiming ? now : null);
    }

    private GameRecord loadOwned(UUID userId, UUID gameId) {
        GameRecord game = games.findByIdForUpdate(gameId)
                .orElseThrow(() -> AppException.notFound("対局が見つかりません。"));
        if (!game.getUserId().equals(userId)) {
            throw AppException.forbidden();
        }
        return game;
    }

    private GameRecord loadOwnedInProgress(UUID userId, UUID gameId) {
        GameRecord game = loadOwned(userId, gameId);
        requireInProgress(game);
        return game;
    }

    private static void requireInProgress(GameRecord game) {
        if (!IN_PROGRESS.equals(game.getStatus())) {
            throw new AppException(422, "INVALID_MOVE", "この対局は操作できません。", Map.of("game", game));
        }
    }

    private static AppException conflict(GameRecord game) {
        return new AppException(409, "VERSION_CONFLICT", "盤面が更新されています。", Map.of("game", game));
    }

    private static AppException invalid(GameRecord game) {
        return new AppException(422, "INVALID_MOVE", "その移動はできません。", Map.of("game", game));
    }

    public interface MoveJson {
        String write(Move move);
    }
}
