import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'models.dart';

export 'models.dart';

const apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://127.0.0.1:8080');

class ApiClient {
  ApiClient({Dio? dio, FlutterSecureStorage? storage, void Function()? onAuthLost})
      : _storage = storage ?? const FlutterSecureStorage(),
        _onAuthLost = onAuthLost {
    _dio = dio ??
        Dio(BaseOptions(
          baseUrl: '$apiBaseUrl/api/v1',
          connectTimeout: const Duration(seconds: 15),
          receiveTimeout: const Duration(seconds: 15),
        ));
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        if (!_anonymous(options.path)) {
          final access = await _storage.read(key: 'access');
          if (access != null) {
            options.headers['Authorization'] = 'Bearer $access';
          }
        }
        handler.next(options);
      },
      onError: (err, handler) async {
        final path = err.requestOptions.path;
        if (err.response?.statusCode == 401 && !_anonymous(path) && !_refreshing) {
          try {
            await _refreshOnce();
            final req = err.requestOptions;
            final access = await _storage.read(key: 'access');
            req.headers['Authorization'] = 'Bearer $access';
            final clone = await _dio.fetch(req);
            return handler.resolve(clone);
          } catch (_) {
            await clearTokens();
            _onAuthLost?.call();
          }
        }
        handler.next(err);
      },
    ));
  }

  late final Dio _dio;
  final FlutterSecureStorage _storage;
  final void Function()? _onAuthLost;
  Completer<void>? _refreshingLock;
  bool _refreshing = false;

  static bool _anonymous(String path) =>
      path.contains('/auth/register') || path.contains('/auth/login') || path.contains('/auth/refresh');

  Future<void> saveTokens(AuthTokens t) async {
    await _storage.write(key: 'access', value: t.accessToken);
    await _storage.write(key: 'refresh', value: t.refreshToken);
  }

  Future<void> clearTokens() async {
    await _storage.delete(key: 'access');
    await _storage.delete(key: 'refresh');
  }

  Future<bool> hasAccess() async => (await _storage.read(key: 'access')) != null;

  Future<void> _refreshOnce() async {
    if (_refreshingLock != null) {
      return _refreshingLock!.future;
    }
    _refreshing = true;
    _refreshingLock = Completer();
    try {
      final refresh = await _storage.read(key: 'refresh');
      if (refresh == null) {
        throw ApiException(401, 'UNAUTHORIZED', '未ログインです。');
      }
      final res = await _dio.post('/auth/refresh', data: {'refreshToken': refresh});
      final tokens = AuthTokens.fromJson(Map<String, dynamic>.from(res.data as Map));
      await saveTokens(tokens);
      _refreshingLock!.complete();
    } catch (e) {
      _refreshingLock!.completeError(e);
      rethrow;
    } finally {
      _refreshing = false;
      _refreshingLock = null;
    }
  }

  Future<AuthTokens> register(String email, String password) async {
    return _authPost('/auth/register', email, password);
  }

  Future<AuthTokens> login(String email, String password) async {
    return _authPost('/auth/login', email, password);
  }

  Future<AuthTokens> _authPost(String path, String email, String password) async {
    try {
      final res = await _dio.post(path, data: {'email': email, 'password': password});
      final tokens = AuthTokens.fromJson(Map<String, dynamic>.from(res.data as Map));
      await saveTokens(tokens);
      return tokens;
    } on DioException catch (e) {
      throw _wrap(e);
    }
  }

  Future<void> logout() async {
    final refresh = await _storage.read(key: 'refresh');
    try {
      await _dio.post('/auth/logout', data: {'refreshToken': refresh});
    } catch (_) {}
    await clearTokens();
  }

  Future<GameDto?> currentGame() async {
    try {
      final res = await _dio.get('/games/current');
      return GameDto.fromJson(Map<String, dynamic>.from(res.data as Map));
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) return null;
      throw _wrap(e);
    }
  }

  Future<GameDto> resume(String gameId) async {
    try {
      final res = await _dio.post('/games/$gameId/resume');
      return GameDto.fromJson(Map<String, dynamic>.from(res.data as Map));
    } on DioException catch (e) {
      throw _wrap(e);
    }
  }

  Future<void> pause(String gameId) async {
    try {
      await _dio.post('/games/$gameId/pause');
    } catch (_) {}
  }

  Future<GameDto> createGame({required bool abandonExisting}) async {
    try {
      final res = await _dio.post('/games', data: {'abandonExisting': abandonExisting});
      return GameDto.fromJson(Map<String, dynamic>.from(res.data as Map));
    } on DioException catch (e) {
      throw _wrap(e);
    }
  }

  Future<GameDto> applyMove(String gameId, int version, Map<String, dynamic> move) async {
    try {
      final res = await _dio.post('/games/$gameId/moves', data: {'version': version, 'move': move});
      return GameDto.fromJson(Map<String, dynamic>.from(res.data as Map));
    } on DioException catch (e) {
      throw _wrap(e);
    }
  }

  Future<GameDto> undo(String gameId, int version) async {
    try {
      final res = await _dio.post('/games/$gameId/undo', data: {'version': version});
      return GameDto.fromJson(Map<String, dynamic>.from(res.data as Map));
    } on DioException catch (e) {
      throw _wrap(e);
    }
  }

  Future<ResultSummary?> latestResult() async {
    try {
      final res = await _dio.get('/results/me', queryParameters: {'limit': 1});
      final items = (res.data as Map)['items'] as List;
      if (items.isEmpty) return null;
      return ResultSummary.fromJson(Map<String, dynamic>.from(items.first as Map));
    } on DioException catch (e) {
      throw _wrap(e);
    }
  }

  ApiException _wrap(DioException e) {
    final data = e.response?.data;
    if (data is Map) {
      return ApiException.fromBody(e.response?.statusCode ?? 500, Map<String, dynamic>.from(data));
    }
    return ApiException(e.response?.statusCode ?? 500, 'INTERNAL_ERROR', '通信に失敗しました。');
  }
}
