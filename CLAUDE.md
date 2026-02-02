# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

Kizuna Platform は、Spring Boot 3.5+ (Java 21) バックエンドと Next.js 16+ (React 19, TypeScript 5.9) フロントエンドで構成されるマルチテナント CMS/CRM/HRM システムです。Traefik リバースプロキシを介して完全に分離されたアーキテクチャを採用しています。

## 開発コマンド

### Docker ベースのビルド・テスト（推奨）

```bash
# ビルド
make build                          # 全サービス
make build service=frontend         # フロントエンドのみ
make build service=backend          # バックエンドのみ

# テスト（70% カバレッジ必須）
make test                           # 全テスト
make test service=frontend          # Jest のみ
make test service=backend           # JUnit + Jacoco のみ

# Lint・フォーマット
make lint                           # チェック
make format                         # 自動修正

# ローカル起動
make up                             # フルスタック起動
make down                           # 停止
make logs service=backend           # ログ確認
```

### ローカル直接実行

```bash
# Backend (Java 21 必須)
cd backend
./gradlew test                      # テスト実行
./gradlew spotlessApply             # フォーマット自動修正
./gradlew spotlessCheck             # スタイルチェック

# Frontend (Node.js 24.7+ 必須)
cd frontend
npm test                            # Jest テスト
npm run lint:fix                    # ESLint 自動修正
npm run format                      # Prettier 自動修正
```

## アーキテクチャ

### ドメイン分離（厳守）

すべてのコードは **Central** または **Tenant** ドメインに属する：

| ドメイン | パス | 用途 | アクセス元 |
|---------|------|------|-----------|
| **Central** | `/central/*` | プラットフォーム管理、テナント管理 | `kizuna.test` |
| **Tenant** | `/tenant/*` | 店舗運営、注文、CRM、HRM | `store1.kizuna.test` |

ディレクトリ構造：
- Backend: `controller/central/` or `controller/tenant/`, `service/central/` or `service/tenant/`
- Frontend: `app/central/` or `app/tenant/`, `services/central/` or `services/tenant/`

### マルチテナントフロー

1. **Frontend Middleware** (`src/middleware.ts`) がホスト名からテナントを判定
2. Cookie を設定: `x-mw-role`, `x-mw-tenant-id`, `x-mw-tenant-name`, `x-mw-tenant-template`
3. **Backend Interceptor** (`TenantIdInterceptor`) が `X-Tenant-ID` ヘッダーを読み取り `TenantContext` に設定
4. Hibernate フィルタで自動的にテナント分離を適用

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

## コード規約

### 共通

- **TDD**: テストを先に書いてから実装
- **カバレッジ**: 70% 必須（CI で強制）
- **コミット前**: `make lint && make test` を実行

### Backend (Java)

- **import 必須**: FQCN を直接使用せず、必ず `import` 文を使用
- **フォーマット**: Spotless + Google Java Format
- **DB マイグレーション**: Liquibase（`db/changelog/changes/` に YAML）
- **設定値**: `AppProperties` から取得（ハードコード禁止）
- **ログ**: `req=<id> tenant=<id>` 形式を維持

### Frontend (TypeScript)

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

## 認証・セッション

- JWT はステートレス（`SessionCreationPolicy.STATELESS`）
- ログアウト時は Redis の blacklist に追加
- Cookie 名 (`x-mw-*`) と Backend ヘッダー (`X-Role`, `X-Tenant-ID`) は 1:1 対応

## ローカル開発環境

```bash
# hosts ファイルに追加
echo "127.0.0.1 kizuna.test store1.kizuna.test" | sudo tee -a /etc/hosts

# 環境変数コピー
cp environment/.env.example .env

# 起動
make build up

# デフォルト認証情報
# Central: admin / pass
# Tenant: admin@store1.kizuna.com / pass
```
