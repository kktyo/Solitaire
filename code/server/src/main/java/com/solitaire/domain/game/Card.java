package com.solitaire.domain.game;

public record Card(String id, boolean faceUp) {

    public int rank() {
        return Rank.parse(id);
    }

    public Suit suit() {
        return Suit.valueOf(id.substring(id.length() - 1));
    }

    public boolean red() {
        return suit().red();
    }

    public Card withFaceUp(boolean up) {
        return new Card(id, up);
    }
}
