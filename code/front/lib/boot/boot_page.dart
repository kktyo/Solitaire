import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../api/client.dart';
import '../game/application/session.dart';

class BootPage extends ConsumerStatefulWidget {
  const BootPage({super.key});

  @override
  ConsumerState<BootPage> createState() => _BootPageState();
}

class _BootPageState extends ConsumerState<BootPage> {
  String? error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _go());
  }

  Future<void> _go() async {
    final api = ref.read(apiClientProvider);
    try {
      if (!await api.hasAccess()) {
        if (mounted) context.go('/login');
        return;
      }
      ref.read(authLoggedInProvider.notifier).state = true;
      final game = await api.currentGame();
      if (!mounted) return;
      if (game != null) {
        final resumed = await api.resume(game.gameId);
        ref.read(gameSessionProvider.notifier).load(resumed);
        context.go('/game');
      } else {
        context.go('/home');
      }
    } catch (_) {
      setState(() => error = '起動に失敗しました。');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (error != null) {
      return Scaffold(
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(error!),
              FilledButton(onPressed: _go, child: const Text('再試行')),
            ],
          ),
        ),
      );
    }
    return const Scaffold(body: Center(child: CircularProgressIndicator()));
  }
}
