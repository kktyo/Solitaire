package com.solitaire.application;

import java.time.Instant;
import java.util.UUID;

public record GameResultRow(
        UUID gameId, UUID userId, int moveCount, long elapsedMs, Instant clearedAt) {}
