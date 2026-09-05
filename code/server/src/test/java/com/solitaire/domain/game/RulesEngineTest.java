package com.solitaire.domain.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void tvDeal() {
        Board board = Deal.initialBoard(new Random(1));
        BoardInvariant.assertFullDeck(board);
        assertEquals(7, board.tableau().size());
        for (int i = 0; i < 7; i++) {
            assertEquals(i + 1, board.tableau().get(i).size());
            assertTrue(board.tableau().get(i).get(i).faceUp());
            for (int j = 0; j < i; j++) {
                assertFalse(board.tableau().get(i).get(j).faceUp());
            }
        }
        assertEquals(24, board.stock().size());
        assertTrue(board.waste().isEmpty());
        board.foundations().values().forEach(c -> assertTrue(c.isEmpty()));
    }

    @Test
    void vectorsFromFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/rules_vectors.json")) {
            JsonNode root = MAPPER.readTree(in);
            for (JsonNode v : root.get("vectors")) {
                String id = v.get("id").asText();
                Board board = readBoard(v.get("board"));
                Move move = readMove(v.get("move"));
                boolean legal = v.get("legal").asBoolean();
                ApplyResult result = Rules.apply(board, move);
                assertEquals(legal, result instanceof ApplyResult.Ok, id);
                if (legal) {
                    ApplyResult.Ok ok = (ApplyResult.Ok) result;
                    JsonNode expect = v.get("expect");
                    assertEquals(expect.path("cleared").asBoolean(false), ok.cleared(), id + " cleared");
                    assertExpect(id, ok.board(), expect);
                }
            }
        }
    }

    @Test
    void tvClear() {
        List<List<Card>> tableau = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            tableau.add(new ArrayList<>());
        }
        tableau.get(0).add(new Card("KS", true));
        Map<Suit, List<Card>> f = new EnumMap<>(Suit.class);
        for (Suit s : Suit.values()) {
            List<Card> col = new ArrayList<>();
            int max = s == Suit.S ? 12 : 13;
            for (int r = 1; r <= max; r++) {
                col.add(new Card(Rank.label(r) + s.name(), true));
            }
            f.put(s, col);
        }
        Board board = new Board(tableau, f, List.of(), List.of());
        ApplyResult result = Rules.apply(
                board,
                Move.relocate(new Location(Pile.TABLEAU, 0), new Location(Pile.FOUNDATION, 0), 1));
        ApplyResult.Ok ok = assertInstanceOf(ApplyResult.Ok.class, result);
        assertTrue(ok.cleared());
        assertEquals(13, ok.board().foundations().get(Suit.S).size());
    }

    private static void assertExpect(String id, Board board, JsonNode expect) {
        if (expect.has("tableau0Empty")) {
            assertEquals(expect.get("tableau0Empty").asBoolean(), board.tableau().get(0).isEmpty(), id);
        }
        if (expect.has("tableau1Top")) {
            List<Card> col = board.tableau().get(1);
            assertEquals(expect.get("tableau1Top").asText(), col.get(col.size() - 1).id(), id);
        }
        if (expect.has("tableau0Len")) {
            assertEquals(expect.get("tableau0Len").asInt(), board.tableau().get(0).size(), id);
        }
        if (expect.has("tableau0Top")) {
            List<Card> col = board.tableau().get(0);
            assertEquals(expect.get("tableau0Top").asText(), col.get(col.size() - 1).id(), id);
        }
        if (expect.has("tableau0TopFaceUp")) {
            List<Card> col = board.tableau().get(0);
            assertEquals(expect.get("tableau0TopFaceUp").asBoolean(), col.get(col.size() - 1).faceUp(), id);
        }
        if (expect.has("foundationSTop")) {
            List<Card> col = board.foundations().get(Suit.S);
            assertEquals(expect.get("foundationSTop").asText(), col.get(col.size() - 1).id(), id);
        }
        if (expect.has("foundationSLen")) {
            assertEquals(expect.get("foundationSLen").asInt(), board.foundations().get(Suit.S).size(), id);
        }
        if (expect.has("stockLen")) {
            assertEquals(expect.get("stockLen").asInt(), board.stock().size(), id);
        }
        if (expect.has("wasteTop")) {
            List<Card> w = board.waste();
            assertEquals(expect.get("wasteTop").asText(), w.get(w.size() - 1).id(), id);
        }
        if (expect.has("wasteTopFaceUp")) {
            List<Card> w = board.waste();
            assertEquals(expect.get("wasteTopFaceUp").asBoolean(), w.get(w.size() - 1).faceUp(), id);
        }
        if (expect.has("wasteLen")) {
            assertEquals(expect.get("wasteLen").asInt(), board.waste().size(), id);
        }
        if (expect.has("stockIds")) {
            List<String> ids = new ArrayList<>();
            board.stock().forEach(c -> ids.add(c.id()));
            List<String> expected = new ArrayList<>();
            expect.get("stockIds").forEach(n -> expected.add(n.asText()));
            assertEquals(expected, ids, id);
        }
        if (expect.has("stockAllFaceDown")) {
            assertTrue(board.stock().stream().noneMatch(Card::faceUp), id);
        }
    }

    private static Board readBoard(JsonNode n) {
        List<List<Card>> tableau = new ArrayList<>();
        n.get("tableau").forEach(col -> tableau.add(readCards(col)));
        Map<Suit, List<Card>> foundations = new EnumMap<>(Suit.class);
        JsonNode f = n.get("foundations");
        for (Suit s : Suit.values()) {
            foundations.put(s, readCards(f.get(s.name())));
        }
        return new Board(tableau, foundations, readCards(n.get("stock")), readCards(n.get("waste")));
    }

    private static List<Card> readCards(JsonNode arr) {
        List<Card> list = new ArrayList<>();
        arr.forEach(c -> list.add(new Card(c.get("id").asText(), c.get("faceUp").asBoolean())));
        return list;
    }

    private static Move readMove(JsonNode n) {
        String type = n.get("type").asText();
        return switch (type) {
            case "DRAW" -> Move.draw();
            case "RECYCLE" -> Move.recycle();
            case "MOVE" -> Move.relocate(
                    new Location(Pile.valueOf(n.get("from").get("pile").asText()), n.get("from").path("index").asInt()),
                    new Location(Pile.valueOf(n.get("to").get("pile").asText()), n.get("to").path("index").asInt()),
                    n.path("count").asInt(1));
            default -> throw new IllegalArgumentException(type);
        };
    }
}
