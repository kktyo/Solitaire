# API 定義

前提: `システム要件定義.md`。ベースパス `/api/v1`。JSON、UTF-8。認証が必要な API は `Authorization: Bearer <access_token>`。

---

## 1. 共通

### 1.1 成功

2xx。本文は各 API のとおり。日時は ISO-8601 UTC（例: `2026-09-05T15:30:00.000Z`）。

### 1.2 エラー本文

```json
{
  "code": "INVALID_MOVE",
  "message": "その移動はできません。",
  "details": {}
}
```

| HTTP | code 例 | 用途 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 入力不備 |
| 401 | `UNAUTHORIZED` | 未ログイン・トークン無効 |
| 403 | `FORBIDDEN` | 権限なし |
| 404 | `NOT_FOUND` | 対局なし |
| 409 | `VERSION_CONFLICT` | 版ずれ。`details.game` に正本の GameResponse を入れる |
| 422 | `INVALID_MOVE` | 不正な手。盤面は不変。`details.game` に正本を入れてよい |
| 429 | `RATE_LIMITED` | 本フェーズは未使用可 |
| 500 | `INTERNAL_ERROR` | 予期せぬ障害 |

### 1.3 型

**Card**

```json
{ "id": "AS", "faceUp": true }
```

`id` は `{A|2|3|4|5|6|7|8|9|10|J|Q|K}{S|H|D|C}`。

**Board**

```json
{
  "tableau": [ [ { "id": "KC", "faceUp": true } ], [], [], [], [], [], [] ],
  "foundations": [ [], [], [], [] ],
  "stock": [ { "id": "2D", "faceUp": false } ],
  "waste": [ { "id": "AH", "faceUp": true } ]
}
```

- `tableau`: 長さ 7。各列は底→表の順（末尾がトップ）。
- `stock`: 先頭が次にめくるカード。すべて `faceUp: false`。
- `waste`: 末尾がトップ。すべて `faceUp: true`。
- `foundations`: 長さ 4 の配列。枠 0..3。各列は空、または底が A（その枠のスート）、末尾がトップ。すべて表向き。
- 読み取り互換: 旧オブジェクト `{ "S","H","D","C" }` は枠順 S→H→D→C として解釈してよい。新規・書き込みは配列のみ。

**GameResponse**

```json
{
  "gameId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "IN_PROGRESS",
  "version": 12,
  "moveCount": 12,
  "elapsedMs": 45000,
  "timingStartedAt": "2026-09-05T15:31:00.000Z",
  "canUndo": true,
  "stalemate": false,
  "board": {},
  "startedAt": "2026-09-05T15:30:00.000Z",
  "updatedAt": "2026-09-05T15:31:00.000Z",
  "clearedAt": null
}
```

`status`: `IN_PROGRESS` | `CLEARED` | `ABANDONED`。  
`stalemate`: 操作詰みなら `true`（`IN_PROGRESS` のみ。クリア済みは常に `false`）。定義はシステム要件 5.5a。  
`timingStartedAt` は計測停止中 `null`。クリア済みは `null`、`clearedAt` あり。

**ResultSummary**

```json
{
  "gameId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "moveCount": 120,
  "elapsedMs": 180000,
  "clearedAt": "2026-09-05T16:00:00.000Z"
}
```

---

## 2. 認証

認証 API は Bearer 不要（ログアウト・リフレッシュは除く）。

### 2.1 登録

`POST /auth/register`

```json
{ "email": "user@example.com", "password": "password1" }
```

成功 `201`

```json
{
  "userId": "…",
  "email": "user@example.com",
  "accessToken": "…",
  "refreshToken": "…",
  "expiresIn": 3600
}
```

| 失敗 | 条件 |
| --- | --- |
| 400 | 形式不正、パスワード 8 未満 |
| 400 `EMAIL_TAKEN` | メール重複 |

登録成功でログイン状態にする。

### 2.2 ログイン

`POST /auth/login`  
本文は登録と同じ。成功 `200`（本文は登録成功と同じ）。失敗 `401`（存在しないメールも同じメッセージ）。

### 2.3 リフレッシュ

