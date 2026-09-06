import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../api/client.dart';
import '../api/models.dart';
import '../game/application/session.dart';

class HomePage extends ConsumerStatefulWidget {
  const HomePage({super.key});

  @override
  ConsumerState<HomePage> createState() => _HomePageState();
}

class _HomePageState extends ConsumerState<HomePage> {
  GameDto? current;
  ResultSummary? latest;
  String? error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final api = ref.read(apiClientProvider);
    try {
      final g = await api.currentGame();
      final r = await api.latestResult();
      setState(() {
        current = g;
        latest = r;
        error = null;
      });
    } on ApiException catch (e) {
      setState(() => error = e.message);
    }
  }

  String _fmt(int ms) {
    final s = ms ~/ 1000;
    final h = s ~/ 3600;
    final m = (s % 3600) ~/ 60;
    final sec = s % 60;
    if (h > 0) {
      return '$h:${m.toString().padLeft(2, '0')}:${sec.toString().padLeft(2, '0')}';
    }
    return '$m:${sec.toString().padLeft(2, '0')}';
  }

  Future<void> _continue() async {
    if (current == null) return;
    final api = ref.read(apiClientProvider);
    final g = await api.resume(current!.gameId);
    ref.read(gameSessionProvider.notifier).load(g);
    if (mounted) context.go('/game');
  }

  Future<void> _newGame() async {
    final api = ref.read(apiClientProvider);
    if (current != null) {
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
    }
    final g = await api.createGame(abandonExisting: current != null);
    ref.read(gameSessionProvider.notifier).load(g);
    if (mounted) context.go('/game');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('ソリティア'),
        actions: [
          TextButton(
            onPressed: () async {
              await ref.read(apiClientProvider).logout();
              ref.read(authLoggedInProvider.notifier).state = false;
              if (context.mounted) context.go('/login');
            },
            child: const Text('ログアウト'),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (error != null) Text(error!, style: const TextStyle(color: Colors.red)),
            FilledButton(onPressed: current == null ? null : _continue, child: const Text('続きから')),
            const SizedBox(height: 12),
            Semantics(
              identifier: 'new-game',
              button: true,
              label: '新しいゲーム',
              child: FilledButton(onPressed: _newGame, child: const Text('新しいゲーム')),
            ),
            const SizedBox(height: 24),
            if (latest != null)
              Text(
                '直近クリア: ${_fmt(latest!.elapsedMs)} / ${latest!.moveCount}手\n${latest!.clearedAt.toLocal()}',
              ),
          ],
        ),
      ),
    );
  }
}
