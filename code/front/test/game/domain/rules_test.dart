import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:solitaire/game/domain/rules.dart';

void main() {
  test('TV-DEAL', () {
    var n = 1;
    final board = Deal.initialBoard((max) {
      n = (n * 1103515245 + 12345) & 0x7fffffff;
      return n % max;
    });
    expect(board.tableau.length, 7);
    for (var i = 0; i < 7; i++) {
      expect(board.tableau[i].length, i + 1);
      expect(board.tableau[i].last.faceUp, isTrue);
    }
    expect(board.stock.length, 24);
  });

  test('vectors', () {
    final file = File('test/fixtures/rules_vectors.json');
    final root = jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
    for (final raw in root['vectors'] as List) {
      final v = raw as Map<String, dynamic>;
      final id = v['id'] as String;
      final board = Board.fromJson(Map<String, dynamic>.from(v['board'] as Map));
      final move = _move(Map<String, dynamic>.from(v['move'] as Map));
      final legal = v['legal'] as bool;
      final result = Rules.apply(board, move);
      expect(result.legal, legal, reason: id);
      if (legal) {
        final expectMap = Map<String, dynamic>.from(v['expect'] as Map);
        expect(result.cleared, expectMap['cleared'] ?? false, reason: id);
        _assertExpect(id, result.board!, expectMap);
      }
    }
  });

  test('TV-CLEAR', () {
    final tableau = List.generate(7, (_) => <Card>[]);
    tableau[0].add(Card('KS', true));
    final f = <Suit, List<Card>>{};
    for (final s in Suit.values) {
      final max = s == Suit.s ? 12 : 13;
      f[s] = [for (var r = 1; r <= max; r++) Card('${Deal.ranks[r - 1]}${s.code}', true)];
    }
    final board = Board(tableau: tableau, foundations: f, stock: [], waste: []);
    final result = Rules.apply(
      board,
      Move.relocate(Location(Pile.tableau, 0), Location(Pile.foundation, 0), 1),
    );
    expect(result.legal, isTrue);
    expect(result.cleared, isTrue);
  });
}

Move _move(Map<String, dynamic> n) {
  switch (n['type']) {
    case 'DRAW':
      return Move.draw();
    case 'RECYCLE':
      return Move.recycle();
    default:
      final from = n['from'] as Map;
      final to = n['to'] as Map;
      return Move.relocate(
        Location(_pile(from['pile'] as String), from['index'] as int),
        Location(_pile(to['pile'] as String), to['index'] as int),
        n['count'] as int? ?? 1,
      );
  }
}

Pile _pile(String p) => Pile.values.firstWhere((e) => e.name.toUpperCase() == p);

void _assertExpect(String id, Board board, Map<String, dynamic> expectMap) {
  if (expectMap.containsKey('tableau0Empty')) {
    expect(board.tableau[0].isEmpty, expectMap['tableau0Empty'], reason: id);
  }
  if (expectMap.containsKey('tableau1Top')) {
    expect(board.tableau[1].last.id, expectMap['tableau1Top'], reason: id);
  }
  if (expectMap.containsKey('tableau0Len')) {
    expect(board.tableau[0].length, expectMap['tableau0Len'], reason: id);
  }
  if (expectMap.containsKey('tableau0Top')) {
    expect(board.tableau[0].last.id, expectMap['tableau0Top'], reason: id);
  }
  if (expectMap.containsKey('tableau0TopFaceUp')) {
    expect(board.tableau[0].last.faceUp, expectMap['tableau0TopFaceUp'], reason: id);
  }
  if (expectMap.containsKey('foundationSTop')) {
    expect(board.foundations[Suit.s]!.last.id, expectMap['foundationSTop'], reason: id);
  }
  if (expectMap.containsKey('foundationSLen')) {
    expect(board.foundations[Suit.s]!.length, expectMap['foundationSLen'], reason: id);
  }
  if (expectMap.containsKey('stockLen')) {
    expect(board.stock.length, expectMap['stockLen'], reason: id);
  }
  if (expectMap.containsKey('wasteTop')) {
    expect(board.waste.last.id, expectMap['wasteTop'], reason: id);
  }
  if (expectMap.containsKey('wasteTopFaceUp')) {
    expect(board.waste.last.faceUp, expectMap['wasteTopFaceUp'], reason: id);
  }
  if (expectMap.containsKey('wasteLen')) {
    expect(board.waste.length, expectMap['wasteLen'], reason: id);
  }
  if (expectMap.containsKey('stockIds')) {
    expect(board.stock.map((c) => c.id).toList(), List<String>.from(expectMap['stockIds'] as List), reason: id);
  }
  if (expectMap.containsKey('stockAllFaceDown')) {
    expect(board.stock.every((c) => !c.faceUp), isTrue, reason: id);
  }
}
