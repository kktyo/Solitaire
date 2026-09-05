package com.solitaire.domain.game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Board {

    private final List<List<Card>> tableau;
    private final Map<Suit, List<Card>> foundations;
    private final List<Card> stock;
    private final List<Card> waste;

    public Board(
            List<List<Card>> tableau,
            Map<Suit, List<Card>> foundations,
            List<Card> stock,
            List<Card> waste) {
        this.tableau = copyCols(tableau);
        this.foundations = copyFoundations(foundations);
        this.stock = new ArrayList<>(stock);
        this.waste = new ArrayList<>(waste);
    }

    public Board copy() {
        return new Board(tableau, foundations, stock, waste);
    }

    public List<List<Card>> tableau() {
        return copyCols(tableau);
    }

    public Map<Suit, List<Card>> foundations() {
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

    List<Card> foundation(Suit s) {
        return foundations.get(s);
    }

    List<Card> stockMut() {
        return stock;
    }

    List<Card> wasteMut() {
        return waste;
    }

    private static List<List<Card>> copyCols(List<List<Card>> src) {
        List<List<Card>> out = new ArrayList<>(7);
        for (List<Card> col : src) {
            out.add(new ArrayList<>(col));
        }
        return out;
    }

    private static Map<Suit, List<Card>> copyFoundations(Map<Suit, List<Card>> src) {
        Map<Suit, List<Card>> out = new EnumMap<>(Suit.class);
        for (Suit s : Suit.values()) {
            List<Card> col = src.get(s);
            out.put(s, col == null ? new ArrayList<>() : new ArrayList<>(col));
        }
        return out;
    }
}
