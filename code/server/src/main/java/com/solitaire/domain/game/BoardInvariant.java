package com.solitaire.domain.game;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BoardInvariant {

    private BoardInvariant() {}

    public static void assertPartial(Board board) {
        if (board.tableau().size() != 7) {
            throw new IllegalStateException("tableau must have 7 columns");
        }
        if (board.foundations().size() != 4) {
            throw new IllegalStateException("foundations must have 4 slots");
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
        Set<Suit> used = new HashSet<>();
        for (List<Card> col : board.foundations()) {
            for (int i = 0; i < col.size(); i++) {
                Card c = col.get(i);
                if (!c.faceUp()) {
                    throw new IllegalStateException("bad foundation");
                }
                if (i == 0) {
                    if (c.rank() != 1) {
                        throw new IllegalStateException("foundation must start with A");
                    }
                    if (!used.add(c.suit())) {
                        throw new IllegalStateException("duplicate foundation suit");
                    }
                } else if (c.suit() != col.get(0).suit() || c.rank() != col.get(i - 1).rank() + 1) {
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
        for (List<Card> col : board.foundations()) {
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
