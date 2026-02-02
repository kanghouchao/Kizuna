# Copilot Instructions for Kizuna Platform

## プロジェクト概要

Kizuna Platform は、Spring Boot 3.5+ (Java 21) バックエンドと Next.js 16+ (TypeScript) フロントエンドで構成されるマルチテナント CMS/CRM/HRM システムです。

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

### 共通ルール

- **コード簡潔性**: 冗長なコードを避け、適切なコメントを付与する
- **TDD**: テストを先に書いてから実装する（Test-Driven Development）
- **コミット前チェック**: 必ず `make lint` と `make test` を実行してから提出

### Backend (Java)

- **import 必須**: 完全修飾クラス名（FQCN）を直接使用しない。必ず `import` 文を使用する
  ```java
  // ❌ Bad
  org.springframework.stereotype.Service

  // ✅ Good
  import org.springframework.stereotype.Service;
  ```
- **フォーマット**: Spotless + Google Java Format（`make format service=backend`）
- **DB マイグレーション**: Liquibase（`db/changelog/changes/` に YAML で追加）
- **設定値**: `AppProperties` から取得（ハードコード禁止）
- **ログ**: `req=<id> tenant=<id>` 形式を維持（Log4j2 ThreadContext）

### Frontend (TypeScript)

- **API クライアント**: `src/lib/client.ts` の axios インスタンスを使用
- **Server Components**: Cookie は `cookies()` で読み取り（`headers()` は使わない）
- **型定義**: `src/types/api.ts` に集約
- **テスト**: `__tests__/` ディレクトリに配置

## 重要ファイル

| 用途 | パス |
|------|------|
| セキュリティ設定 | `backend/.../config/SecurityConfig.java` |
| JWT フィルタ | `backend/.../config/filter/JwtAuthenticationFilter.java` |
| テナントコンテキスト | `backend/.../config/interceptor/TenantIdInterceptor.java` |
| DB マイグレーション | `backend/src/main/resources/db/changelog/changes/` |
| Frontend Middleware | `frontend/src/middleware.ts` |
| HTTP クライアント | `frontend/src/lib/client.ts` |

## 注意事項

- JWT はステートレス。ログアウト時は Redis の blacklist に追加
- Cookie 名 (`x-mw-*`) と Backend ヘッダー (`X-Role`, `X-Tenant-ID`) は 1:1 対応
- CI は 70% カバレッジを強制。新機能には必ずテストを追加
- PR は small & focused に。frontend/backend/environment をまたぐ変更は分割を検討
