# Copilot Instructions for Kizuna Platform

## プロジェクト概要

Kizuna Platform は、Spring Boot 3.5+ (Java 21) バックエンドと Next.js 16+ (TypeScript) フロントエンドで構成されるマルチテナント CMS/CRM/HRM システムです。

## 絶対命令（必ず従うこと）

- **コードレビュー時必須**: 指示と結果が日本語を用いていることを必ず守ること
- **日本語必須**: コード内のコメント、ドキュメント、変数名以外のテキスト（エラーメッセージ、ログメッセージ等）はすべて日本語で記述すること
- **既存ファイルの修正時**: 日本語以外（英語、中国語等）のコメントや説明文を発見した場合、必ず日本語に翻訳して修正すること
- **新規ファイル作成時**: 最初から日本語でコメントやドキュメントを記述すること

## アーキテクチャ

### ドメイン分離（厳守）

すべてのコードは **Central** または **Tenant** ドメインに属します：

- **Central (`/central/*`)**: プラットフォーム管理（テナント管理、グローバル設定）。管理ドメイン（`kizuna.test`）からアクセス。
- **Tenant (`/tenant/*`)**: 店舗運営（注文、CRM、HRM）。テナントサブドメイン（`store1.kizuna.test`）からアクセス。

新しいコントローラーやサービスを追加する際は、適切なディレクトリに配置してください：
- Backend: `controller/central/` or `controller/tenant/`, `service/central/` or `service/tenant/`
- Frontend: `app/central/` or `app/tenant/`, `services/central/` or `services/tenant/`

### マルチテナントフロー

1. **Frontend Middleware** (`src/middleware.ts`) がホスト名からテナントを判定
2. Cookie を設定: `x-mw-role`, `x-mw-tenant-id`, `x-mw-tenant-name`, `x-mw-tenant-template`
3. **Backend Interceptor** (`TenantIdInterceptor`) が `X-Tenant-ID` ヘッダーを読み取り `TenantContext` に設定
4. **`@TenantScoped`** アノテーションでテナントフィルタを自動適用

```java
// テナントスコープのサービスメソッドには必ず @TenantScoped を付与
@TenantScoped
public List<Order> findOrdersByTenant() {
    return orderRepository.findAll(); // Hibernate フィルタが自動適用
}
```

### API ルーティング

Traefik が `/api/*` を backend へルーティングし prefix を除去：
- Frontend: `/api/central/login` → Backend: `/central/login`
- Frontend: `/api/tenant/orders` → Backend: `/tenant/orders`

## 開発コマンド

```bash
# ビルド・起動（Docker 使用）
make build                      # 全サービスビルド
make up                         # フルスタック起動
make down                       # 停止

# テスト（70% カバレッジ必須）
make test                       # 全テスト実行
make test service=backend       # Backend のみ

# Lint・フォーマット
make lint                       # チェック
make format                     # 自動修正

# ローカル直接実行
cd backend && ./gradlew test spotlessApply
cd frontend && npm test && npm run lint:fix
```

## コード規約

> **重要**: コード生成時は常にベストプラクティスを推奨し、以下の規約に従うこと。

### 共通（厳守）

- **TDD (テスト駆動開発)**: **必ず実装前にテストを作成してください**。テストファーストの原則を厳守し、テストが失敗することを確認してから実装を行ってください。
- **機能追加時のデータ登録**: 新しい機能（APIエンドポイント、画面）を追加する際は、必ず対応する権限（Permission）やメニュー（Menu）のデータを登録するSQLスクリプト（Liquibase changeset）を作成してください。機能実装とデータ登録は常にセットで行う必要があります。
- **簡潔なコード**: 冗長なコードを避け、シンプルで読みやすいコードを書く
- **カバレッジ**: 70% 必須（CI で強制）
- **Backend単体テスト方針**: 単体テストは業務ロジック（Service / UseCase / Controller の振る舞い）を優先し、設定・定型変換・インフラ薄層の網羅を目的にしない
- **コミット前チェック**: 必ず `make lint && make test` を実行してからコミット

### Backend (Java)

- **命名規則**: クラス名、メソッド名、変数名、フィールド名はすべて **CamelCase** を使用すること。
    - **Database**: テーブル名、カラム名は **snake_case**。JPA/Hibernate が自動的にマッピングします。
    - **API / JSON**: 通信時のJSONキーはすべて **snake_case**。Jacksonの設定により自動変換されるため、Java側で変換ロジックを書く必要はありません。
- **import ルール**: 完全修飾クラス名（FQCN）を直接使用せず、必ず個別に import する。ワイルドカード import (`*`) は禁止
  ```java
  // ✅ Best Practice: 個別に import
  import java.util.List;
  import java.util.Optional;
  import org.springframework.stereotype.Service;

  @Service
  public class MyService {
      public Optional<List<String>> getData() { ... }
  }
  ```
  ```java
  // ❌ Bad: ワイルドカード import
  import java.util.*;
  ```
  ```java
  // ❌ Bad: import を書かずに FQCN を直接使用
  public class MyService {
      private org.springframework.stereotype.Service service;
  }
  ```
- **フォーマット**: Spotless + Google Java Format
- **カバレッジ算出除外**: JaCoCo では `config` / `model` / `repository` / `mapper` / `exception` を除外し、業務実装の検証に集中する
- **例外ルール**: 上記パッケージに明確な業務分岐・検証ロジックが存在する場合は、除外前提にせず個別にテスト追加可否を判断する
- **DB マイグレーション**: Liquibase（`db/changelog/changes/` に YAML）
- **設定値**: `AppProperties` から取得（ハードコード禁止）
- **ログ**: `req=<id> tenant=<id>` 形式を維持

### Frontend (TypeScript)

- **命名規則**: 
    - コンポーネント名: **PascalCase**。
    - API関連の型定義（Interface/Type）およびプロパティ名: **snake_case**（バックエンドのJSONと一致させる）。
    - 内部変数・関数名: 一般的なTypeScript慣習に従うが、APIデータに由来する変数は **snake_case** を維持すること。
- **API クライアント**: `src/lib/client.ts` の axios インスタンスを使用
- **Server Components**: Cookie は `cookies()` で読み取り（`headers()` は使わない）
- **型定義**: `src/types/api.ts` に集約
- **テスト配置**: `__tests__/` ディレクトリに配置

## 重要ファイル

| 用途 | パス |
|------|------|
| セキュリティ設定 | `backend/src/main/java/com/kizuna/config/SecurityConfig.java` |
| JWT フィルタ | `backend/src/main/java/com/kizuna/config/filter/JwtAuthenticationFilter.java` |
| テナントコンテキスト | `backend/src/main/java/com/kizuna/config/interceptor/TenantIdInterceptor.java` |
| DB マイグレーション | `backend/src/main/resources/db/changelog/changes/` |
| Frontend Middleware | `frontend/src/middleware.ts` |
| HTTP クライアント | `frontend/src/lib/client.ts` |

## 注意事項

- JWT はステートレス。ログアウト時は Redis の blacklist に追加
- Cookie 名 (`x-mw-*`) と Backend ヘッダー (`X-Role`, `X-Tenant-ID`) は 1:1 対応
- CI は 70% カバレッジを強制。新機能には必ずテストを追加
- PR は small & focused に。frontend/backend/environment をまたぐ変更は分割を検討