`POST /auth/refresh`

```json
{ "refreshToken": "…" }
```

成功 `200`（新しい access / refresh を返して旧 refresh を無効化してもよい）。失敗 `401`。

### 2.4 ログアウト

`POST /auth/logout`  
Bearer 必須。本文 `{ "refreshToken": "…" }`。成功 `204`。当該 refresh を無効化。

---

## 3. 対局

すべて Bearer 必須。操作対象は **自分の対局** のみ。

### 3.1 進行中取得

`GET /games/current`

| 結果 | HTTP | 本文 |
| --- | --- | --- |
| 進行中あり | 200 | GameResponse（計測再開: `timingStartedAt` を now にして永続化してよい） |
| なし | 404 | エラー本文 |

ホーム表示用に計測を再開したくない場合は、実装は **GET では `timingStartedAt` を変えない** とし、対局画面入場時に `POST /games/{gameId}/resume` を呼ぶ。

本フェーズの契約: **`GET /games/current` は盤面を返すだけ（計測状態は変えない）**。対局画面を開いたら 3.2 を呼ぶ。

### 3.2 計測再開

`POST /games/{gameId}/resume`

成功 `200` GameResponse。`IN_PROGRESS` のみ。`timingStartedAt = now`（既に計測中なら now にリセットせず維持してよいが、経過の二重加算をしないこと）。

### 3.3 計測停止

`POST /games/{gameId}/pause`

成功 `200` GameResponse。累積 `elapsedMs` を確定し `timingStartedAt = null`。ホームへ戻る・バックグラウンド遷移で呼ぶ。

### 3.4 新規

`POST /games`

```json
{ "abandonExisting": true }
```

- 進行中がない: 新規配札、`201` GameResponse。`abandonExisting` は無視してよい。
- 進行中があるかつ `abandonExisting !== true`: `409` `GAME_IN_PROGRESS`。`details.game` に現対局。
- 進行中があるかつ `true`: 現対局を `ABANDONED` にし新規 `201`。

### 3.5 手の適用

`POST /games/{gameId}/moves`

```json
{
  "version": 12,
  "move": { "type": "MOVE", "from": { "pile": "TABLEAU", "index": 2 }, "to": { "pile": "FOUNDATION", "index": 0 }, "count": 1 }
}
```

`move.type`:

| type | 追加フィールド | 意味 |
| --- | --- | --- |
| `MOVE` | `from`, `to`, `count` | カード移動。`count` は 1 以上。捨て札・組札・山札からの `count` は 1 のみ |
| `DRAW` | なし | 山札 1 枚をめくる |
| `RECYCLE` | なし | 捨て札を山札へ戻す |

`pile`: `TABLEAU` | `FOUNDATION` | `STOCK` | `WASTE`。  
`index`: 場札 0–6、組札 0–3（枠番号。スート固定ではない）。STOCK/WASTE では 0 固定または省略。

成功 `200` GameResponse。クリア直後は `status: CLEARED`、`canUndo: false`、結果が永続化されていること。

失敗: `409` 版ずれ、`422` 不正、`404`、`400` スキーマ不正。`CLEARED` / `ABANDONED` への手は `422` または `409`。

### 3.6 アンドゥ

`POST /games/{gameId}/undo`

```json
{ "version": 12 }
```

成功 `200` GameResponse。履歴がない、または `CLEARED` は `422` `CANNOT_UNDO`。

### 3.7 直近結果

`GET /results/me?limit=1`

成功 `200`

```json
{ "items": [ { "gameId": "…", "moveCount": 120, "elapsedMs": 180000, "clearedAt": "…" } ] }
```

`items` は `clearedAt` 降順。本人の `CLEARED` のみ。空配列は 200。

---

## 4. クライアント実装メモ

1. 対局画面入場: `GET /games/current` または新規 → `POST …/resume`。
2. 退場・バックグラウンド: `POST …/pause`（失敗しても次回復帰で GET する）。
3. 操作は `version` を付けて 1 本ずつ。409 なら返ってきた GameResponse で置換。
4. 経過時間の POST は存在しない（4.2 章どおりサーバ時計のみ）。
