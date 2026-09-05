package com.solitaire.application;

import com.solitaire.domain.game.Board;

import java.time.Instant;
import java.util.UUID;

public record GameMoveRow(
        UUID gameId,
        int seq,
        String kind,
        String moveJson,
        Board boardBefore,
        int moveCountBefore,
        long elapsedMsBefore,
        Instant createdAt) {}
