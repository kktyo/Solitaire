package com.solitaire.domain.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Board {

    private final List<List<Card>> tableau;
    private final List<List<Card>> foundations;
    private final List<Card> stock;
    private final List<Card> waste;

    public Board(
            List<List<Card>> tableau,
            List<List<Card>> foundations,
            List<Card> stock,
            List<Card> waste) {
        this.tableau = copyCols(tableau);
        this.foundations = copyFoundations(foundations);
        this.stock = new ArrayList<>(stock);
        this.waste = new ArrayList<>(waste);
    }

    /** 旧スート固定 JSON / テスト用。枠順は S,H,D,C。 */
    public Board(
            List<List<Card>> tableau,
            Map<Suit, List<Card>> foundationsBySuit,
            List<Card> stock,
            List<Card> waste) {
        this(tableau, fromSuitMap(foundationsBySuit), stock, waste);
    }

    public static List<List<Card>> emptyFoundations() {
        List<List<Card>> f = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            f.add(new ArrayList<>());
        }
        return f;
    }

    public static List<List<Card>> fromSuitMap(Map<Suit, List<Card>> src) {
        List<List<Card>> f = emptyFoundations();
        if (src == null) {
            return f;
        }
        for (int i = 0; i < 4; i++) {
            List<Card> col = src.get(Suit.ofIndex(i));
            if (col != null) {
                f.set(i, new ArrayList<>(col));
            }
        }
        return f;
    }

    public Board copy() {
        return new Board(tableau, foundations, stock, waste);
    }

    public List<List<Card>> tableau() {
        return copyCols(tableau);
    }

    /** 枠 0..3。空、または底が A。 */
    public List<List<Card>> foundations() {
        return copyFoundations(foundations);
    }

    public List<Card> stock() {
        return new ArrayList<>(stock);
    }

    public List<Card> waste() {
        return new ArrayList<>(waste);
    }

    List<Card> tableauCol(int i) {
        return tableau.get(i);
    }

    List<Card> foundationSlot(int i) {
        return foundations.get(i);
    }

    List<Card> stockMut() {
        return stock;
    }

    List<Card> wasteMut() {
        return waste;
    }

    private static List<List<Card>> copyCols(List<List<Card>> src) {
        List<List<Card>> out = new ArrayList<>(src.size());
        for (List<Card> col : src) {
            out.add(new ArrayList<>(col));
        }
        return out;
    }

    private static List<List<Card>> copyFoundations(List<List<Card>> src) {
        List<List<Card>> out = emptyFoundations();
        int n = Math.min(4, src.size());
        for (int i = 0; i < n; i++) {
            out.set(i, new ArrayList<>(src.get(i)));
        }
        return out;
    }
}
