package com.solitaire.domain.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public final class Solver {

    private Solver() {}

    public static boolean canClear(Board start, int maxNodes) {
        Board collapsed = collapseFoundations(start);
        if (Rules.isCleared(collapsed)) {
            return true;
        }
        record Node(Board board, int foundationCards) {}
        PriorityQueue<Node> pq = new PriorityQueue<>(Comparator.comparingInt(Node::foundationCards).reversed());
        Set<String> seen = new HashSet<>();
        pq.add(new Node(collapsed, foundationCount(collapsed)));
        seen.add(Rules.fingerprint(collapsed));
        int nodes = 0;
        while (!pq.isEmpty() && nodes < maxNodes) {
            Node cur = pq.poll();
            nodes++;
            if (Rules.isCleared(cur.board())) {
                return true;
            }
            for (Move move : orderedMoves(cur.board())) {
                ApplyResult result = Rules.apply(cur.board(), move);
                if (result instanceof ApplyResult.Ok ok) {
                    Board next = collapseFoundations(ok.board());
                    if (Rules.isCleared(next)) {
                        return true;
                    }
                    if (seen.add(Rules.fingerprint(next))) {
                        pq.add(new Node(next, foundationCount(next)));
                    }
                }
            }
        }
        return false;
    }

    static Board collapseFoundations(Board board) {
        Board cur = board;
        while (true) {
            Move found = null;
            for (Move m : Rules.legalRelocates(cur)) {
                if (m.to() != null && m.to().pile() == Pile.FOUNDATION) {
                    found = m;
                    break;
                }
            }
            if (found == null) {
                return cur;
            }
            ApplyResult result = Rules.apply(cur, found);
            if (!(result instanceof ApplyResult.Ok ok)) {
                return cur;
            }
            cur = ok.board();
        }
    }

    private static int foundationCount(Board board) {
        int n = 0;
        for (Suit s : Suit.values()) {
            n += board.foundation(s).size();
        }
        return n;
    }

    private static List<Move> orderedMoves(Board board) {
        List<Move> relocates = Rules.legalRelocates(board);
        List<Move> out = new ArrayList<>(relocates.size() + 2);
        out.addAll(relocates);
        if (Rules.isLegal(board, Move.draw())) {
            out.add(Move.draw());
        }
        if (Rules.isLegal(board, Move.recycle())) {
            out.add(Move.recycle());
        }
        return out;
    }
}
