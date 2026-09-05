package com.solitaire.domain.game;

public record Move(Type type, Location from, Location to, int count) {

    public enum Type {
        MOVE,
        DRAW,
        RECYCLE
    }

    public static Move draw() {
        return new Move(Type.DRAW, null, null, 0);
    }

    public static Move recycle() {
        return new Move(Type.RECYCLE, null, null, 0);
    }

    public static Move relocate(Location from, Location to, int count) {
        return new Move(Type.MOVE, from, to, count);
    }
}
