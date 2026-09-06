import 'dart:async';

import 'package:flutter/material.dart' hide Card;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../api/client.dart';
import '../application/session.dart';
import '../domain/rules.dart';

class GamePage extends ConsumerStatefulWidget {
  const GamePage({super.key});

  @override
  ConsumerState<GamePage> createState() => _GamePageState();
}

class _GamePageState extends ConsumerState<GamePage> with WidgetsBindingObserver {
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _tick = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _tick?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final session = ref.read(gameSessionProvider);
    if (session == null) return;
    final api = ref.read(apiClientProvider);
    if (state == AppLifecycleState.paused) {
      api.pause(session.server.gameId);
    } else if (state == AppLifecycleState.resumed) {
      api.resume(session.server.gameId).then((g) {
        if (mounted) ref.read(gameSessionProvider.notifier).load(g);
      }).catchError((_) {});
    }
  }

  String _clock(GameSession s) {
    var ms = s.server.elapsedMs;
    final started = s.server.timingStartedAt;
    if (started != null) {
      ms += DateTime.now().toUtc().difference(started.toUtc()).inMilliseconds;
    }
    final sec = ms ~/ 1000;
    final h = sec ~/ 3600;
    final m = (sec % 3600) ~/ 60;
    final ss = sec % 60;
    if (h > 0) {
      return '$h:${m.toString().padLeft(2, '0')}:${ss.toString().padLeft(2, '0')}';
    }
    return '$m:${ss.toString().padLeft(2, '0')}';
  }

  Future<void> _restart() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('確認'),
        content: const Text('進行中のゲームは破棄されます'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('キャンセル')),
          FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('実行')),
        ],
      ),
    );
    if (ok != true) return;
    final g = await ref.read(apiClientProvider).createGame(abandonExisting: true);
    ref.read(gameSessionProvider.notifier).load(g);
  }

  Future<void> _home() async {
    final s = ref.read(gameSessionProvider);
    if (s != null) {
      await ref.read(apiClientProvider).pause(s.server.gameId);
    }
    if (mounted) context.go('/home');
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(gameSessionProvider);
    if (session == null) {
      return const Scaffold(body: Center(child: Text('対局がありません')));
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (session.server.status == 'CLEARED' && mounted) {
        showDialog<void>(
          context: context,
          barrierDismissible: false,
          builder: (c) => AlertDialog(
            title: const Text('クリア'),
            content: Text('${session.server.moveCount}手 / ${_clock(session)}'),
            actions: [
              TextButton(
                onPressed: () {
                  Navigator.pop(c);
                  context.go('/home');
                },
                child: const Text('ホーム'),
              ),
              FilledButton(
                onPressed: () async {
                  Navigator.pop(c);
                  final g = await ref.read(apiClientProvider).createGame(abandonExisting: false);
                  ref.read(gameSessionProvider.notifier).load(g);
                },
                child: const Text('もう一度'),
              ),
            ],
          ),
        );
      }
      if (session.error != null) {
        showDialog<void>(
          context: context,
          builder: (c) => AlertDialog(
            title: const Text('通信エラー'),
            content: Text(session.error!),
            actions: [
              TextButton(
                onPressed: () async {
                  Navigator.pop(c);
                  final g = await ref.read(apiClientProvider).currentGame();
                  if (g != null) ref.read(gameSessionProvider.notifier).load(g);
                },
                child: const Text('再試行'),
              ),
              TextButton(
                onPressed: () {
                  Navigator.pop(c);
                  context.go('/home');
                },
                child: const Text('ホーム'),
              ),
            ],
          ),
        );
      }
    });

    final board = session.displayBoard;
    final w = MediaQuery.sizeOf(context).width;
    final cardW = (w / 7) - 4;
    final cardH = cardW * 1.4;

    return Scaffold(
      backgroundColor: const Color(0xFF0D4F1C),
      appBar: AppBar(
        backgroundColor: const Color(0xFF083615),
        foregroundColor: Colors.white,
        title: Text('${_clock(session)}  ${session.server.moveCount}手'),
        leading: IconButton(onPressed: session.sending ? null : _home, icon: const Icon(Icons.home)),
        actions: [
          IconButton(
            onPressed: session.sending || !session.server.canUndo
                ? null
                : () => ref.read(gameSessionProvider.notifier).undo(),
            icon: const Icon(Icons.undo),
          ),
          IconButton(onPressed: session.sending ? null : _restart, icon: const Icon(Icons.refresh)),
        ],
      ),
      body: Column(
        children: [
          if (session.toast != null)
            MaterialBanner(
              content: Text(session.toast!),
              actions: [TextButton(onPressed: () {}, child: const Text(''))],
            ),
          Padding(
            padding: const EdgeInsets.all(8),
            child: Row(
              children: [
                for (var i = 0; i < 4; i++)
                  _pileTarget(
                    cardW,
                    cardH,
                    Location(Pile.foundation, i),
                    board.foundations[Suit.values[i]]!,
                    session,
                  ),
                const Spacer(),
                GestureDetector(
                  onTap: () => ref.read(gameSessionProvider.notifier).tapStock(),
                  child: _cardFace(cardW, cardH, board.stock.isEmpty ? null : Card('back', false), back: true, empty: board.stock.isEmpty),
                ),
                const SizedBox(width: 8),
                _pileTarget(cardW, cardH, Location(Pile.waste, 0), board.waste, session, waste: true),
              ],
            ),
          ),
          Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (var col = 0; col < 7; col++)
                  Expanded(child: _tableauColumn(col, board.tableau[col], cardW, cardH, session)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _tableauColumn(int col, List<Card> cards, double w, double h, GameSession session) {
    final sel = session.selected;
    return DragTarget<Move>(
      onWillAcceptWithDetails: (d) => true,
      onAcceptWithDetails: (d) {
        ref.read(gameSessionProvider.notifier).play(Move.relocate(d.data.from!, Location(Pile.tableau, col), d.data.count));
      },
      builder: (c, cand, rej) {
        if (cards.isEmpty) {
          return GestureDetector(
            onTap: () => ref.read(gameSessionProvider.notifier).selectOrMove(Location(Pile.tableau, col), 1),
            child: Container(
              margin: const EdgeInsets.all(2),
              height: h,
              decoration: BoxDecoration(border: Border.all(color: Colors.white24), borderRadius: BorderRadius.circular(6)),
            ),
          );
        }
        return Stack(
          children: [
            for (var i = 0; i < cards.length; i++)
              Positioned(
                top: i * 22,
                left: 2,
                right: 2,
                child: _draggableCard(col, i, cards, w, h, session, sel),
              ),
          ],
        );
      },
    );
  }

  Widget _draggableCard(int col, int i, List<Card> cards, double w, double h, GameSession session, Selection? sel) {
    final live = session.displayBoard;
    final movable = Rules.movableTableauCount(live, col, i);
    final card = cards[i];
    final highlighted = sel != null && sel.from.pile == Pile.tableau && sel.from.index == col && i >= live.tableau[col].length - sel.count;
    final child = GestureDetector(
      onTap: () {
        if (movable > 0) {
          ref.read(gameSessionProvider.notifier).selectOrMove(Location(Pile.tableau, col), movable);
        }
      },
      child: _cardFace(w, h, card, highlight: highlighted),
    );
    if (!card.faceUp || movable == 0 || session.sending) return child;
    return Draggable<Move>(
      data: Move.relocate(Location(Pile.tableau, col), Location(Pile.tableau, col), movable),
      feedback: _cardFace(w, h, card, highlight: true),
      childWhenDragging: Opacity(opacity: 0.3, child: child),
      child: child,
    );
  }

  Widget _pileTarget(double w, double h, Location loc, List<Card> cards, GameSession session, {bool waste = false}) {
    final top = cards.isEmpty ? null : cards.last;
    final child = GestureDetector(
      onTap: () {
        if (waste && top != null) {
          ref.read(gameSessionProvider.notifier).selectOrMove(loc, 1);
        } else {
          ref.read(gameSessionProvider.notifier).selectOrMove(loc, 1);
        }
      },
      child: _cardFace(w, h, top, empty: top == null),
    );
    return DragTarget<Move>(
      onAcceptWithDetails: (d) {
        ref.read(gameSessionProvider.notifier).play(Move.relocate(d.data.from!, loc, d.data.count));
      },
      builder: (c, a, r) => Padding(padding: const EdgeInsets.symmetric(horizontal: 2), child: child),
    );
  }

  Widget _cardFace(double w, double h, Card? card, {bool back = false, bool empty = false, bool highlight = false}) {
    final color = empty
        ? Colors.white10
        : (card == null || back || !(card.faceUp))
            ? const Color(0xFF1A237E)
            : Colors.white;
    final textColor = card == null || !card.faceUp
        ? Colors.white
        : (card.red ? Colors.red : Colors.black);
    final label = card == null || !card.faceUp || back
        ? ''
        : '${_rank(card)}${_suit(card)}';
    return Container(
      width: w,
      height: h,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: highlight ? Colors.amber : Colors.black26, width: highlight ? 3 : 1),
      ),
      child: Text(label, style: TextStyle(color: textColor, fontWeight: FontWeight.bold, fontSize: 12)),
    );
  }

  String _rank(Card c) {
    const order = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
    return order[c.rank - 1];
  }

  String _suit(Card c) => switch (c.suit) { Suit.s => '♠', Suit.h => '♥', Suit.d => '♦', Suit.c => '♣' };
}
