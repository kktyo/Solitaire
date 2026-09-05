package com.solitaire.domain.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Rules {

    private Rules() {}

    public static boolean isLegal(Board board, Move move) {
        return apply(board, move) instanceof ApplyResult.Ok;
    }

    public static ApplyResult apply(Board board, Move move) {
        if (move == null || move.type() == null) {
            return ApplyResult.illegal();
        }
        Board next = board.copy();
        return switch (move.type()) {
            case DRAW -> applyDraw(next);
            case RECYCLE -> applyRecycle(next);
            case MOVE -> applyMove(next, move);
        };
    }

    public static int movableTableauCount(Board board, int column, int startIndex) {
        if (column < 0 || column > 6) {
            return 0;
        }
        List<Card> col = board.tableau().get(column);
        if (startIndex < 0 || startIndex >= col.size()) {
            return 0;
        }
        if (!col.get(startIndex).faceUp()) {
            return 0;
        }
        for (int j = startIndex; j < col.size() - 1; j++) {
            if (!BoardInvariant.tableauFollows(col.get(j), col.get(j + 1))) {
                return 0;
            }
        }
        return col.size() - startIndex;
    }

    private static ApplyResult applyDraw(Board board) {
        if (board.stockMut().isEmpty()) {
            return ApplyResult.illegal();
        }
        Card c = board.stockMut().remove(0).withFaceUp(true);
        board.wasteMut().add(c);
        return ApplyResult.ok(board);
    }

    private static ApplyResult applyRecycle(Board board) {
        if (!board.stockMut().isEmpty() || board.wasteMut().isEmpty()) {
            return ApplyResult.illegal();
        }
        List<Card> waste = board.wasteMut();
        List<Card> stock = new ArrayList<>();
        for (int i = waste.size() - 1; i >= 0; i--) {
            stock.add(waste.get(i).withFaceUp(false));
        }
        waste.clear();
        board.stockMut().addAll(stock);
        return ApplyResult.ok(board);
    }

    private static ApplyResult applyMove(Board next, Move move) {
        if (move.count() < 1 || move.from() == null || move.to() == null) {
            return ApplyResult.illegal();
        }
        if (sameLoc(move.from(), move.to())) {
            return ApplyResult.illegal();
        }
        List<Card> taken = take(next, move.from(), move.count());
        if (taken == null) {
            return ApplyResult.illegal();
        }
        if (!canPlace(taken.get(0), next, move.to(), move.count())) {
            return ApplyResult.illegal();
        }
        removeLast(next, move.from(), move.count());
        append(next, move.to(), taken);
        flipTableau(next, move.from());
        return ApplyResult.ok(next);
    }

    private static boolean sameLoc(Location a, Location b) {
        return a.pile() == b.pile() && a.index() == b.index();
    }

    private static List<Card> take(Board board, Location from, int count) {
        return switch (from.pile()) {
            case TABLEAU -> {
                int i = from.index();
                if (i < 0 || i > 6) {
                    yield null;
                }
                List<Card> col = board.tableauCol(i);
                if (col.size() < count) {
                    yield null;
                }
                int start = col.size() - count;
                List<Card> slice = new ArrayList<>(col.subList(start, col.size()));
                if (slice.stream().anyMatch(c -> !c.faceUp())) {
                    yield null;
                }
                if (movableTableauCount(board, i, start) != count) {
                    yield null;
                }
                yield slice;
            }
            case WASTE -> {
                if (count != 1 || board.wasteMut().isEmpty()) {
                    yield null;
                }
                yield List.of(board.wasteMut().get(board.wasteMut().size() - 1));
            }
            case FOUNDATION -> {
                if (count != 1) {
                    yield null;
                }
                Suit s = suitOf(from.index());
                if (s == null) {
                    yield null;
                }
                List<Card> col = board.foundation(s);
                if (col.isEmpty()) {
                    yield null;
                }
                yield List.of(col.get(col.size() - 1));
            }
            case STOCK -> null;
        };
    }

    private static boolean canPlace(Card first, Board board, Location to, int count) {
        return switch (to.pile()) {
            case TABLEAU -> {
                int i = to.index();
                if (i < 0 || i > 6) {
                    yield false;
                }
                List<Card> col = board.tableauCol(i);
                if (col.isEmpty()) {
                    yield first.rank() == 13;
                }
                Card top = col.get(col.size() - 1);
                yield first.red() != top.red() && first.rank() == top.rank() - 1;
            }
            case FOUNDATION -> {
                if (count != 1) {
                    yield false;
                }
                Suit s = suitOf(to.index());
                if (s == null || first.suit() != s) {
                    yield false;
                }
                List<Card> col = board.foundation(s);
                if (col.isEmpty()) {
                    yield first.rank() == 1;
                }
                Card top = col.get(col.size() - 1);
                yield first.rank() == top.rank() + 1;
            }
            case STOCK, WASTE -> false;
        };
    }

    private static void removeLast(Board board, Location from, int count) {
        switch (from.pile()) {
            case TABLEAU -> {
                List<Card> col = board.tableauCol(from.index());
                for (int n = 0; n < count; n++) {
                    col.remove(col.size() - 1);
                }
            }
            case WASTE -> board.wasteMut().remove(board.wasteMut().size() - 1);
            case FOUNDATION -> {
                List<Card> col = board.foundation(Objects.requireNonNull(suitOf(from.index())));
                col.remove(col.size() - 1);
            }
            case STOCK -> {
                throw new IllegalStateException("stock move");
            }
        }
    }

    private static void append(Board board, Location to, List<Card> cards) {
        switch (to.pile()) {
            case TABLEAU -> board.tableauCol(to.index()).addAll(cards);
            case FOUNDATION -> board.foundation(Objects.requireNonNull(suitOf(to.index()))).addAll(cards);
            case STOCK, WASTE -> throw new IllegalStateException("cannot move to stock/waste");
        }
    }

    private static void flipTableau(Board board, Location from) {
        if (from.pile() != Pile.TABLEAU) {
            return;
        }
        List<Card> col = board.tableauCol(from.index());
        if (!col.isEmpty() && !col.get(col.size() - 1).faceUp()) {
            Card last = col.get(col.size() - 1);
            col.set(col.size() - 1, last.withFaceUp(true));
        }
    }

    private static Suit suitOf(int index) {
        if (index < 0 || index > 3) {
            return null;
        }
        return Suit.ofIndex(index);
    }
}
