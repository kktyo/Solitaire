import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/client.dart';
import '../domain/rules.dart';

final apiClientProvider = Provider<ApiClient>((ref) => ApiClient(onAuthLost: () {
      ref.read(authLoggedInProvider.notifier).state = false;
    }));

final authLoggedInProvider = StateProvider<bool>((ref) => false);

class GameSession {
  GameSession({
    required this.server,
    this.optimisticBoard,
    this.pending,
    this.toast,
    this.error,
    this.autoPlaying = false,
  });

  final GameDto server;
  final Board? optimisticBoard;
  final Move? pending;
  final String? toast;
  final String? error;
  final bool autoPlaying;

  Board get displayBoard => optimisticBoard ?? Board.fromJson(server.board);
  bool get sending => pending != null;
  bool get busy => pending != null || autoPlaying;

  GameSession copyWith({
    GameDto? server,
    Board? optimisticBoard,
    Move? pending,
    String? toast,
    String? error,
    bool? autoPlaying,
    bool clearOptimistic = false,
    bool clearPending = false,
    bool clearToast = false,
    bool clearError = false,
  }) {
    return GameSession(
      server: server ?? this.server,
      optimisticBoard: clearOptimistic ? null : (optimisticBoard ?? this.optimisticBoard),
      pending: clearPending ? null : (pending ?? this.pending),
      toast: clearToast ? null : (toast ?? this.toast),
      error: clearError ? null : (error ?? this.error),
      autoPlaying: autoPlaying ?? this.autoPlaying,
    );
  }
}

class GameSessionController extends Notifier<GameSession?> {
  bool _auto = false;
  bool _skipAuto = false;

  @override
  GameSession? build() => null;

  ApiClient get _api => ref.read(apiClientProvider);

  void load(GameDto game) {
    state = GameSession(server: game);
    Future.microtask(_autoComplete);
  }

  Future<void> play(Move move) async {
    if (state?.busy == true) {
      return;
    }
    _skipAuto = false;
    final ok = await _submit(move);
    if (ok) {
      await _autoComplete();
    }
  }

  Future<bool> _submit(Move move) async {
    final current = state;
    if (current == null || current.sending) {
      return false;
    }
    final board = current.displayBoard;
    final result = Rules.apply(board, move);
    if (!result.legal) {
      state = current.copyWith(toast: 'その移動はできません。');
      return false;
    }
    state = current.copyWith(
      optimisticBoard: result.board,
      pending: move,
      clearToast: true,
      clearError: true,
    );
    try {
      final next = await _api.applyMove(current.server.gameId, current.server.version, move.toJson());
      state = GameSession(server: next, autoPlaying: _auto);
      return true;
    } on ApiException catch (e) {
      if (e.status == 409) {
        final g = e.details['game'];
        if (g is Map<String, dynamic>) {
          state = GameSession(server: GameDto.fromJson(g), autoPlaying: _auto);
        }
        return false;
      }
      if (e.status == 422) {
        state = GameSession(server: current.server, toast: e.message, autoPlaying: _auto);
        return false;
      }
      state = GameSession(server: current.server, error: e.message, autoPlaying: _auto);
      return false;
    }
  }

  Future<void> _autoComplete() async {
    if (_auto || _skipAuto) {
      return;
    }
    _auto = true;
    final started = state;
    if (started != null) {
      state = started.copyWith(autoPlaying: true);
    }
    try {
      var idle = 0;
      while (true) {
        final current = state;
        if (current == null || current.sending || current.server.status != 'IN_PROGRESS') {
          break;
        }
        final b = current.displayBoard;
        if (!Rules.tableauAllFaceUp(b) || Rules.isCleared(b)) {
          break;
        }
        final m = Rules.nextAutoMove(b);
        if (m == null) {
          break;
        }
        if (m.type != MoveType.move) {
          idle++;
          if (idle > 52) {
            break;
          }
        } else {
          idle = 0;
        }
        final ok = await _submit(m);
        if (!ok) {
          break;
        }
      }
    } finally {
      _auto = false;
      final end = state;
      if (end != null && end.autoPlaying) {
        state = end.copyWith(autoPlaying: false);
      }
    }
  }

  Future<void> undo() async {
    final current = state;
    if (current == null || current.busy || !current.server.canUndo) return;
    _skipAuto = true;
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
    if (current == null || current.busy) return;
    final b = current.displayBoard;
    if (b.stock.isNotEmpty) {
      play(Move.draw());
    } else if (b.waste.isNotEmpty) {
      play(Move.recycle());
    }
  }
}

final gameSessionProvider = NotifierProvider<GameSessionController, GameSession?>(GameSessionController.new);
