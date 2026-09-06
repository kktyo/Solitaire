import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../api/client.dart';
import '../game/application/session.dart';

class RegisterPage extends ConsumerStatefulWidget {
  const RegisterPage({super.key});

  @override
  ConsumerState<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends ConsumerState<RegisterPage> {
  final email = TextEditingController();
  final password = TextEditingController();
  final confirm = TextEditingController();
  String? error;
  bool busy = false;

  @override
  void dispose() {
    email.dispose();
    password.dispose();
    confirm.dispose();
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
    if (password.text != confirm.text) {
      setState(() => error = '確認用パスワードが一致しません。');
      return;
    }
    setState(() => busy = true);
    try {
      final api = ref.read(apiClientProvider);
      await api.register(email.text, password.text);
      ref.read(authLoggedInProvider.notifier).state = true;
      if (mounted) context.go('/home');
    } on ApiException catch (e) {
      setState(() => error = e.message);
    } catch (_) {
      setState(() => error = '通信に失敗しました。');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('登録')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            Semantics(
              identifier: 'register-email',
              child: TextField(
                controller: email,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(labelText: 'メール'),
              ),
            ),
            Semantics(
              identifier: 'register-password',
              child: TextField(
                controller: password,
                obscureText: true,
                decoration: const InputDecoration(labelText: 'パスワード'),
              ),
            ),
            Semantics(
              identifier: 'register-password-confirm',
              child: TextField(
                controller: confirm,
                obscureText: true,
                decoration: const InputDecoration(labelText: 'パスワード確認'),
              ),
            ),
            if (error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(error!, style: const TextStyle(color: Colors.red))),
            const SizedBox(height: 16),
            FilledButton(onPressed: busy ? null : _submit, child: const Text('登録')),
            TextButton(onPressed: () => context.go('/login'), child: const Text('ログインへ')),
          ],
        ),
      ),
    );
  }
}
