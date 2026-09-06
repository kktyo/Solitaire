enum Suit { s, h, d, c }

extension SuitX on Suit {
  String get code => name.toUpperCase();
  bool get red => this == Suit.h || this == Suit.d;
  static Suit ofIndex(int i) => Suit.values[i];
}

class Card {
  Card(this.id, this.faceUp);
  final String id;
  final bool faceUp;

  int get rank {
    const order = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
    final r = id.substring(0, id.length - 1);
    final i = order.indexOf(r);
    if (i < 0) {
      throw FormatException('bad id $id');
    }
    return i + 1;
  }

  Suit get suit => Suit.values.firstWhere((s) => s.code == id.substring(id.length - 1));
  bool get red => suit.red;
  Card withFaceUp(bool up) => Card(id, up);
}

enum Pile { tableau, foundation, stock, waste }

class Location {
  Location(this.pile, this.index);
  final Pile pile;
  final int index;
}

enum MoveType { move, draw, recycle }

class Move {
  Move({required this.type, this.from, this.to, this.count = 0});
  final MoveType type;
  final Location? from;
  final Location? to;
  final int count;

  factory Move.draw() => Move(type: MoveType.draw);
  factory Move.recycle() => Move(type: MoveType.recycle);
  factory Move.relocate(Location from, Location to, int count) =>
      Move(type: MoveType.move, from: from, to: to, count: count);

  Map<String, dynamic> toJson() {
    final m = <String, dynamic>{'type': type.name.toUpperCase()};
    if (from != null) {
      m['from'] = {'pile': from!.pile.name.toUpperCase(), 'index': from!.index};
    }
    if (to != null) {
      m['to'] = {'pile': to!.pile.name.toUpperCase(), 'index': to!.index};
    }
    if (type == MoveType.move) {
      m['count'] = count;
    }
    return m;
  }
}

class Board {
  Board({
    required List<List<Card>> tableau,
    required List<List<Card>> foundations,
    required List<Card> stock,
    required List<Card> waste,
  })  : tableau = tableau.map((c) => List<Card>.from(c)).toList(),
        foundations = List.generate(4, (i) => List<Card>.from(i < foundations.length ? foundations[i] : const [])),
        stock = List<Card>.from(stock),
        waste = List<Card>.from(waste);

  final List<List<Card>> tableau;
  final List<List<Card>> foundations;
  final List<Card> stock;
  final List<Card> waste;

  Board copy() => Board(tableau: tableau, foundations: foundations, stock: stock, waste: waste);

  factory Board.fromJson(Map<String, dynamic> json) {
    List<Card> cards(dynamic raw) =>
        (raw as List? ?? const []).map((e) => Card(e['id'] as String, e['faceUp'] as bool)).toList();
    final tab = (json['tableau'] as List).map((c) => cards(c)).toList();
    final rawF = json['foundations'];
    late final List<List<Card>> f;
    if (rawF is List) {
      f = rawF.map((c) => cards(c)).toList();
    } else {
      final m = Map<String, dynamic>.from(rawF as Map);
      f = [for (final s in Suit.values) cards(m[s.code])];
    }
    return Board(tableau: tab, foundations: f, stock: cards(json['stock']), waste: cards(json['waste']));
  }

  Map<String, dynamic> toJson() => {
        'tableau': tableau
            .map((c) => c.map((e) => {'id': e.id, 'faceUp': e.faceUp}).toList())
            .toList(),
        'foundations': foundations.map((c) => c.map((e) => {'id': e.id, 'faceUp': e.faceUp}).toList()).toList(),
        'stock': stock.map((e) => {'id': e.id, 'faceUp': e.faceUp}).toList(),
        'waste': waste.map((e) => {'id': e.id, 'faceUp': e.faceUp}).toList(),
      };
}

class ApplyResult {
  ApplyResult.ok(this.board, this.cleared) : legal = true;
  ApplyResult.illegal()
      : legal = false,
        board = null,
        cleared = false;
  final bool legal;
  final Board? board;
  final bool cleared;
}

