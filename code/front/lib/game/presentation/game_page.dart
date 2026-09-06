import 'dart:async';

import 'package:flutter/material.dart' hide Card;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../application/session.dart';
import '../domain/rules.dart';
import 'playing_card.dart';

class GamePage extends ConsumerStatefulWidget {
  const GamePage({super.key});

  @override
  ConsumerState<GamePage> createState() => _GamePageState();
}

class _GamePageState extends ConsumerState<GamePage> with WidgetsBindingObserver, TickerProviderStateMixin {
  Timer? _tick;
  int? _stalemateShownVersion;
  int? _dragCol;
  int? _dragIndex;

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
      } else if (session.server.status == 'IN_PROGRESS' &&
          session.server.stalemate &&
          session.server.version != _stalemateShownVersion &&
          mounted) {
        _stalemateShownVersion = session.server.version;
        showDialog<void>(
          context: context,
          builder: (c) => AlertDialog(
            title: const Text('詰みです'),
            content: const Text('置ける手がありません。アンドゥするかやり直してください。'),
            actions: [
              TextButton(
                onPressed: session.server.canUndo
                    ? () {
                        Navigator.pop(c);
                        ref.read(gameSessionProvider.notifier).undo();
                      }
                    : null,
                child: const Text('アンドゥ'),
              ),
              FilledButton(
                onPressed: () {
                  Navigator.pop(c);
                  _restart();
                },
                child: const Text('やり直し'),
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
          Expanded(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final maxN = board.tableau.fold<int>(1, (m, col) => col.length > m ? col.length : m);
                final layout = BoardLayout.of(
                  viewport: Size(constraints.maxWidth, constraints.maxHeight),
                  maxTableauCount: maxN,
                );
                return Center(
                  child: SizedBox(
                    width: layout.boardWidth,
                    child: Column(
                      children: [
                        Padding(
                          padding: const EdgeInsets.all(8),
                          child: Row(
                            children: [
                              for (var i = 0; i < 4; i++)
                                _pileSlot(
                                  layout,
                                  Location(Pile.foundation, i),
                                  board.foundations[Suit.values[i]]!,
                                  session,
                                  draggable: true,
                                ),
                              const Spacer(),
                              GestureDetector(
                                onTap: () => ref.read(gameSessionProvider.notifier).tapStock(),
                                child: PlayingCardView(
                                  width: layout.cardWidth,
                                  height: layout.cardHeight,
                                  empty: board.stock.isEmpty,
                                  facedown: board.stock.isNotEmpty,
                                ),
                              ),
                              const SizedBox(width: 8),
                              _pileSlot(layout, Location(Pile.waste, 0), board.waste, session, draggable: true),
                            ],
                          ),
                        ),
                        Expanded(
                          child: LayoutBuilder(
                            builder: (context, table) {
                              final tableLayout = BoardLayout(
                                boardWidth: layout.boardWidth,
                                cardWidth: layout.cardWidth,
                                cardHeight: layout.cardHeight,
                                minPeek: layout.minPeek,
                                preferredPeek: layout.preferredPeek,
                                tableauHeight: table.maxHeight,
                              );
                              return Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  for (var col = 0; col < 7; col++)
                                    Expanded(
                                      child: _tableauColumn(col, board.tableau[col], tableLayout, session),
                                    ),
                                ],
                              );
                            },
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  bool _willAccept(Move incoming, Location to, GameSession session) {
    if (session.sending) return false;
    return Rules.isLegal(session.displayBoard, Move.relocate(incoming.from!, to, incoming.count));
  }

  void _drop(Move incoming, Location to) {
    ref.read(gameSessionProvider.notifier).play(Move.relocate(incoming.from!, to, incoming.count));
  }

  void _flyBack(Offset from, Offset to, Widget feedback) {
    final overlay = Overlay.of(context, rootOverlay: true);
    final controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 250));
    final anim = Tween<Offset>(begin: from, end: to).animate(CurvedAnimation(parent: controller, curve: Curves.easeOut));
    late OverlayEntry entry;
    entry = OverlayEntry(
      builder: (_) => AnimatedBuilder(
        animation: anim,
        builder: (_, __) => Positioned(
          left: anim.value.dx,
          top: anim.value.dy,
          child: IgnorePointer(child: feedback),
        ),
      ),
    );
    overlay.insert(entry);
    controller.forward().whenComplete(() {
      entry.remove();
      controller.dispose();
    });
  }

  Widget _tableauColumn(int col, List<Card> cards, BoardLayout layout, GameSession session) {
    final peek = layout.peekFor(cards.length);
    return DragTarget<Move>(
      onWillAcceptWithDetails: (d) => _willAccept(d.data, Location(Pile.tableau, col), session),
      onAcceptWithDetails: (d) => _drop(d.data, Location(Pile.tableau, col)),
      builder: (c, cand, rej) {
        if (cards.isEmpty) {
          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 2),
            child: PlayingCardView(width: layout.cardWidth, height: layout.cardHeight, empty: true),
          );
        }
        return SizedBox(
          height: layout.cardHeight + peek * (cards.length - 1),
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              for (var i = 0; i < cards.length; i++)
                Positioned(
                  top: i * peek,
                  left: 2,
                  right: 2,
                  child: _tableauCard(col, i, cards, layout, session),
                ),
            ],
          ),
        );
      },
    );
  }

  Widget _tableauCard(int col, int i, List<Card> cards, BoardLayout layout, GameSession session) {
    final live = session.displayBoard;
    final movable = Rules.movableTableauCount(live, col, i);
    final card = cards[i];
    final hiding = _dragCol == col && _dragIndex != null && i >= _dragIndex!;
    final face = PlayingCardView(
      width: layout.cardWidth,
      height: layout.cardHeight,
      card: card,
    );
    if (!card.faceUp || movable == 0 || session.sending) {
      return face;
    }
    final origin = Location(Pile.tableau, col);
    final data = Move.relocate(origin, origin, movable);
    final feedback = _stackFeedback(cards.sublist(i), layout);
    return _returnDraggable(
      data: data,
      feedback: feedback,
      childWhenDragging: Opacity(opacity: hiding ? 0 : 0.3, child: face),
      child: face,
      onStart: () => setState(() {
        _dragCol = col;
        _dragIndex = i;
      }),
      onEnd: () => setState(() {
        _dragCol = null;
        _dragIndex = null;
      }),
    );
  }

  Widget _stackFeedback(List<Card> cards, BoardLayout layout) {
    final peek = layout.minPeek;
    return Material(
      color: Colors.transparent,
      child: SizedBox(
        width: layout.cardWidth,
        height: layout.cardHeight + peek * (cards.length - 1),
        child: Stack(
          children: [
            for (var k = 0; k < cards.length; k++)
              Positioned(
                top: k * peek,
                child: PlayingCardView(
                  width: layout.cardWidth,
                  height: layout.cardHeight,
                  card: cards[k],
                  highlight: true,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _pileSlot(BoardLayout layout, Location loc, List<Card> cards, GameSession session, {required bool draggable}) {
    final top = cards.isEmpty ? null : cards.last;
    Widget face = PlayingCardView(
      width: layout.cardWidth,
      height: layout.cardHeight,
      card: top,
      empty: top == null,
    );
    if (draggable && top != null && !session.sending) {
      final data = Move.relocate(loc, loc, 1);
      final feedback = Material(
        color: Colors.transparent,
        child: PlayingCardView(width: layout.cardWidth, height: layout.cardHeight, card: top, highlight: true),
      );
      face = _returnDraggable(
        data: data,
        feedback: feedback,
        childWhenDragging: Opacity(opacity: 0.3, child: face),
        child: face,
      );
    }
    return DragTarget<Move>(
      onWillAcceptWithDetails: (d) => _willAccept(d.data, loc, session),
      onAcceptWithDetails: (d) => _drop(d.data, loc),
      builder: (c, a, r) => Padding(padding: const EdgeInsets.symmetric(horizontal: 2), child: face),
    );
  }

  Widget _returnDraggable({
    required Move data,
    required Widget feedback,
    required Widget childWhenDragging,
    required Widget child,
    VoidCallback? onStart,
    VoidCallback? onEnd,
  }) {
    Offset origin = Offset.zero;
    return Builder(
      builder: (ctx) {
        return Draggable<Move>(
          data: data,
          feedback: feedback,
          childWhenDragging: childWhenDragging,
          onDragStarted: () {
            final box = ctx.findRenderObject() as RenderBox?;
            if (box != null && box.hasSize) {
              origin = box.localToGlobal(Offset.zero);
            }
            onStart?.call();
          },
          onDragEnd: (details) {
            onEnd?.call();
            if (!details.wasAccepted && mounted) {
              _flyBack(details.offset, origin, feedback);
            }
          },
          child: child,
        );
      },
    );
  }
}
