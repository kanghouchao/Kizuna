# E2E テスト（playwright-bdd）

Kizuna のフル実スタック（Traefik → frontend / backend → PostgreSQL / Redis）を
外部から headless ブラウザで検証する E2E スイートです。日本語 Gherkin で書いた
シナリオを [playwright-bdd](https://vitalets.github.io/playwright-bdd/) が
Playwright テストへ変換して実行します。

## 実行方法

前提: ホストに `jq` が必要です（`e2e/package.json` から Playwright の版数を抽出し、one-shot コンテナのイメージタグに反映するため）。

リポジトリのルートで次を実行します。

```bash
task e2e
```

`task e2e` は自己完結で、以下を順に行います。

1. frontend / backend の Docker イメージを worktree 固有のタグ（`e2e-<worktree 名>`）でビルド
2. 同じ E2E プロジェクトの残存コンテナと匿名ボリュームを回収し、ネットワークを破棄
3. E2E 専用スタックを起動して実行（`e2e/docker-compose.e2e.yml`。gateway の healthy を待ち、
   ランナーが gateway 経由の公開サイトへの疎通を確認してからテストを始める）
4. 成否にかかわらずスタックを破棄（`defer`）

合否は E2E コンテナの退出コードで判定します。初回の再試行で採取する `trace` と失敗時の
`screenshot` が `e2e/test-results/`、HTML レポートが `e2e/playwright-report/` に
残ります（いずれも Git 管理外）。

### 開発スタックとの関係

E2E は**開発スタック（project `kizuna`）を使いません**。専用の使い捨てスタックを毎回立て、
終わったら壊します。そのため

- 開発スタックを起動したまま E2E を走らせても、互いのデータもコンテナも壊れません
- プロジェクト名・イメージタグは worktree ディレクトリの basename を小文字にし、英数字以外を
  `-` に置換して導出します。この結果が異なる worktree 同士は並行実行できます。同じ basename や
  正規化後の名前が一致する checkout、同じ worktree 内の複数実行は互いのスタックを回収するため並行実行できません
- DB / Redis / MinIO は tmpfs なので、毎回シードからやり直した状態で始まります

## 構成

- `features/**/*.feature` — 日本語 Gherkin のシナリオ
- `steps/**/*.ts` — ステップ定義
- `base-url.ts` — 店舗の `BASE_URL` とプラットフォームの `PLATFORM_URL` の既定値
- `playwright.config.ts` — `defineBddConfig` と Playwright 設定
- `docker-compose.e2e.yml` — E2E 専用の使い捨てフルスタック（DB / Redis / MinIO /
  backend / frontend / gateway ＋ テストランナー）

ブラウザの `baseURL` は環境変数 `BASE_URL`（既定 `http://store1.kizuna.test`）で
切り替えます。プラットフォーム側は `PLATFORM_URL`（既定 `http://kizuna.test`）です。
スイートは共有店舗の状態を扱うため、`workers: 1`・`fullyParallel: false` で直列実行します。
store / platform の判別は Host ヘッダで frontend proxy が行うため、
gateway サービスに network alias `store1.kizuna.test` / `kizuna.test` を付与しています。
alias はネットワーク単位なので、スタックを複数立てても互いに干渉しません。

## シナリオの追加手順

1. `features/` に `.feature` を追加し、先頭に `# language: ja` を記述する
2. `steps/` に対応するステップ定義を追加する（`createBdd()` の `Given` / `When` / `Then`）
3. `task e2e` で実行する

日本語 Gherkin のキーワードは以下を使用できます（シナリオは日本語に統一）。

| 役割 | 日本語キーワード |
| --- | --- |
| Feature | `機能` |
| Scenario | `シナリオ` |
| Given | `前提` |
| When | `もし` |
| Then | `ならば` |
| And | `かつ` |
| But | `しかし` |

ステップ定義は英語 API（`Given` / `When` / `Then`）で書き、日本語キーワードの
シナリオ行と正規表現／cucumber 式で突き合わせます。

## ローカルでの反復（任意）

Docker を使わず手元の Node で回す場合は、`e2e/` で以下を実行します。前提は
**開発スタック（`task up`）が起動済み**で、`store1.kizuna.test` と `kizuna.test` がホストから名前解決できること
——E2E 専用スタックはホストポートを公開しないため、この経路では使えません。開発スタックの
データに対して走ることになるので、書き込みを伴うシナリオの扱いには注意してください。

この経路は、[ステップ共通処理](steps/store-api.ts) と Gherkin に記載されたシードアカウント・
固定パスワードに DB が一致している場合に限ります。README の手順で自分の初期パスワードを
設定した開発 DB では、そのまま全シナリオを実行できません。通常の検証は専用シードを使う
`task e2e` を使用してください。

```bash
cd e2e
npm ci
npx playwright install chromium
npx bddgen        # .feature からテストコードを生成
npx playwright test
```
