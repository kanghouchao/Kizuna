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

1. `task build` — frontend / backend の Docker イメージをビルド
2. `task up`（`--wait`）— 開発スタック（project `kizuna`）を起動
3. gateway 経由のヘルスポーリング（`curl -H "Host: store1.kizuna.test" http://localhost/`）
4. one-shot コンテナで E2E 実行（`e2e/docker-compose.e2e.yml`）
5. 成否にかかわらず `task down` でスタックを停止（`defer`）

合否は one-shot コンテナの退出コードで判定します。失敗時は `trace` /
`screenshot` が `e2e/test-results/`、HTML レポートが `e2e/playwright-report/` に
残ります（いずれも Git 管理外）。

## 構成

- `features/**/*.feature` — 日本語 Gherkin のシナリオ
- `steps/**/*.ts` — ステップ定義
- `playwright.config.ts` — `defineBddConfig` と Playwright 設定
- `docker-compose.e2e.yml` — 実 dev スタックの `kizuna_network` に attach する
  one-shot 実行コンテナ

ブラウザの `baseURL` は環境変数 `BASE_URL`（既定 `http://store1.kizuna.test`）で
切り替えます。tenant / central の判別は Host ヘッダで frontend middleware が行うため、
gateway サービスに network alias `store1.kizuna.test` / `kizuna.test` を付与しています。

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

Docker を使わず手元の Node で回す場合は、`e2e/` で以下を実行します
（対象スタックが起動済みで、`store1.kizuna.test` が名前解決できることが前提）。

```bash
cd e2e
npm ci
npx bddgen        # .feature からテストコードを生成
npx playwright test
```
