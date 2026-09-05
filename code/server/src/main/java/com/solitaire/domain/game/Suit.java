package com.solitaire.domain.game;

public enum Suit {
    S,
    H,
    D,
    C;

    public boolean red() {
        return this == H || this == D;
    }

    public static Suit ofIndex(int index) {
        return values()[index];
    }
}
