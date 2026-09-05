package com.solitaire.api.game;

import com.solitaire.api.GameResponses;
import com.solitaire.application.GameApplicationService;
import com.solitaire.application.GameMoveRepository;
import com.solitaire.application.GameRecord;
import com.solitaire.domain.game.Move;
import com.solitaire.infra.db.BoardJsonCodec;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GameController {

    private final GameApplicationService games;
    private final GameMoveRepository moves;
    private final BoardJsonCodec codec;

    public GameController(GameApplicationService games, GameMoveRepository moves, BoardJsonCodec codec) {
        this.games = games;
        this.moves = moves;
        this.codec = codec;
    }

    @GetMapping("/games/current")
    public Map<String, Object> current() {
        return respond(games.getCurrent(GameResponses.currentUser()));
    }

    @PostMapping("/games/{gameId}/resume")
    public Map<String, Object> resume(@PathVariable UUID gameId) {
        return respond(games.resume(GameResponses.currentUser(), gameId));
    }

    @PostMapping("/games/{gameId}/pause")
    public Map<String, Object> pause(@PathVariable UUID gameId) {
        return respond(games.pause(GameResponses.currentUser(), gameId));
    }

    @PostMapping("/games")
    public ResponseEntity<Map<String, Object>> create(@RequestBody(required = false) CreateGameRequest body) {
        boolean abandon = body != null && Boolean.TRUE.equals(body.abandonExisting());
        GameRecord game = games.create(GameResponses.currentUser(), abandon);
        return ResponseEntity.status(HttpStatus.CREATED).body(respond(game));
    }

    @PostMapping("/games/{gameId}/moves")
    public Map<String, Object> move(@PathVariable UUID gameId, @RequestBody MoveRequest body) {
        if (body == null || body.move() == null) {
            throw com.solitaire.application.AppException.validation("move が不正です。");
        }
        Move move;
        try {
            move = codec.readMove(body.move());
        } catch (RuntimeException e) {
            throw com.solitaire.application.AppException.validation("move が不正です。");
        }
        return respond(games.applyMove(GameResponses.currentUser(), gameId, body.version(), move));
    }

    @PostMapping("/games/{gameId}/undo")
    public Map<String, Object> undo(@PathVariable UUID gameId, @RequestBody UndoRequest body) {
        return respond(games.undo(GameResponses.currentUser(), gameId, body.version()));
    }

    @GetMapping("/results/me")
    public Map<String, Object> results(@RequestParam(defaultValue = "1") int limit) {
        var items = games.listMyResults(GameResponses.currentUser(), limit).stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("gameId", r.gameId().toString());
                    m.put("moveCount", r.moveCount());
                    m.put("elapsedMs", r.elapsedMs());
                    m.put("clearedAt", GameResponses.ts(r.clearedAt()));
                    return m;
                })
                .toList();
        return Map.of("items", items);
    }

    private Map<String, Object> respond(GameRecord g) {
        boolean canUndo = "IN_PROGRESS".equals(g.getStatus())
                && GameApplicationService.findUndoTarget(moves.listByGameOrderBySeqDesc(g.getId())) != null;
        if ("CLEARED".equals(g.getStatus())) {
            canUndo = false;
        }
        return GameResponses.of(g, canUndo, codec);
    }

    public record CreateGameRequest(Boolean abandonExisting) {}

    public record MoveRequest(int version, com.fasterxml.jackson.databind.JsonNode move) {}

    public record UndoRequest(int version) {}
}
