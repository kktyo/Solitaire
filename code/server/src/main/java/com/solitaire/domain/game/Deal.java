package com.solitaire.domain.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Deal {

    private Deal() {}

    public static Board initialBoard(Random rng) {
        List<Card> deck = new ArrayList<>();
        for (Suit s : Suit.values()) {
            for (String r : Rank.labels()) {
                deck.add(new Card(r + s.name(), false));
            }
        }
        fisherYates(deck, rng);
        return dealFrom(deck);
    }

    static Board dealFrom(List<Card> deck) {
        if (deck.size() != 52) {
            throw new IllegalArgumentException("deck");
        }
        List<List<Card>> tableau = new ArrayList<>(7);
        int idx = 0;
        for (int col = 0; col < 7; col++) {
            List<Card> pile = new ArrayList<>();
            for (int n = 0; n < col + 1; n++) {
                Card c = deck.get(idx++);
                pile.add(n == col ? c.withFaceUp(true) : c);
            }
            tableau.add(pile);
        }
        List<Card> stock = new ArrayList<>(deck.subList(idx, deck.size()));
        Board board = new Board(tableau, Board.emptyFoundations(), stock, List.of());
        BoardInvariant.assertFullDeck(board);
        return board;
    }

    public static final int WINNABLE_ATTEMPTS = 24;
    public static final int WINNABLE_NODES = 12_000;
    public static final int WINNABLE_FALLBACK_NODES = 80_000;

    /** ソルバがクリア手順を見つけた山。手順は返さない。 */
    public static Board winnableBoard(Random rng) {
        for (int i = 0; i < WINNABLE_ATTEMPTS; i++) {
            Board board = initialBoard(rng);
            if (Solver.canClear(board, WINNABLE_NODES)) {
                return board;
            }
        }
        for (int i = 0; i < 16; i++) {
            Board board = initialBoard(rng);
            if (Solver.canClear(board, WINNABLE_FALLBACK_NODES)) {
                return board;
            }
        }
        Board ranked = rankedDeal(rng);
        if (Solver.canClear(ranked, WINNABLE_FALLBACK_NODES)) {
            return ranked;
        }
        throw new IllegalStateException("クリア可能な配札を生成できませんでした。");
    }

    static Board rankedDeal(Random rng) {
        List<Suit> suits = new ArrayList<>(List.of(Suit.values()));
        java.util.Collections.shuffle(suits, rng);
        List<Card> deck = new ArrayList<>();
        for (int r = 1; r <= 13; r++) {
            for (Suit s : suits) {
                deck.add(new Card(Rank.label(r) + s.name(), false));
            }
        }
        return dealFrom(deck);
    }

    private static void fisherYates(List<Card> deck, Random rng) {
        for (int i = deck.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Card tmp = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, tmp);
        }
    }
}
