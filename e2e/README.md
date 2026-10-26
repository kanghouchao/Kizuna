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

`task e2e` は自己完結で、以下を順に行います（1 は `task e2e:build`、2〜4 は `task e2e:run` として単独でも実行できます）。

1. frontend / backend の Docker イメージを worktree 固有のタグ（`e2e-<worktree 名>`）でビルド
2. 同じ E2E プロジェクトの残存コンテナと匿名ボリュームを回収し、ネットワークを破棄
3. E2E 専用スタックを起動して実行（`e2e/docker-compose.e2e.yml`。gateway の healthy を待ち、
   ランナーが gateway 経由の公開サイトへの疎通を確認してからテストを始める）
4. 成否にかかわらずスタックを破棄（`defer`）

合否は E2E コンテナの退出コードで判定します。初回の再試行で採取する `trace` と失敗時の
`screenshot` が `e2e/test-results/`、HTML レポートが `e2e/playwright-report/` に
残ります（いずれも Git 管理外）。

### 対象の絞り込みと分割

変更に関係するシナリオだけを回すときは、本体シナリオに対する正規表現を `grep` で渡します。
正規表現はプロジェクト名・ファイル名・シナリオ名をつないだ文字列に当たるので、feature ファイル名でも絞れます。
`initial-setup` は絞り込みの対象外で、常に先に実行されます。

```bash
task e2e grep=order-lifecycle
```

`shard=1/4` のように渡すと、全体を分割したうちの一つだけを実行します（CI が使う形）。

### 増分と全量

PR を出す前は、変更に関係する feature だけを `task e2e grep=...` で実行し、選んだ feature と理由を PR に書きます
（レビューで選択の妥当性を確認するため）。ローカルでは全量を実行しません。`steps/store-api.ts`・`playwright.config.ts`・
E2E のスタック・認証・店舗スコープのように多くのシナリオが共有する部分に触れたときは、その部分を通る feature まで選択を広げます。

全量は master へ入った後に [E2E ワークフロー](../.github/workflows/e2e.yml)が実行します。Actions の画面から任意のブランチで手動実行もでき、その失敗は issue になりません。イメージを一度だけ
ビルドし、4 つの shard がそれぞれ自分の使い捨てスタックで `task e2e:run shard=i/4` を実行し、最後の `E2E` job が
blob レポートを一つの HTML にまとめます。合併が続いたときは最新の master だけを検証します。統合済みの HTML レポートは
artifact `e2e-html-report`、失敗した shard の trace は `e2e-test-results-<shard>` に残ります。

失敗すると、題名「master の全量 E2E が失敗している」の未解決 issue を立てます（既にあれば追記します）。次の PR で直し、
すぐに直せない場合は原因の合併を revert します。

### 型検査

`task lint service=e2e` は、前端と同じ固定NodeイメージでE2E自身の依存とTypeScriptを使い、
`tsconfig.json`に含まれる手書きTypeScriptを検査します。ブラウザやDBは起動しません。
生成済みの`.features-gen`と実行成果物は検査対象外です。Playwrightの変換・実行だけでは型の整合性は保証されません。

標準`task lint`とCIのRepo Lintにこの検査を含め、フルE2Eのランナーもシナリオ生成前に実行します。
ホストでの高速な反復は`e2e/`で
`npm install`の後に`npm run typecheck`を実行できます。

### 開発スタックとの関係

E2E は**開発スタック（project `kizuna`）を使いません**。専用の使い捨てスタックを毎回立て、
終わったら壊します。そのため

- 開発スタックを起動したまま E2E を走らせても、互いのデータもコンテナも壊れません
- プロジェクト名・イメージタグは worktree ディレクトリの basename を小文字にし、英数字以外を
  `-` に置換して導出します。この結果が異なる worktree 同士は並行実行できます。同じ basename や
  正規化後の名前が一致する checkout、同じ worktree 内の複数実行は互いのスタックを回収するため並行実行できません
- DB / Redis / SeaweedFS は tmpfs なので、毎回シードからやり直した状態で始まります
- シードに店舗もアカウントも無く、最初に走る `initial-setup` プロジェクトが初期設定画面から管理者を登録し、
  スイートの店舗とアカウントを作ります

## 構成

- `features/*.feature` — 日本語 Gherkin のシナリオ（`chromium` プロジェクト）
- `features/initial-setup/*.feature` — 初期設定のシナリオ（`initial-setup` プロジェクト）。
  店舗 1（`Sample`・`store1.kizuna.test`）と店舗 2（`Sample 2`・`store2.kizuna.test`）を登録し、
  `admin@kizuna.test`（統括）、`tanaka.hanako@kizuna.test`（店長・店舗 1/2）、
  `yamada.jiro@kizuna.test`（スタッフ・店舗 1）をパスワード `pass1234` で作ります
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

シードに店舗もアカウントも無いため、この経路は**作り直した直後でまだ誰も初期設定も店舗登録もしていない開発 DB**
に限ります。最初に `initial-setup` プロジェクトを 1 回だけ流し、スイートの店舗 2 件とアカウント 3 件
（パスワード `pass1234`）を作ります。以後は初期設定が完了済みになり `initial-setup` は落ちるので、
反復では `--no-deps` で依存を外します（`--project=chromium` だけでは依存も走ります）。
自分の管理者や店舗を登録済みの開発 DB では、そのまま全シナリオを実行できません。
通常の検証は毎回まっさらなスタックを使う `task e2e` を使用してください。

```bash
cd e2e
npm install
npx playwright install chromium
npm run typecheck && npx bddgen                 # 型検査 → .feature の変換
npx playwright test --project=initial-setup     # DB を作り直した後に 1 回だけ（アカウント作成）
npx playwright test --project=chromium --no-deps --grep order-lifecycle  # 反復は関係する feature に絞る
```
