import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'auth/login_page.dart';
import 'auth/register_page.dart';
import 'boot/boot_page.dart';
import 'game/presentation/game_page.dart';
import 'home/home_page.dart';

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/boot',
    routes: [
      GoRoute(path: '/boot', builder: (c, s) => const BootPage()),
      GoRoute(path: '/login', builder: (c, s) => const LoginPage()),
      GoRoute(path: '/register', builder: (c, s) => const RegisterPage()),
      GoRoute(path: '/home', builder: (c, s) => const HomePage()),
      GoRoute(path: '/game', builder: (c, s) => const GamePage()),
    ],
  );
});

class SolitaireApp extends ConsumerWidget {
  const SolitaireApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    return MaterialApp.router(
      title: 'ソリティア',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1B5E20)),
        useMaterial3: true,
      ),
      routerConfig: router,
    );
  }
}