class Deal {
  static const ranks = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];

  static Board initialBoard(int Function(int max) nextInt) {
    final deck = <Card>[];
    for (final s in Suit.values) {
      for (final r in ranks) {
        deck.add(Card('$r${s.code}', false));
      }
    }
    for (var i = deck.length - 1; i > 0; i--) {
      final j = nextInt(i + 1);
      final tmp = deck[i];
      deck[i] = deck[j];
      deck[j] = tmp;
    }
    final tableau = <List<Card>>[];
    var idx = 0;
    for (var col = 0; col < 7; col++) {
      final pile = <Card>[];
      for (var n = 0; n < col + 1; n++) {
        final c = deck[idx++];
        pile.add(n == col ? c.withFaceUp(true) : c);
      }
      tableau.add(pile);
    }
    return Board(
      tableau: tableau,
      foundations: List.generate(4, (_) => <Card>[]),
      stock: deck.sublist(idx),
      waste: [],
    );
  }
}

class Rules {
  static bool isLegal(Board board, Move move) => apply(board, move).legal;

  static ApplyResult apply(Board board, Move move) {
    final next = board.copy();
    switch (move.type) {
      case MoveType.draw:
        return _draw(next);
      case MoveType.recycle:
        return _recycle(next);
      case MoveType.move:
        return _move(next, move);
    }
  }

  static int movableTableauCount(Board board, int column, int startIndex) {
    if (column < 0 || column > 6) return 0;
    final col = board.tableau[column];
    if (startIndex < 0 || startIndex >= col.length) return 0;
    if (!col[startIndex].faceUp) return 0;
    for (var j = startIndex; j < col.length - 1; j++) {
      if (!_follows(col[j], col[j + 1])) return 0;
    }
    return col.length - startIndex;
  }

  static ApplyResult _draw(Board b) {
    if (b.stock.isEmpty) return ApplyResult.illegal();
    final c = b.stock.removeAt(0).withFaceUp(true);
    b.waste.add(c);
    return _ok(b);
  }

  static ApplyResult _recycle(Board b) {
    if (b.stock.isNotEmpty || b.waste.isEmpty) return ApplyResult.illegal();
    final stock = <Card>[];
    for (var i = b.waste.length - 1; i >= 0; i--) {
      stock.add(b.waste[i].withFaceUp(false));
    }
    b.waste.clear();
    b.stock.addAll(stock);
    return _ok(b);
  }

  static ApplyResult _move(Board next, Move move) {
    if (move.count < 1 || move.from == null || move.to == null) return ApplyResult.illegal();
    if (move.from!.pile == move.to!.pile && move.from!.index == move.to!.index) {
      return ApplyResult.illegal();
    }
    final taken = _take(next, move.from!, move.count);
    if (taken == null) return ApplyResult.illegal();
    if (!_canPlace(taken.first, next, move.to!, move.count)) return ApplyResult.illegal();
    _removeLast(next, move.from!, move.count);
    _append(next, move.to!, taken);
    _flip(next, move.from!);
    return _ok(next);
  }

  static List<Card>? _take(Board board, Location from, int count) {
    switch (from.pile) {
      case Pile.tableau:
        if (from.index < 0 || from.index > 6) return null;
        final col = board.tableau[from.index];
        if (col.length < count) return null;
        final start = col.length - count;
        final slice = col.sublist(start);
        if (slice.any((c) => !c.faceUp)) return null;
        if (movableTableauCount(board, from.index, start) != count) return null;
        return slice;
      case Pile.waste:
        if (count != 1 || board.waste.isEmpty) return null;
        return [board.waste.last];
      case Pile.foundation:
        if (count != 1 || from.index < 0 || from.index > 3) return null;
        final col = board.foundations[from.index];
        if (col.isEmpty) return null;
        return [col.last];
      case Pile.stock:
        return null;
    }
  }

  static bool _canPlace(Card first, Board board, Location to, int count) {
    switch (to.pile) {
      case Pile.tableau:
        if (to.index < 0 || to.index > 6) return false;
        final col = board.tableau[to.index];
        if (col.isEmpty) return first.rank == 13;
        final top = col.last;
        return first.red != top.red && first.rank == top.rank - 1;
      case Pile.foundation:
        if (count != 1 || to.index < 0 || to.index > 3) return false;
        final col = board.foundations[to.index];
        if (col.isEmpty) {
          if (first.rank != 1) return false;
          for (var s = 0; s < 4; s++) {
            if (s == to.index) continue;
            final other = board.foundations[s];
            if (other.isNotEmpty && other.first.suit == first.suit) return false;
          }
          return true;
        }
        final top = col.last;
        return first.suit == top.suit && first.rank == top.rank + 1;
      case Pile.stock:
      case Pile.waste:
        return false;
    }
  }

