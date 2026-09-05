package com.solitaire.domain.game;

import java.util.List;

public final class Rank {

    private static final List<String> ORDER = List.of(
            "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K");

    private Rank() {}

    public static int parse(String id) {
        String rank = id.substring(0, id.length() - 1);
        int i = ORDER.indexOf(rank);
        if (i < 0) {
            throw new IllegalArgumentException("bad card id: " + id);
        }
        return i + 1;
    }

    public static String label(int rank) {
        return ORDER.get(rank - 1);
    }

    public static List<String> labels() {
        return ORDER;
    }
}
