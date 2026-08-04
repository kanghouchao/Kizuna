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
2. 前回の残骸を掃除（`docker compose ... down`）
3. E2E 専用スタックを起動して実行（`e2e/docker-compose.e2e.yml`。gateway が healthy に
   なるまで compose の `depends_on` が待つ）
4. 成否にかかわらずスタックを破棄（`defer`）

合否は E2E コンテナの退出コードで判定します。失敗時は `trace` /
`screenshot` が `e2e/test-results/`、HTML レポートが `e2e/playwright-report/` に
残ります（いずれも Git 管理外）。

### 開発スタックとの関係

E2E は**開発スタック（project `kizuna`）を使いません**。専用の使い捨てスタックを毎回立て、
終わったら壊します。そのため

- 開発スタックを起動したまま E2E を走らせても、互いのデータもコンテナも壊れません
- 複数の worktree が同時に E2E を走らせても衝突しません（プロジェクト名・イメージタグとも
  worktree 名から導出して一意化しています）
- DB / Redis / MinIO は tmpfs なので、毎回シードからやり直した状態で始まります

## 構成

- `features/**/*.feature` — 日本語 Gherkin のシナリオ
- `steps/**/*.ts` — ステップ定義
- `playwright.config.ts` — `defineBddConfig` と Playwright 設定
- `docker-compose.e2e.yml` — E2E 専用の使い捨てフルスタック（DB / Redis / MinIO /
  backend / frontend / gateway ＋ テストランナー）

ブラウザの `baseURL` は環境変数 `BASE_URL`（既定 `http://store1.kizuna.test`）で
切り替えます。store / platform の判別は Host ヘッダで frontend middleware が行うため、
gateway サービスに network alias `store1.kizuna.test` / `kizuna.test` を付与しています。
alias はネットワーク単位なので、スタックを複数立てても互いに干渉しません。

## シナリオの追加手順

1. `features/` に `.feature` を追加し、先頭に `# language: ja` を記述する
2. `steps/` に対応するステップ定義を追加する（`createBdd()` の `Given` / `もし` 等）
3. `task e2e` で実行する

日本語 Gherkin のキーワードは以下を使用できます（英語キーワードも併用可）。

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
**開発スタック（`task up`）が起動済み**で、`store1.kizuna.test` がホストから名前解決できること
——E2E 専用スタックはホストポートを公開しないため、この経路では使えません。開発スタックの
データに対して走ることになるので、書き込みを伴うシナリオの扱いには注意してください。

```bash
cd e2e
npm ci
npx bddgen        # .feature からテストコードを生成
npx playwright test
```
