package com.solitaire.domain.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SolverTest {

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void winnableBoardIsSolvable() {
        Board board = Deal.winnableBoard(new Random(1));
        BoardInvariant.assertFullDeck(board);
        assertTrue(Solver.canClear(board, Deal.WINNABLE_FALLBACK_NODES));
    }
}
