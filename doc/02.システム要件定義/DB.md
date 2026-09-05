# DB 定義

前提: `システム要件定義.md`。RDB: SQL Server。論理名。物理型は SQL Server 想定。

---

## 1. 方針

- 文字コード: Unicode（`NVARCHAR`）。
- 主キー: UUID（`UNIQUEIDENTIFIER`）または UUID 文字列。アプリで生成してよい。
- 削除: 利用者・対局は物理削除しない（対局は状態遷移）。リフレッシュトークンは無効化または削除可。
- パスワード・トークン生値は保存しない（ハッシュまたはサーバが発行したトークンのハッシュ）。

---

## 2. テーブル

### 2.1 users

| 列 | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| id | uniqueidentifier | PK | 利用者 ID |
| email | nvarchar(256) | NOT NULL, UNIQUE | 正規化（小文字 trim）済み |
| password_hash | nvarchar(255) | NOT NULL | BCrypt 等 |
| created_at | datetime2 | NOT NULL | UTC |
| updated_at | datetime2 | NOT NULL | UTC |

### 2.2 refresh_tokens

| 列 | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| id | uniqueidentifier | PK | |
| user_id | uniqueidentifier | NOT NULL, FK users | |
| token_hash | nvarchar(255) | NOT NULL, UNIQUE | 生トークンは保存しない |
| expires_at | datetime2 | NOT NULL | |
| revoked_at | datetime2 | NULL | ログアウト等 |
| created_at | datetime2 | NOT NULL | |

### 2.3 games

| 列 | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| id | uniqueidentifier | PK | gameId |
| user_id | uniqueidentifier | NOT NULL, FK users | |
| status | nvarchar(16) | NOT NULL | `IN_PROGRESS` / `CLEARED` / `ABANDONED` |
| version | int | NOT NULL | 0 始まり。更新のたびに +1 |
| move_count | int | NOT NULL | 現在手数 |
| elapsed_ms | bigint | NOT NULL | 確定済み経過 ms |
| timing_started_at | datetime2 | NULL | 計測中のみ |
| board_json | nvarchar(max) | NOT NULL | Board JSON（正本） |
| started_at | datetime2 | NOT NULL | |
| updated_at | datetime2 | NOT NULL | |
| cleared_at | datetime2 | NULL | クリア時のみ |

索引・制約:

- `UX_games_user_in_progress`: `user_id` に対し `status = 'IN_PROGRESS'` の行を最大 1（フィルタ済み一意索引）。
- `IX_games_user_status`: `(user_id, status, cleared_at)`。

`board_json` は API の Board と同一スキーマ。裏表を含む全 52 枚が過不足なく入ること。

### 2.4 game_moves

進行中のアンドゥおよび検証用。クリア後も監査のため残してよい。

| 列 | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| id | bigint identity | PK | |
| game_id | uniqueidentifier | NOT NULL, FK games | |
| seq | int | NOT NULL | 対局内の通番 1,2,… |
| kind | nvarchar(8) | NOT NULL | `APPLY` / `UNDO` |
| move_json | nvarchar(max) | NULL | APPLY 時の move オブジェクト |
| board_before_json | nvarchar(max) | NOT NULL | 適用前盤面 |
| move_count_before | int | NOT NULL | |
| elapsed_ms_before | bigint | NOT NULL | |
| created_at | datetime2 | NOT NULL | |

制約: `UQ_game_moves_game_seq (game_id, seq)`。

アンドゥ手順: 最新の `APPLY` でまだ打ち消されていないものを 1 件戻し、`board` / `move_count` を `*_before` に戻す。`UNDO` 行を追加し `version` を +1。`elapsed_ms` はアンドゥでも減らさない（止まった時間は戻さない）。手数だけ戻す。

### 2.5 game_results

クリア認定の参照用。`games` が `CLEARED` のものと 1:1。

| 列 | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| game_id | uniqueidentifier | PK, FK games | |
| user_id | uniqueidentifier | NOT NULL, FK users | 検索用冗長 |
| move_count | int | NOT NULL | 確定手数 |
| elapsed_ms | bigint | NOT NULL | 確定経過 |
| cleared_at | datetime2 | NOT NULL | |

`IX_game_results_user_cleared (user_id, cleared_at DESC)`。

クリア処理は同一トランザクションで `games.status` 更新と本テーブル insert を行う。クライアント申告の時間・手数は書かない。

---

## 3. 状態遷移

```
（INSERT）→ IN_PROGRESS → CLEARED
                       → ABANDONED
CLEARED / ABANDONED から IN_PROGRESS へは戻さない。
```

新規対局は常に INSERT。やり直しは旧行 ABANDONED ＋新行 INSERT。

---

## 4. 保持

| データ | 本フェーズ |
| --- | --- |
| users | 無期限 |
| refresh_tokens | 期限切れ・revoked は削除してよい |
| games / game_moves / game_results | 無期限（運用削除は対象外） |

---

## 5. セキュリティ

- アプリは最小権限の DB ユーザを使う。
- 接続文字列は環境変数。
- メールの UNIQUE は正規化後の値に対して張る。
