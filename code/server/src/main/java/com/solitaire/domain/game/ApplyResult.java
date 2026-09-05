package com.solitaire.domain.game;

public sealed interface ApplyResult permits ApplyResult.Ok, ApplyResult.Illegal {

    record Ok(Board board, boolean cleared) implements ApplyResult {}

    record Illegal() implements ApplyResult {}

    static ApplyResult illegal() {
        return new Illegal();
    }

    static ApplyResult ok(Board board) {
        boolean cleared = board.foundations().values().stream().allMatch(c -> c.size() == 13);
        return new Ok(board, cleared);
    }
}
