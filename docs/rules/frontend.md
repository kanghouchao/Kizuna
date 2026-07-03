# Frontend (TypeScript) Conventions

- **Stack**: TypeScript 5.9, React 19, Next.js 16 (App Router), Jest, ESLint, Prettier
- **Architecture**: Feature-Sliced Design（FSD）。層構造は **Steiger** が `task lint` / CI で機械検証する

## FSD structure

```
frontend/src/
├── app/          # Next App Router: 純ルート薄殻。`export { XxxPage as default } from '@/_pages/...'` のみ
│                 #   例外: ルート `/`（app/page.tsx）は cookie のテナント解決による
│                 #   動的テンプレート dispatch を担うため薄殻にできない
├── _app/         # プロバイダ等のアプリ初期化（Next 予約名 app と衝突するため _app）
├── _pages/       # ページ slice。作用域接頭辞で命名: central-* / store-* + login, register, store-site
├── widgets/      # sidebar, header など複合 UI
├── features/     # ユーザー動作単位の slice: auth-login, tenant-register, ...
├── entities/     # バックエンドのドメインモジュールを鏡映:
│                 #   tenant, user, menu, cast, order, store-profile, system-config
│                 #   各 slice = model(型) / api(リクエスト) / index(公開 API)
└── shared/       # api(apiClient・共通型), lib(navigation・config・proxy), ui(汎用コンポーネント)
```

- **import は slice の index（公開 API）経由**。slice 内部は相対パス。自 slice を alias で import しない
- **層の依存方向は下向きのみ**: app → _pages → widgets → features → entities → shared
- **entity 同士の import 禁止**。複数 entity を跨ぐ組成（例: store-site の storefrontService）はページ層の責務
- **server-only モジュール**（next/headers 依存など）は通常の index に含めず `index.server.ts` の別出口に分離（例: `shared/api/index.server.ts` の serverClient）
- **公開ストアフロントのテンプレートは `_pages/store-site/templates/<key>/page.tsx`**。cookie の templateKey による動的 import の契約なのでパス構造を変えない
- **alias**: `@/*` → `./src/*`（tsconfig / jest 双方に設定済み）

## Code Conventions

- **Naming**:
  - コンポーネント名: **PascalCase**
  - API 関連の型（Interface/Type）とそのプロパティ名: **snake_case**（バックエンドの JSON キーに一致）
  - 内部変数・関数: 通常の TypeScript 規約。ただし API 由来のデータは snake_case を維持
  - Store 側語彙は Store 接頭辞: StoreProfile*, StoreUserResponse, storeAuthApi, storeMenuApi（CONTEXT.md 参照）
- **Coverage**: Jest 閾値 lines/statements 70%。対象は shared/api・shared/lib・entities（ページ・テンプレートは対象外）
- **Steiger ルールを無効化する場合は steiger.config.mjs に理由コメント必須**（typo-in-layer-name / repetitive-naming / insignificant-slice は設計判断・ルール仕様により off）
