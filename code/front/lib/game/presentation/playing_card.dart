import 'dart:math' as math;

import 'package:flutter/material.dart' hide Card;

import '../domain/rules.dart';

const playingCardAspect = 3.5 / 2.5;

String rankLabel(Card card) {
  const order = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
  return order[card.rank - 1];
}

String suitGlyph(Suit suit) => switch (suit) { Suit.s => '♠', Suit.h => '♥', Suit.d => '♦', Suit.c => '♣' };

class PlayingCardView extends StatelessWidget {
  const PlayingCardView({
    super.key,
    required this.width,
    required this.height,
    this.card,
    this.empty = false,
    this.facedown = false,
    this.highlight = false,
  });

  final double width;
  final double height;
  final Card? card;
  final bool empty;
  final bool facedown;
  final bool highlight;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(width * 0.08);
    if (empty) {
      return Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          borderRadius: radius,
          border: Border.all(
            color: highlight ? Colors.amber : Colors.white38,
            width: highlight ? 2.5 : 1.2,
          ),
          color: Colors.white10,
        ),
      );
    }
    final showBack = facedown || card == null || !card!.faceUp;
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        borderRadius: radius,
        border: Border.all(
          color: highlight ? Colors.amber : const Color(0xFF2A2A2A),
          width: highlight ? 2.5 : 1,
        ),
        boxShadow: const [
          BoxShadow(color: Color(0x33000000), blurRadius: 2, offset: Offset(0, 1)),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: showBack ? _CardBack(width: width, height: height) : _CardFace(card: card!, width: width, height: height),
    );
  }
}

class _CardBack extends StatelessWidget {
  const _CardBack({required this.width, required this.height});
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(size: Size(width, height), painter: _BackPainter());
  }
}

