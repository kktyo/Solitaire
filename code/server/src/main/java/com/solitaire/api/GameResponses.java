package com.solitaire.api;

import com.solitaire.application.GameRecord;
import com.solitaire.domain.game.Rules;
import com.solitaire.infra.db.BoardJsonCodec;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class GameResponses {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private GameResponses() {}

    public static Map<String, Object> of(GameRecord g, boolean canUndo, BoardJsonCodec codec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gameId", g.getId().toString());
        m.put("status", g.getStatus());
        m.put("version", g.getVersion());
        m.put("moveCount", g.getMoveCount());
        m.put("elapsedMs", g.getElapsedMs());
        m.put("timingStartedAt", ts(g.getTimingStartedAt()));
        m.put("canUndo", canUndo);
        boolean stalemate = "IN_PROGRESS".equals(g.getStatus()) && Rules.isStalemate(g.getBoard());
        m.put("stalemate", stalemate);
        m.put("board", codec.toNode(g.getBoard()));
        m.put("startedAt", ts(g.getStartedAt()));
        m.put("updatedAt", ts(g.getUpdatedAt()));
        m.put("clearedAt", ts(g.getClearedAt()));
        return m;
    }

    public static String ts(Instant i) {
        return i == null ? null : ISO.format(i);
    }

    public static UUID currentUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID id)) {
            throw com.solitaire.application.AppException.unauthorized();
        }
        return id;
    }
}
