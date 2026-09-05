package com.solitaire.domain.game;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BoardInvariant {

    private BoardInvariant() {}

    public static void assertPartial(Board board) {
        if (board.tableau().size() != 7) {
            throw new IllegalStateException("tableau must have 7 columns");
        }
        for (Suit s : Suit.values()) {
            if (!board.foundations().containsKey(s)) {
                throw new IllegalStateException("missing foundation " + s);
            }
        }
        for (Card c : board.stock()) {
            if (c.faceUp()) {
                throw new IllegalStateException("stock must be face down");
            }
        }
        for (Card c : board.waste()) {
            if (!c.faceUp()) {
                throw new IllegalStateException("waste must be face up");
            }
        }
        for (Map.Entry<Suit, List<Card>> e : board.foundations().entrySet()) {
            List<Card> col = e.getValue();
            for (int i = 0; i < col.size(); i++) {
                Card c = col.get(i);
                if (!c.faceUp() || c.suit() != e.getKey()) {
                    throw new IllegalStateException("bad foundation");
                }
                if (i == 0 && c.rank() != 1) {
                    throw new IllegalStateException("foundation must start with A");
                }
                if (i > 0 && c.rank() != col.get(i - 1).rank() + 1) {
                    throw new IllegalStateException("foundation rank");
                }
            }
        }
        for (List<Card> col : board.tableau()) {
            boolean seenFace = false;
            for (int i = 0; i < col.size(); i++) {
                Card c = col.get(i);
                if (c.faceUp()) {
                    seenFace = true;
                } else if (seenFace) {
                    throw new IllegalStateException("face down above face up");
                }
            }
            int firstUp = -1;
            for (int i = 0; i < col.size(); i++) {
                if (col.get(i).faceUp()) {
                    firstUp = i;
                    break;
                }
            }
            if (firstUp >= 0) {
                for (int j = firstUp; j < col.size() - 1; j++) {
                    if (!tableauFollows(col.get(j), col.get(j + 1))) {
                        throw new IllegalStateException("tableau sequence");
                    }
                }
            }
        }
    }

    public static void assertFullDeck(Board board) {
        assertPartial(board);
        Set<String> ids = new HashSet<>();
        for (List<Card> col : board.tableau()) {
            for (Card c : col) {
                ids.add(c.id());
            }
        }
        for (List<Card> col : board.foundations().values()) {
            for (Card c : col) {
                ids.add(c.id());
            }
        }
        for (Card c : board.stock()) {
            ids.add(c.id());
        }
        for (Card c : board.waste()) {
            ids.add(c.id());
        }
        if (ids.size() != 52) {
            throw new IllegalStateException("expected 52 unique cards, got " + ids.size());
        }
        for (Suit s : Suit.values()) {
            for (String r : Rank.labels()) {
                String id = r + s.name();
                if (!ids.contains(id)) {
                    throw new IllegalStateException("missing " + id);
                }
            }
        }
    }

    static boolean tableauFollows(Card lower, Card upper) {
        return lower.red() != upper.red() && upper.rank() == lower.rank() - 1 && upper.faceUp() && lower.faceUp();
    }
}