  static void _removeLast(Board board, Location from, int count) {
    switch (from.pile) {
      case Pile.tableau:
        final col = board.tableau[from.index];
        col.removeRange(col.length - count, col.length);
      case Pile.waste:
        board.waste.removeLast();
      case Pile.foundation:
        board.foundations[from.index].removeLast();
      case Pile.stock:
        break;
    }
  }

  static void _append(Board board, Location to, List<Card> cards) {
    switch (to.pile) {
      case Pile.tableau:
        board.tableau[to.index].addAll(cards);
      case Pile.foundation:
        board.foundations[to.index].addAll(cards);
      case Pile.stock:
      case Pile.waste:
        break;
    }
  }

  static void _flip(Board board, Location from) {
    if (from.pile != Pile.tableau) return;
    final col = board.tableau[from.index];
    if (col.isNotEmpty && !col.last.faceUp) {
      col[col.length - 1] = col.last.withFaceUp(true);
    }
  }

  static bool _follows(Card lower, Card upper) =>
      lower.red != upper.red && upper.rank == lower.rank - 1 && upper.faceUp && lower.faceUp;

  static ApplyResult _ok(Board b) {
    return ApplyResult.ok(b, isCleared(b));
  }

  static bool isCleared(Board board) => board.foundations.every((c) => c.length == 13);

  static List<Move> legalRelocates(Board board) {
    final out = <Move>[];
    void addFrom(Location from, int count) {
      for (var to = 0; to < 7; to++) {
        if (from.pile == Pile.tableau && from.index == to) continue;
        final m = Move.relocate(from, Location(Pile.tableau, to), count);
        if (isLegal(board, m)) out.add(m);
      }
      if (count == 1) {
        for (var f = 0; f < 4; f++) {
          if (from.pile == Pile.foundation && from.index == f) continue;
          final m = Move.relocate(from, Location(Pile.foundation, f), 1);
          if (isLegal(board, m)) out.add(m);
        }
      }
    }

    for (var col = 0; col < 7; col++) {
      final cards = board.tableau[col];
      for (var i = 0; i < cards.length; i++) {
        final count = movableTableauCount(board, col, i);
        if (count > 0) addFrom(Location(Pile.tableau, col), count);
      }
    }
    if (board.waste.isNotEmpty) {
      addFrom(Location(Pile.waste, 0), 1);
    }
    for (var f = 0; f < 4; f++) {
      if (board.foundations[f].isNotEmpty) {
        addFrom(Location(Pile.foundation, f), 1);
      }
    }
    return out;
  }

  static bool isStalemate(Board board) {
    if (isCleared(board)) return false;
    if (legalRelocates(board).isNotEmpty) return false;
    if (board.stock.isEmpty && board.waste.isEmpty) return true;
    var cur = board.copy();
    final seen = <String>{};
    while (true) {
      if (!seen.add(_stockWasteKey(cur))) return true;
      if (_wasteTopPlayable(cur)) return false;
      final ApplyResult next;
      if (cur.stock.isNotEmpty) {
        next = apply(cur, Move.draw());
      } else if (cur.waste.isNotEmpty) {
        next = apply(cur, Move.recycle());
      } else {
        return true;
      }
      if (!next.legal) return true;
      cur = next.board!;
    }
  }

  static bool _wasteTopPlayable(Board board) {
    if (board.waste.isEmpty) return false;
    final from = Location(Pile.waste, 0);
    for (var to = 0; to < 7; to++) {
      if (isLegal(board, Move.relocate(from, Location(Pile.tableau, to), 1))) return true;
    }
    for (var f = 0; f < 4; f++) {
      if (isLegal(board, Move.relocate(from, Location(Pile.foundation, f), 1))) return true;
    }
    return false;
  }

  static String _stockWasteKey(Board board) {
    final ids = StringBuffer();
    for (final c in board.stock) {
      ids.write(c.id);
    }
    ids.write('/');
    for (final c in board.waste) {
      ids.write(c.id);
    }
    return ids.toString();
  }

  static bool tableauAllFaceUp(Board board) => board.tableau.every((col) => col.every((c) => c.faceUp));

  static Move? nextAutoMove(Board board) {
    for (final m in legalRelocates(board)) {
      if (m.to?.pile == Pile.foundation) return m;
    }
    if (board.stock.isNotEmpty) return Move.draw();
    if (board.waste.isNotEmpty) return Move.recycle();
    return null;
  }
}
