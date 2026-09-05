# ソリティア（Klondike）

ログイン利用者が Draw 1 の Klondike を遊び、進行中対局の保存・再開とサーバ検証済みクリア結果を扱うアプリです。正本は `doc/` です。

## 構成

```
doc/                 仕様・設計
code/front/          Flutter
code/server/         Spring Boot 3 / Java 21
code/docker/         Compose と API Dockerfile
infra/main.bicep     App Service Plan・スロット・自動スケール・Azure SQL・ACR
```

## ローカル起動

1. 接続情報はリポジトリに置かない。雛形だけをコピーする。
   - `cp code/docker/.env.example code/docker/.env`
   - `cp code/server/src/main/resources/application.yml.example code/server/src/main/resources/application.yml`
   - 必要なら `application.yml` と `.env` に実値を書く（どちらも gitignore 済み）
2. `cd code/docker && docker compose --env-file .env up --build`  
   Dockerfile がサーバ JAR をビルドします。初回は Gradle のダウンロードがあります。
3. API: `http://127.0.0.1:8080/api/v1/health`
4. フロント: `cd code/front`
   - 初回のみ: `flutter create --project-name solitaire --org com.solitaire --platforms=android,ios .`
   - `flutter run --dart-define=API_BASE_URL=http://127.0.0.1:8080`

シミュレータから Mac の API を叩く場合は `127.0.0.1` の代わりにホストの LAN IP を使います。

## テスト

- サーバ: `cd code/server && ./gradlew test`  
  JUnit（規則ベクタ・ユースケース・認証 API）。Docker がある環境では Testcontainers の SQL Server で結合（`GameApiIT`）が走ります。本番 Azure SQL には接続しません。
- フロント: `cd code/front && flutter test`  
  規則ベクタと、送信中に第二手が HTTP されないことの単体テスト。
- `integration_test/` はログイン画面の到達確認です。対局〜保存〜再開の手動受入はローカル API に対して行います（システム要件 9 章）。

## CI/CD

GitHub Actions:

| ワークフロー | 内容 |
| --- | --- |
| `ci-server.yml` | Gradle / JUnit（Testcontainers はランナーに Docker があるとき） |
| `ci-front.yml` | `flutter test`。APK は失敗してもサーバ CD は止めない |
| `cd-staging.yml` | main のサーバ変更でイメージを ACR へ push し **staging スロット**へ。ヘルス `/api/v1/health` |
| `swap-production.yml` | 手動で staging → production スワップ |

必要な GitHub Secrets: `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`  
Variables: `ACR_NAME`, `WEBAPP_NAME`, `AZURE_RESOURCE_GROUP`

App Settings（ポータルまたは Bicep）: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`

## Azure 初期構築

```
az deployment group create -g <rg> -f infra/main.bicep \
  -p sqlAdminLogin=solitaireadmin \
     sqlAdminPassword=<secret> \
     jwtAccessSecret=<32bytes+> \
     jwtRefreshSecret=<32bytes+>
```

SKU は Standard S1（スロットとオートスケールのため）。SQL は Azure SQL Basic。ファイアウォールは App Service 向け `AllowAllWindowsAzureIps`（本フェーズ。プライベートエンドポイントは後続）。JWT はステートレスなのでインスタンス増減にセッションストアは不要です。

ACR の AcrPull を Web App / staging スロットのマネージド ID に付与してください。

## 環境変数（サーバ）

| 名 | 用途 |
| --- | --- |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | SQL Server |
| `JWT_ACCESS_SECRET` / `JWT_REFRESH_SECRET` | HS256（256 bit 以上） |
| `PORT` | 既定 8080 |

`application.yml`・`.env`・鍵ファイル・Bicep の parameters.json は `.gitignore` のため push されません。雛形は `*.example` のみです。
