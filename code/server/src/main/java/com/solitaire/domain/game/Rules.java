package com.solitaire.domain.game;

import java.util.ArrayList;
import java.util.List;

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
                if (from.index() < 0 || from.index() > 3) {
                    yield null;
                }
                List<Card> col = board.foundationSlot(from.index());
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
                int i = to.index();
                if (i < 0 || i > 3) {
                    yield false;
                }
                List<Card> col = board.foundationSlot(i);
                if (col.isEmpty()) {
                    if (first.rank() != 1) {
                        yield false;
                    }
                    for (int s = 0; s < 4; s++) {
                        if (s == i) {
                            continue;
                        }
                        List<Card> other = board.foundationSlot(s);
                        if (!other.isEmpty() && other.get(0).suit() == first.suit()) {
                            yield false;
                        }
                    }
                    yield true;
                }
                Card top = col.get(col.size() - 1);
                yield first.suit() == top.suit() && first.rank() == top.rank() + 1;
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
                List<Card> col = board.foundationSlot(from.index());
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
            case FOUNDATION -> board.foundationSlot(to.index()).addAll(cards);
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

    public static boolean isCleared(Board board) {
        for (int i = 0; i < 4; i++) {
            if (board.foundationSlot(i).size() != 13) {
                return false;
            }
        }
        return true;
    }

    public static List<Move> legalRelocates(Board board) {
        List<Move> out = new ArrayList<>();
        for (int col = 0; col < 7; col++) {
            List<Card> cards = board.tableauCol(col);
            for (int i = 0; i < cards.size(); i++) {
                int count = movableTableauCount(board, col, i);
                if (count == 0) {
                    continue;
                }
                Location from = new Location(Pile.TABLEAU, col);
                addRelocates(board, from, count, out);
            }
        }
        if (!board.wasteMut().isEmpty()) {
            addRelocates(board, new Location(Pile.WASTE, 0), 1, out);
        }
        for (int f = 0; f < 4; f++) {
            if (!board.foundationSlot(f).isEmpty()) {
                addRelocates(board, new Location(Pile.FOUNDATION, f), 1, out);
            }
        }
        return out;
    }

    private static void addRelocates(Board board, Location from, int count, List<Move> out) {
        for (int to = 0; to < 7; to++) {
            if (from.pile() == Pile.TABLEAU && from.index() == to) {
                continue;
            }
            Move m = Move.relocate(from, new Location(Pile.TABLEAU, to), count);
            if (isLegal(board, m)) {
                out.add(m);
            }
        }
        if (count == 1) {
            for (int f = 0; f < 4; f++) {
                if (from.pile() == Pile.FOUNDATION && from.index() == f) {
                    continue;
                }
                Move m = Move.relocate(from, new Location(Pile.FOUNDATION, f), 1);
                if (isLegal(board, m)) {
                    out.add(m);
                }
            }
        }
    }

    public static boolean isStalemate(Board board) {
        if (isCleared(board)) {
            return false;
        }
        if (!legalRelocates(board).isEmpty()) {
            return false;
        }
        if (board.stockMut().isEmpty() && board.wasteMut().isEmpty()) {
            return true;
        }
        Board cur = board.copy();
        var seen = new java.util.HashSet<String>();
        while (true) {
            if (!seen.add(stockWasteKey(cur))) {
                return true;
            }
            if (wasteTopPlayable(cur)) {
                return false;
            }
            ApplyResult next;
            if (!cur.stockMut().isEmpty()) {
                next = apply(cur, Move.draw());
            } else if (!cur.wasteMut().isEmpty()) {
                next = apply(cur, Move.recycle());
            } else {
                return true;
            }
            if (!(next instanceof ApplyResult.Ok ok)) {
                return true;
            }
            cur = ok.board();
        }
    }

    static boolean wasteTopPlayable(Board board) {
        if (board.wasteMut().isEmpty()) {
            return false;
        }
        Location from = new Location(Pile.WASTE, 0);
        for (int to = 0; to < 7; to++) {
            if (isLegal(board, Move.relocate(from, new Location(Pile.TABLEAU, to), 1))) {
                return true;
            }
        }
        for (int f = 0; f < 4; f++) {
            if (isLegal(board, Move.relocate(from, new Location(Pile.FOUNDATION, f), 1))) {
                return true;
            }
        }
        return false;
    }

    static String stockWasteKey(Board board) {
        StringBuilder sb = new StringBuilder();
        for (Card c : board.stockMut()) {
            sb.append(c.id());
        }
        sb.append('/');
        for (Card c : board.wasteMut()) {
            sb.append(c.id());
        }
        return sb.toString();
    }

    static String fingerprint(Board board) {
        StringBuilder sb = new StringBuilder(256);
        for (int i = 0; i < 7; i++) {
            for (Card c : board.tableauCol(i)) {
                sb.append(c.id()).append(c.faceUp() ? '+' : '-');
            }
            sb.append('|');
        }
        for (int i = 0; i < 4; i++) {
            for (Card c : board.foundationSlot(i)) {
                sb.append(c.id());
            }
            sb.append('|');
        }
        sb.append(stockWasteKey(board));
        return sb.toString();
    }

    public static Move nextAutoMove(Board board) {
        for (Move m : legalRelocates(board)) {
            if (m.to() != null && m.to().pile() == Pile.FOUNDATION) {
                return m;
            }
        }
        if (!board.stockMut().isEmpty()) {
            return Move.draw();
        }
        if (!board.wasteMut().isEmpty()) {
            return Move.recycle();
        }
        return null;
    }

    public static boolean tableauAllFaceUp(Board board) {
        for (int i = 0; i < 7; i++) {
            for (Card c : board.tableauCol(i)) {
                if (!c.faceUp()) {
                    return false;
                }
            }
        }
        return true;
    }
}
