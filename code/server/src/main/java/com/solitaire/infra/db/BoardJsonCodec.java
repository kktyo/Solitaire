package com.solitaire.infra.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.solitaire.domain.game.Board;
import com.solitaire.domain.game.Card;
import com.solitaire.domain.game.Location;
import com.solitaire.domain.game.Move;
import com.solitaire.domain.game.Pile;
import com.solitaire.domain.game.Suit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class BoardJsonCodec implements com.solitaire.application.GameApplicationService.MoveJson {

    private final ObjectMapper mapper;

    public BoardJsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String writeBoard(Board board) {
        try {
            return mapper.writeValueAsString(toNode(board));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Board readBoard(String json) {
        try {
            return fromNode(mapper.readTree(json));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String write(Move move) {
        try {
            ObjectNode n = mapper.createObjectNode();
            n.put("type", move.type().name());
            if (move.from() != null) {
                n.set("from", loc(move.from()));
            }
            if (move.to() != null) {
                n.set("to", loc(move.to()));
            }
            if (move.type() == Move.Type.MOVE) {
                n.put("count", move.count());
            }
            return mapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Move readMove(JsonNode n) {
        String type = n.path("type").asText();
        return switch (type) {
            case "DRAW" -> Move.draw();
            case "RECYCLE" -> Move.recycle();
            case "MOVE" -> Move.relocate(readLoc(n.get("from")), readLoc(n.get("to")), n.path("count").asInt(1));
            default -> throw new IllegalArgumentException("unknown move");
        };
    }

    public ObjectNode toNode(Board board) {
        ObjectNode n = mapper.createObjectNode();
        ArrayNode tab = n.putArray("tableau");
        for (List<Card> col : board.tableau()) {
            tab.add(cards(col));
        }
        ArrayNode f = n.putArray("foundations");
        for (List<Card> col : board.foundations()) {
            f.add(cards(col));
        }
        n.set("stock", cards(board.stock()));
        n.set("waste", cards(board.waste()));
        return n;
    }

    private ArrayNode cards(List<Card> col) {
        ArrayNode a = mapper.createArrayNode();
        for (Card c : col) {
            ObjectNode n = mapper.createObjectNode();
            n.put("id", c.id());
            n.put("faceUp", c.faceUp());
            a.add(n);
        }
        return a;
    }

    private ObjectNode loc(Location loc) {
        ObjectNode n = mapper.createObjectNode();
        n.put("pile", loc.pile().name());
        n.put("index", loc.index());
        return n;
    }

    private static Location readLoc(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return new Location(Pile.valueOf(n.path("pile").asText()), n.path("index").asInt(0));
    }

    private Board fromNode(JsonNode n) {
        List<List<Card>> tableau = new ArrayList<>();
        for (JsonNode col : n.path("tableau")) {
            tableau.add(readCards(col));
        }
        JsonNode f = n.path("foundations");
        List<List<Card>> foundations;
        if (f.isArray()) {
            foundations = new ArrayList<>(4);
            for (JsonNode col : f) {
                foundations.add(readCards(col));
            }
            while (foundations.size() < 4) {
                foundations.add(new ArrayList<>());
            }
        } else {
            Map<Suit, List<Card>> bySuit = new EnumMap<>(Suit.class);
            for (Suit s : Suit.values()) {
                bySuit.put(s, readCards(f.path(s.name())));
            }
            foundations = Board.fromSuitMap(bySuit);
        }
        return new Board(tableau, foundations, readCards(n.path("stock")), readCards(n.path("waste")));
    }

    private static List<Card> readCards(JsonNode arr) {
        List<Card> list = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return list;
        }
        for (JsonNode n : arr) {
            list.add(new Card(n.path("id").asText(), n.path("faceUp").asBoolean()));
        }
        return list;
    }
}
