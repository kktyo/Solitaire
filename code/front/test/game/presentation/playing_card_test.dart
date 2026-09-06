import 'package:flutter/material.dart' hide Card;
import 'package:flutter_test/flutter_test.dart';
import 'package:solitaire/game/domain/rules.dart';
import 'package:solitaire/game/presentation/playing_card.dart';

void main() {
  test('wide viewport caps board width', () {
    final layout = BoardLayout.of(viewport: const Size(1400, 900), maxTableauCount: 7);
    expect(layout.boardWidth, lessThanOrEqualTo(BoardLayout.maxBoardWidth + 1));
  });

  test('deep tableau peek stays at least minPeek and fits height', () {
    final layout = BoardLayout.of(viewport: const Size(390, 700), maxTableauCount: 13);
    expect(layout.peekFor(13), greaterThanOrEqualTo(layout.minPeek - 0.01));
    expect(layout.cardHeight + 12 * layout.minPeek, lessThanOrEqualTo(layout.tableauHeight + 1));
  });

  testWidgets('face card shows corner rank and suit', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PlayingCardView(width: 80, height: 112, card: Card('7H', true)),
        ),
      ),
    );
    expect(find.text('7'), findsWidgets);
    expect(find.text('♥'), findsWidgets);
  });

  testWidgets('ten keeps two-digit rank', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PlayingCardView(width: 80, height: 112, card: Card('10S', true)),
        ),
      ),
    );
    expect(find.text('10'), findsWidgets);
    expect(find.text('1'), findsNothing);
  });

  test('nine and ten pip counts', () {
    expect(pipsFor(9).length, 9);
    expect(pipsFor(10).length, 10);
  });

  testWidgets('empty slot has no rank', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: PlayingCardView(width: 80, height: 112, empty: true),
        ),
      ),
    );
    expect(find.text('A'), findsNothing);
    expect(find.text('♠'), findsNothing);
  });
}