class _BackPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final inset = size.width * 0.07;
    final outer = Offset.zero & size;
    canvas.drawRect(outer, Paint()..color = const Color(0xFFECE6D8));
    final inner = RRect.fromRectAndRadius(
      Rect.fromLTWH(inset, inset, size.width - inset * 2, size.height - inset * 2),
      Radius.circular(size.width * 0.05),
    );
    canvas.drawRRect(inner, Paint()..color = const Color(0xFF1B3A8A));
    canvas.save();
    canvas.clipRRect(inner);
    final line = Paint()
      ..color = const Color(0xFFD4AF37).withOpacity(0.55)
      ..strokeWidth = math.max(1, size.width * 0.018)
      ..style = PaintingStyle.stroke;
    final step = size.width * 0.14;
    for (var x = -size.height; x < size.width + size.height; x += step) {
      canvas.drawLine(Offset(x, 0), Offset(x + size.height, size.height), line);
      canvas.drawLine(Offset(x, size.height), Offset(x + size.height, 0), line);
    }
    canvas.restore();
    canvas.drawRRect(
      inner,
      Paint()
        ..color = const Color(0xFFD4AF37)
        ..style = PaintingStyle.stroke
        ..strokeWidth = math.max(1, size.width * 0.03),
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _CardFace extends StatelessWidget {
  const _CardFace({required this.card, required this.width, required this.height});
  final Card card;
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    final color = card.red ? const Color(0xFFC41E3A) : const Color(0xFF111111);
    final rank = rankLabel(card);
    final suit = suitGlyph(card.suit);
    final index = _Index(rank: rank, suit: suit, color: color, cardWidth: width);
    return ColoredBox(
      color: Colors.white,
      child: Stack(
        children: [
          Positioned(left: 0, top: 0, child: index),
          Positioned(right: 0, bottom: 0, child: Transform.rotate(angle: math.pi, child: index)),
          Positioned.fill(
            child: Padding(
              padding: EdgeInsets.fromLTRB(width * 0.18, height * 0.16, width * 0.18, height * 0.16),
              child: card.rank >= 11
                  ? _FaceCenter(rank: rank, suit: suit, color: color, cardWidth: width)
                  : CustomPaint(
                      painter: _PipPainter(
                        pips: pipsFor(card.rank),
                        glyph: suit,
                        color: color,
                        fontSize: width * (card.rank == 1 ? 0.42 : 0.22),
                      ),
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

class _FaceCenter extends StatelessWidget {
  const _FaceCenter({required this.rank, required this.suit, required this.color, required this.cardWidth});
  final String rank;
  final String suit;
  final Color color;
  final double cardWidth;

  @override
  Widget build(BuildContext context) {
    return FittedBox(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(rank, style: TextStyle(color: color, fontWeight: FontWeight.w800, fontSize: cardWidth * 0.42, height: 1)),
          Text(suit, style: TextStyle(color: color, fontSize: cardWidth * 0.28, height: 1)),
        ],
      ),
    );
  }
}

class _Index extends StatelessWidget {
  const _Index({required this.rank, required this.suit, required this.color, required this.cardWidth});
  final String rank;
  final String suit;
  final Color color;
  final double cardWidth;

  @override
  Widget build(BuildContext context) {
    final rankSize = rank == '10' ? cardWidth * 0.155 : cardWidth * 0.20;
    return Padding(
      padding: EdgeInsets.only(left: cardWidth * 0.045, top: cardWidth * 0.03, right: cardWidth * 0.02),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            rank,
            style: TextStyle(color: color, fontWeight: FontWeight.w800, fontSize: rankSize, height: 0.95),
          ),
          Text(suit, style: TextStyle(color: color, fontSize: cardWidth * 0.155, height: 0.95)),
        ],
      ),
    );
  }
}

class CardPip {
  const CardPip(this.x, this.y, {this.flip = false});
  final double x;
  final double y;
  final bool flip;
}

List<CardPip> pipsFor(int rank) {
  const l = 0.22;
  const r = 0.78;
  const outerL = 0.12;
  const outerR = 0.88;
  const c = 0.50;
  const t = 0.08;
  const b = 0.92;
  const m = 0.50;
  const upper = 0.30;
  const lower = 0.70;
  switch (rank) {
    case 1:
      return const [CardPip(c, m)];
    case 2:
      return const [CardPip(c, t), CardPip(c, b, flip: true)];
    case 3:
      return const [CardPip(c, t), CardPip(c, m), CardPip(c, b, flip: true)];
    case 4:
      return const [CardPip(l, t), CardPip(r, t), CardPip(l, b, flip: true), CardPip(r, b, flip: true)];
    case 5:
      return const [CardPip(l, t), CardPip(r, t), CardPip(c, m), CardPip(l, b, flip: true), CardPip(r, b, flip: true)];
    case 6:
      return const [
        CardPip(l, t),
        CardPip(r, t),
        CardPip(l, m),
        CardPip(r, m),
        CardPip(l, b, flip: true),
        CardPip(r, b, flip: true),
      ];
    case 7:
      return const [
        CardPip(l, t),
        CardPip(r, t),
        CardPip(c, upper),
        CardPip(l, m),
        CardPip(r, m),
        CardPip(l, b, flip: true),
        CardPip(r, b, flip: true),
      ];
    case 8:
      return const [
        CardPip(l, t),
        CardPip(r, t),
        CardPip(c, upper),
        CardPip(l, m),
        CardPip(r, m),
        CardPip(c, lower, flip: true),
        CardPip(l, b, flip: true),
        CardPip(r, b, flip: true),
      ];
    case 9:
      return const [
        CardPip(outerL, 0.10),
        CardPip(outerR, 0.10),
        CardPip(outerL, 0.32),
        CardPip(outerR, 0.32),
        CardPip(c, 0.50),
        CardPip(outerL, 0.68, flip: true),
        CardPip(outerR, 0.68, flip: true),
        CardPip(outerL, 0.90, flip: true),
        CardPip(outerR, 0.90, flip: true),
      ];
    case 10:
      return const [
        CardPip(outerL, 0.08),
        CardPip(outerR, 0.08),
        CardPip(outerL, 0.28),
        CardPip(outerR, 0.28),
        CardPip(c, 0.40),
        CardPip(c, 0.60, flip: true),
        CardPip(outerL, 0.72, flip: true),
        CardPip(outerR, 0.72, flip: true),
        CardPip(outerL, 0.92, flip: true),
        CardPip(outerR, 0.92, flip: true),
      ];
    default:
      return const [];
  }
}

class _PipPainter extends CustomPainter {
  _PipPainter({required this.pips, required this.glyph, required this.color, required this.fontSize});

  final List<CardPip> pips;
  final String glyph;
  final Color color;
  final double fontSize;

  @override
  void paint(Canvas canvas, Size size) {
    final painter = TextPainter(textAlign: TextAlign.center, textDirection: TextDirection.ltr);
    for (final pip in pips) {
      painter.text = TextSpan(
        text: glyph,
        style: TextStyle(color: color, fontSize: fontSize, height: 1, fontWeight: FontWeight.w600),
      );
      painter.layout();
      canvas.save();
      canvas.translate(pip.x * size.width, pip.y * size.height);
      if (pip.flip) {
        canvas.rotate(math.pi);
      }
      painter.paint(canvas, Offset(-painter.width / 2, -painter.height / 2));
      canvas.restore();
    }
  }

  @override
  bool shouldRepaint(covariant _PipPainter oldDelegate) =>
      oldDelegate.glyph != glyph || oldDelegate.color != color || oldDelegate.fontSize != fontSize;
}

/// 盤面幅上限と場札オフセット。
class BoardLayout {
  BoardLayout({
    required this.boardWidth,
    required this.cardWidth,
    required this.cardHeight,
    required this.minPeek,
    required this.preferredPeek,
    required this.tableauHeight,
  });

  static const maxBoardWidth = 560.0;
  static const columnGutter = 4.0;

  final double boardWidth;
  final double cardWidth;
  final double cardHeight;
  final double minPeek;
  final double preferredPeek;
  final double tableauHeight;

  factory BoardLayout.of({
    required Size viewport,
    required int maxTableauCount,
  }) {
    final capped = math.min(viewport.width, maxBoardWidth);
    var cardWidth = (capped / 7) - columnGutter;
    var cardHeight = cardWidth * playingCardAspect;
    var minPeek = math.max(18.0, cardWidth * 0.32);
    final n = math.max(1, maxTableauCount);
    const topPad = 16.0;
    final denom = (2 * cardHeight) + ((n - 1) * minPeek);
    if (denom > 0 && viewport.height > topPad) {
      final scale = math.min(1.0, (viewport.height - topPad) / denom);
      if (scale < 1) {
        cardWidth *= scale;
        cardHeight *= scale;
        minPeek *= scale;
      }
    }
    final tableauHeight = math.max(0.0, viewport.height - topPad - cardHeight);
    final preferredPeek = math.max(minPeek, cardWidth * 0.38);
    return BoardLayout(
      boardWidth: math.min(viewport.width, 7 * (cardWidth + columnGutter)),
      cardWidth: cardWidth,
      cardHeight: cardHeight,
      minPeek: minPeek,
      preferredPeek: preferredPeek,
      tableauHeight: tableauHeight,
    );
  }

  double peekFor(int count) {
    if (count <= 1) {
      return 0;
    }
    final room = tableauHeight - cardHeight;
    if (!room.isFinite || room <= 0) {
      return minPeek;
    }
    return (room / (count - 1)).clamp(minPeek, preferredPeek);
  }
}

/// ドロップ成功時、連なり先頭が着地するグローバル左上。
Offset dropLanding({
  required Offset slotTopLeft,
  required Location dest,
  required Board board,
  required BoardLayout layout,
  required int incomingCount,
}) {
  const inset = 2.0;
  if (dest.pile == Pile.tableau) {
    final n = dest.index >= 0 && dest.index < board.tableau.length ? board.tableau[dest.index].length : 0;
    final peek = layout.peekFor(n + incomingCount);
    return slotTopLeft + Offset(inset, n * peek);
  }
  return slotTopLeft + const Offset(inset, 0);
}
