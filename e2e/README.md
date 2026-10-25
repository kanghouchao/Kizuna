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

### 型検査

`task lint service=e2e` は、前端と同じ固定NodeイメージでE2E自身の依存とTypeScriptを使い、
`tsconfig.json`に含まれる手書きTypeScriptを検査します。ブラウザやDBは起動しません。
生成済みの`.features-gen`と実行成果物は検査対象外です。Playwrightの変換・実行だけでは型の整合性は保証されません。

標準`task lint`とCIのRepo Lintにこの検査を含め、フルE2Eのランナーもシナリオ生成前に実行します。
CIでフルスタックのE2Eを実行しない運用は維持します。ホストでの高速な反復は`e2e/`で
`npm install`の後に`npm run typecheck`を実行できます。

### 開発スタックとの関係

E2E は**開発スタック（project `kizuna`）を使いません**。専用の使い捨てスタックを毎回立て、
終わったら壊します。そのため

- 開発スタックを起動したまま E2E を走らせても、互いのデータもコンテナも壊れません
- プロジェクト名・イメージタグは worktree ディレクトリの basename を小文字にし、英数字以外を
  `-` に置換して導出します。この結果が異なる worktree 同士は並行実行できます。同じ basename や
  正規化後の名前が一致する checkout、同じ worktree 内の複数実行は互いのスタックを回収するため並行実行できません
- DB / Redis / SeaweedFS は tmpfs なので、毎回シードからやり直した状態で始まります
- シードにアカウントは無く、最初に走る `initial-setup` プロジェクトが初期設定画面からスイートのアカウントを作ります

## 構成

- `features/*.feature` — 日本語 Gherkin のシナリオ（`chromium` プロジェクト）
- `features/initial-setup/*.feature` — 初期設定のシナリオ（`initial-setup` プロジェクト）。
  `admin@kizuna.test`（グループ管理者）、`tanaka.hanako@kizuna.test`（店長・店舗 1/2）、
  `yamada.jiro@kizuna.test`（店舗スタッフ・店舗 1）をパスワード `pass1234` で作ります
- `steps/**/*.ts` — ステップ定義（`initial-setup` プロジェクトは `steps/initial-setup.steps.ts` だけを読みます）
- `base-url.ts` — 店舗の `BASE_URL` とアプリドメインの `APP_URL` の既定値
- `playwright.config.ts` — `defineBddProject` による 2 プロジェクトと Playwright 設定。
  `chromium` は `dependencies` で `initial-setup` の完了を待ち、`initial-setup` が失敗すると実行されません
- `docker-compose.e2e.yml` — E2E 専用の使い捨てフルスタック（DB / Redis / SeaweedFS /
  backend / frontend / gateway ＋ テストランナー）

ブラウザの `baseURL` は環境変数 `BASE_URL`（既定 `http://store1.kizuna.test`）で
切り替えます。アプリドメイン側は `APP_URL`（既定 `http://kizuna.test`）です。
スイートは共有店舗の状態を扱うため、`workers: 1`・`fullyParallel: false` で直列実行します。
store / app の判別は Host ヘッダで frontend proxy が行うため、
gateway サービスに network alias `store1.kizuna.test` / `kizuna.test` を付与しています。
alias はネットワーク単位なので、スタックを複数立てても互いに干渉しません。

## シナリオの追加手順

1. `features/` 直下に `.feature` を追加し、先頭に `# language: ja` を記述する（`features/initial-setup/` は初期設定専用）
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

シードにアカウントは無いため、この経路は**作り直した直後でまだ誰も初期設定をしていない開発 DB**
に限ります。最初に `initial-setup` プロジェクトを 1 回だけ流し、スイートのアカウント 3 件を
パスワード `pass1234` で作ります。以後は初期設定が完了済みになり `initial-setup` は落ちるので、
反復では `--no-deps` で依存を外します（`--project=chromium` だけでは依存も走ります）。
自分の管理者を初期設定で登録済みの開発 DB では、そのまま全シナリオを実行できません。
通常の検証は毎回まっさらなスタックを使う `task e2e` を使用してください。

```bash
cd e2e
npm install
npx playwright install chromium
npm run typecheck && npx bddgen                 # 型検査 → .feature の変換
npx playwright test --project=initial-setup     # DB を作り直した後に 1 回だけ（アカウント作成）
npx playwright test --project=chromium --no-deps  # 反復はこちら
```
