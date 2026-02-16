# Kizuna Project Instructional Context (GEMINI.md)

このファイルは、Kizunaプロジェクトにおける開発およびコード生成の指針を提供します。

## 絶対命令（最優先事項）

- **日本語必須**: コード内のコメント、ドキュメント、エラーメッセージ、ログメッセージはすべて日本語で記述してください。
- **言語の統一**: 既存のファイルに英語や中国語のコメントがある場合は、修正時に日本語へ翻訳してください。
- **規約遵守**: 下記の設計・コーディング規約を厳守してください。

## プロジェクト概要

Kizuna Platformは、Spring Boot 3.5+ (Java 21) と Next.js 16+ (React 19, TypeScript 5.9) で構成されるマルチテナント CMS/CRM/HRM システムです。

### 核心技術
- **Backend**: Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL, Redis, Liquibase, MapStruct, Lombok.
- **Frontend**: TypeScript, Next.js (App Router), React 19, Tailwind CSS, Axios.
- **インフラ**: Traefik (Reverse Proxy), Nginx (Static File Server), Docker Compose.

## 開発・ビルドコマンド

### Dockerベース（推奨）
- `make build`: 全サービスのビルド。
- `make up`: コンテナの起動（DB、Redis、Nginx、Backend、Frontend）。
- `make test`: テストの実行（カバレッジ 70% 必須）。
- `make format`: コードの自動整形（Spotless / Prettier / ESLint）。
- `make logs service=<name>`: ログの確認。

### 直接実行
- **Backend**: `./gradlew test`, `./gradlew spotlessApply`.
- **Frontend**: `npm test`, `npm run lint:fix`, `npm run format`.

## アーキテクチャと設計指針

### 1. ドメイン分離
- **Central (`/central/*`)**: プラットフォーム管理用。
- **Tenant (`/tenant/*`)**: 店舗運営用（注文、キャスト管理、CRM）。
- コードは必ず適切なドメインパッケージ（`controller/tenant` 等）に配置してください。

### 2. マルチテナント・フロー
- **Frontend**: Middlewareがホスト名からテナントを特定し、Cookie (`x-mw-tenant-id` 等) を設定します。
- **Backend**: `TenantIdInterceptor` がリクエストヘッダーからテナントIDを抽出し、`TenantContext` に設定します。
- **JPA隔離**: テナントスコープのサービスには `@TenantScoped` を付与してください。Hibernateフィルタが適用され、データが自動的に分離されます。

### 3. 命名規則とデータ形式
- **Backend (Java)**: クラス名、メソッド名、変数名、フィールド名はすべて **CamelCase** (例: `userProfile`, `getTenantConfig`) を使用してください。
- **Database**: テーブル名、カラム名は **snake_case** (例: `user_profiles`, `tenant_config`) を使用してください。
- **API / JSON**: 通信時のJSONキーはすべて **snake_case** に統一されます。Jacksonの設定により自動変換されるため、Java側で変換ロジックを書く必要はありません。
- **Frontend (TypeScript)**:
    - コンポーネント名: **PascalCase** (例: `TenantConfigForm`)。
    - API関連の型定義（Interface/Type）およびオブジェクトのプロパティ名: **snake_case** (例: `logo_url`, `template_key`)。バックエンドのJSONと完全に一致させてください。
    - 変数名・関数名: 一般的なTypeScript慣習に従いますが、APIデータに由来する変数は **snake_case** を維持してください。

### 4. 開発プロセス
- **TDD (テスト駆動開発)**: **必ず実装前にテストを作成してください**。テストファーストの原則を厳守し、テストが失敗することを確認してから実装を行ってください。
- **機能追加時のデータ登録**: 新しい機能（APIエンドポイント、画面）を追加する際は、必ず対応する権限（Permission）やメニュー（Menu）のデータを登録するSQLスクリプト（Liquibase changeset）を作成してください。機能実装とデータ登録は常にセットで行う必要があります。


## コーディング規約

### 共通
- **TDD (テスト駆動開発)**: **必ず実装前にテストを作成してください**。テストファーストの原則を厳守し、テストが失敗することを確認してから実装を行ってください。
- **カバレッジ**: 70% 以上のラインカバレッジを維持してください。
- **Backend単体テスト方針**: 単体テストは業務ロジック（Service / UseCase / Controller の振る舞い）を優先し、設定・定型変換・インフラ薄層の網羅を目的にしないでください。

### Backend (Java)
- **import**: ワイルドカード import (`*`) は禁止です。個別に import してください。
- **マッピング**: Entity と DTO の変換には MapStruct を使用してください。
- **カバレッジ算出除外**: JaCoCo では `config` / `model` / `repository` / `mapper` / `exception` を除外し、業務実装の検証に集中してください。
- **例外ルール**: 上記パッケージに明確な業務分岐・検証ロジックが存在する場合は、除外前提にせず個別にテスト追加可否を判断してください。
- **バリデーション**: 入力値のチェックを徹底し、特にファイルパスに関してはパストラバーサル対策（`normalize()` とプレフィックスチェック）を必ず行ってください。

### Frontend (TypeScript)
- **Data Fetching**: 
    - Server Components からの API 呼び出しには `src/lib/server-client.ts` を使用してください。
    - 模版（Template）は自律的にデータを取得する Server Component として構築してください。
- **コンポーネント分離**: 表示用の Presentational Component とデータ取得用の Container を明確に分離してください。
- **型定義**: `src/types/api.ts` に API 関連の型を集約してください。

## 静的ファイル配信
- 画像等のファイルは `/static/tenant/files/...` パスで配信されます。
- Traefik がこのパスを検知し、Nginx (`static` コンテナ) へルーティングします。
- Nginx は `/usr/share/nginx/html/uploads/` 配下の実ファイルを返します。