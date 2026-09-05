import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/client.dart';
import '../api/models.dart';
import '../game/domain/rules.dart';

final apiClientProvider = Provider<ApiClient>((ref) => ApiClient(onAuthLost: () {
      ref.read(authLoggedInProvider.notifier).state = false;
    }));

final authLoggedInProvider = StateProvider<bool>((ref) => false);

class GameSession {
  GameSession({
    required this.server,
    this.optimisticBoard,
    this.pending,
    this.selected,
    this.toast,
    this.error,
  });

  final GameDto server;
  final Board? optimisticBoard;
  final Move? pending;
  final Selection? selected;
  final String? toast;
  final String? error;

  Board get displayBoard => optimisticBoard ?? Board.fromJson(server.board);
  bool get sending => pending != null;

  GameSession copyWith({
    GameDto? server,
    Board? optimisticBoard,
    Move? pending,
    Selection? selected,
    String? toast,
    String? error,
    bool clearOptimistic = false,
    bool clearPending = false,
    bool clearSelected = false,
    bool clearToast = false,
    bool clearError = false,
  }) {
    return GameSession(
      server: server ?? this.server,
      optimisticBoard: clearOptimistic ? null : (optimisticBoard ?? this.optimisticBoard),
      pending: clearPending ? null : (pending ?? this.pending),
      selected: clearSelected ? null : (selected ?? this.selected),
      toast: clearToast ? null : (toast ?? this.toast),
      error: clearError ? null : (error ?? this.error),
    );
  }
}

class Selection {
  Selection(this.from, this.count);
  final Location from;
  final int count;
}

class GameSessionController extends Notifier<GameSession?> {
  @override
  GameSession? build() => null;

  ApiClient get _api => ref.read(apiClientProvider);

  void load(GameDto game) {
    state = GameSession(server: game);
  }

  Future<void> play(Move move) async {
    final current = state;
    if (current == null || current.sending) {
      return;
    }
    final board = current.displayBoard;
    final result = Rules.apply(board, move);
    if (!result.legal) {
      state = current.copyWith(toast: 'その移動はできません。');
      return;
    }
    state = current.copyWith(
      optimisticBoard: result.board,
      pending: move,
      clearSelected: true,
      clearToast: true,
      clearError: true,
    );
    try {
      final next = await _api.applyMove(current.server.gameId, current.server.version, move.toJson());
      state = GameSession(server: next);
    } on ApiException catch (e) {
      if (e.status == 409) {
        final g = e.details['game'];
        if (g is Map<String, dynamic>) {
          state = GameSession(server: GameDto.fromJson(g));
          return;
        }
      }
      if (e.status == 422) {
        state = GameSession(server: current.server, toast: e.message);
        return;
      }
      state = GameSession(server: current.server, error: e.message);
    }
  }

  Future<void> undo() async {
    final current = state;
    if (current == null || current.sending || !current.server.canUndo) return;
    try {
      state = current.copyWith(pending: Move.draw(), clearToast: true);
      final next = await _api.undo(current.server.gameId, current.server.version);
      state = GameSession(server: next);
    } on ApiException catch (e) {
      state = GameSession(server: current.server, toast: e.message);
    }
  }

  void tapStock() {
    final current = state;
    if (current == null) return;
    final b = current.displayBoard;
    if (b.stock.isNotEmpty) {
      play(Move.draw());
    } else if (b.waste.isNotEmpty) {
      play(Move.recycle());
    }
  }

  void selectOrMove(Location loc, int count) {
    final current = state;
    if (current == null || current.sending) return;
    final sel = current.selected;
    if (sel == null) {
      state = current.copyWith(selected: Selection(loc, count));
      return;
    }
    if (sel.from.pile == loc.pile && sel.from.index == loc.index) {
      state = current.copyWith(clearSelected: true);
      return;
    }
    play(Move.relocate(sel.from, loc, sel.count));
  }
}

final gameSessionProvider = NotifierProvider<GameSessionController, GameSession?>(GameSessionController.new);
