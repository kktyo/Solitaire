import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../api/client.dart';
import '../game/application/session.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final email = TextEditingController();
  final password = TextEditingController();
  String? error;
  bool busy = false;

  @override
  void dispose() {
    email.dispose();
    password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => error = null);
    if (email.text.isEmpty || !email.text.contains('@')) {
      setState(() => error = 'メールアドレスを入力してください。');
      return;
    }
    if (password.text.length < 8) {
      setState(() => error = 'パスワードは8文字以上です。');
      return;
    }
    setState(() => busy = true);
    try {
      final api = ref.read(apiClientProvider);
      await api.login(email.text, password.text);
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
    } on ApiException catch (e) {
      setState(() => error = e.message);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('ログイン')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            Semantics(
              identifier: 'email',
              textField: true,
              child: TextField(
                controller: email,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(labelText: 'メール'),
              ),
            ),
            Semantics(
              identifier: 'password',
              textField: true,
              child: TextField(
                controller: password,
                obscureText: true,
                decoration: const InputDecoration(labelText: 'パスワード'),
              ),
            ),
            if (error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(error!, style: const TextStyle(color: Colors.red))),
            const SizedBox(height: 16),
            FilledButton(onPressed: busy ? null : _submit, child: const Text('ログイン')),
            TextButton(onPressed: () => context.go('/register'), child: const Text('登録')),
          ],
        ),
      ),
    );
  }
}
