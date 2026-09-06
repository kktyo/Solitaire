import 'package:flutter_test/flutter_test.dart';
import 'package:solitaire/api/models.dart';
import 'package:solitaire/game/application/session.dart';
import 'package:solitaire/game/domain/rules.dart';

void main() {
  test('does not enqueue a second HTTP move while sending', () async {
    final controller = GameSessionController();
    var calls = 0;
    late GameSession session;
    session = GameSession(
      server: GameDto(
        gameId: 'g1',
        status: 'IN_PROGRESS',
        version: 0,
        moveCount: 0,
        elapsedMs: 0,
        timingStartedAt: DateTime.parse('2026-09-06T00:00:00.000Z'),
        canUndo: false,
        stalemate: false,
        board: Board(
          tableau: List.generate(7, (_) => <Card>[]),
          foundations: List.generate(4, (_) => <Card>[]),
          stock: [Card('3C', false), Card('4C', false)],
          waste: [],
        ).toJson(),
        startedAt: DateTime.parse('2026-09-06T00:00:00.000Z'),
        updatedAt: DateTime.parse('2026-09-06T00:00:00.000Z'),
        clearedAt: null,
      ),
    );

    Future<void> playLocked(Move move) async {
      if (session.sending) {
        return;
      }
      final result = Rules.apply(session.displayBoard, move);
      expect(result.legal, isTrue);
      session = session.copyWith(optimisticBoard: result.board, pending: move);
      calls++;
    }

    await playLocked(Move.draw());
    await playLocked(Move.draw());
    expect(calls, 1);
    expect(controller, isNotNull);
  });
}
