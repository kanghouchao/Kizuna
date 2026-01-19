# Kizuna Platform Copilot Instructions (CMS, CRM, HRM)

## アーキテクチャと責務
- Kizuna Platform は `backend/` の Spring Boot 3.5.9 API、`frontend/` の Next.js 16 アプリ、`environment/` の Traefik＋Compose 定義に分離される二層構成。
- プロジェクトのビジョン：CMS (コンテンツ管理)、CRM (顧客管理)、HRM (人事管理) を統合したマルチテナントプラットフォーム。
- すべての外部リクエストは Traefik で `/api` -> backend、その他 -> frontend に振り分けられ、`environment/development/docker-compose.yml` でルーティングとヘルスチェックが定義される。
- Multi-tenant モデル：中央管理 (`/central/*`) とテナント (`/tenant/*`) を API/画面ともに分離し、ホスト名判定で役割を切り替える。

## バックエンド指針
- `backend/src/main/java` は `config/`, `controller/central|tenant`, `service`, `repository`, `model` の典型的な層構造。新機能 (CMS/CRM/HRM 等) はこの分離に合わせて配置する。
- `SecurityConfig` は stateless JWT + CSRF cookie を前提としているため、エンドポイント追加時も `CookieCsrfTokenRepository` を尊重し、`JwtAuthenticationFilter` 前後のチェーン順序を崩さない。
- テナントスコープは `TenantIdInterceptor` (X-Role + X-Tenant-ID ヘッダー) と `@TenantScoped` + `TenantFilterEnable` で実現する。テナント固有クエリを実行するサービスメソッドは必ず `@TenantScoped` を付与し、`TenantContext` の設定/クリアを二重に行わない。
- すべてのリクエストには `RequestCorrelationFilter` が `X-Request-ID` を強制付与する。新しいフィルターやロガーは ThreadContext を破壊しないようにする。
- Redis は JWT ブラックリスト (`CentralAuthController.logout`) と Spring Cache 双方で利用。Redis キーの接頭辞は `application.yml` の `spring.cache.redis.key-prefix` を再利用する。
- データモデルの変更は `backend/src/main/resources/db/changelog/` の Liquibase で管理し、`db.changelog-master.yaml` にチェインを追加する。

## フロントエンド指針
- Next.js 16 の App Router を採用し、`src/app/central` と `src/app/tenant` で画面を分割。SSR コンポーネントは `middleware.ts` が与える cookie (`x-mw-role`, `x-mw-tenant-*`) を `cookies()` で参照する。
- `src/middleware.ts` は管理ドメイン集合とバックエンドの `GET /central/tenant?domain=` を同期させている。レスポンス shape (template_key / tenant_id / tenant_name or id/name/domain) を壊さないこと。
- API 呼び出しは `src/lib/client.ts` の axios インスタンス経由で `/api` ベースに集約され、ここで JWT・CSRF・テナントヘッダーが付与される。新しいサービス層は `src/services/central|tenant/` にメソッドを追加し、型は `src/types/api.ts` に定義する。

## ローカル開発とビルド
- Docker 前提。`make build` (または `make build service=backend|frontend`) でそれぞれの BuildKit ステージを実行し、`make up env=development` で Traefik + DB + Redis + アプリを起動。
- Lint/Format/Test はすべて Make 経由で Docker ステージを呼ぶ：`make lint service=frontend`, `make format service=backend`, `make test`。ローカルで単体実行したい場合のみ `./backend/gradlew test` や `npm test` を使う。
- テスト成果物は `make reports` で `reports/backend` (JUnit/Jacoco) と `reports/frontend` (Jest coverage) に集約され、`reports/index.html` から参照するのが前提。

## 実装上の注意
- Traefik が `/api` プレフィックスを剥がした後に Spring が `/central/*` を受けるため、フロントが呼び出すパスは `/api/central/...` で統一する。
- Middleware が設定する cookie 名はバックエンドヘッダーと 1:1 対応しているため、名称変更時は `TenantIdInterceptor` と `client.ts` を同時に更新する。
- 監査やログに依存する値 (`req=<id> tenant=<id>`) は Log4j2 の ThreadContext を利用する。追加カスタムロガーもこの形式を踏襲する。
- 重大な設定値は `AppProperties` から取得し、ハードコードしない。特にドメイン/スキームや JWT の有効期限はここから参照する。
