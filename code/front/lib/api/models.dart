class ApiException implements Exception {
  ApiException(this.status, this.code, this.message, [this.details = const {}]);
  final int status;
  final String code;
  final String message;
  final Map<String, dynamic> details;

  factory ApiException.fromBody(int status, Map<String, dynamic> body) {
    return ApiException(
      status,
      body['code'] as String? ?? 'INTERNAL_ERROR',
      body['message'] as String? ?? 'エラーが発生しました。',
      Map<String, dynamic>.from(body['details'] as Map? ?? {}),
    );
  }
}

class GameDto {
  GameDto({
    required this.gameId,
    required this.status,
    required this.version,
    required this.moveCount,
    required this.elapsedMs,
    required this.timingStartedAt,
    required this.canUndo,
    required this.board,
    required this.startedAt,
    required this.updatedAt,
    required this.clearedAt,
  });

  final String gameId;
  final String status;
  final int version;
  final int moveCount;
  final int elapsedMs;
  final DateTime? timingStartedAt;
  final bool canUndo;
  final Map<String, dynamic> board;
  final DateTime startedAt;
  final DateTime updatedAt;
  final DateTime? clearedAt;

  factory GameDto.fromJson(Map<String, dynamic> json) {
    DateTime? parse(dynamic v) => v == null ? null : DateTime.parse(v as String);
    return GameDto(
      gameId: json['gameId'] as String,
      status: json['status'] as String,
      version: json['version'] as int,
      moveCount: json['moveCount'] as int,
      elapsedMs: json['elapsedMs'] as int,
      timingStartedAt: parse(json['timingStartedAt']),
      canUndo: json['canUndo'] as bool? ?? false,
      board: Map<String, dynamic>.from(json['board'] as Map),
      startedAt: DateTime.parse(json['startedAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
      clearedAt: parse(json['clearedAt']),
    );
  }
}

class ResultSummary {
  ResultSummary({required this.gameId, required this.moveCount, required this.elapsedMs, required this.clearedAt});
  final String gameId;
  final int moveCount;
  final int elapsedMs;
  final DateTime clearedAt;

  factory ResultSummary.fromJson(Map<String, dynamic> json) => ResultSummary(
        gameId: json['gameId'] as String,
        moveCount: json['moveCount'] as int,
        elapsedMs: json['elapsedMs'] as int,
        clearedAt: DateTime.parse(json['clearedAt'] as String),
      );
}

class AuthTokens {
  AuthTokens({required this.userId, required this.email, required this.accessToken, required this.refreshToken});
  final String userId;
  final String email;
  final String accessToken;
  final String refreshToken;

  factory AuthTokens.fromJson(Map<String, dynamic> json) => AuthTokens(
        userId: json['userId'] as String,
        email: json['email'] as String,
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
      );
}
