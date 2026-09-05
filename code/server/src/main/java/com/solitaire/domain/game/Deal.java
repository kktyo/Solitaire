package com.solitaire.domain.game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
        Map<Suit, List<Card>> foundations = new EnumMap<>(Suit.class);
        for (Suit s : Suit.values()) {
            foundations.put(s, new ArrayList<>());
        }
        Board board = new Board(tableau, foundations, stock, List.of());
        BoardInvariant.assertFullDeck(board);
        return board;
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
