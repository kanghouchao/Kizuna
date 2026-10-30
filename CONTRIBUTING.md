# 開発・貢献ガイド

変更は一つの目的に絞り、実装と検証結果をレビューできる単位で提出する。環境の準備は [README](README.md)、領域の規約は [AGENTS.md](AGENTS.md) から辿る。

## 実装前

- 用語は [GLOSSARY.md](GLOSSARY.md) を確認する。設計判断を見直す前に、対象の実装・テスト・Git 履歴を確認する。
- API の追加・変更は [API 契約規約](backend/AGENTS.md#api-contract)に従う。前後端にまたがる変更では、端点・メソッド・成功／失敗コード・要求／応答の型と省略可否・授権・ページングを実装前に提示して承認を得る。
- UI は [デザインシステム](frontend/DESIGN.md)、スキーマは [DB 規約](backend/src/main/resources/db/AGENTS.md)を確認する。
- 人向け文書、コメント、issue、PR、コミットメッセージは日本語。AI 指令は英語。識別子とコマンドは翻訳しない。

## コマンドと検証

最終検証は Taskfile を使い、Docker によって CI と条件を揃える。`build`・`lint`・`test`・`format`・`clean` は `service=frontend` / `service=backend` で対象を限定できる。単体テストを片側だけ実行する場合は `task test service=frontend` / `task test service=backend` を使う。全コマンドは `task help` を参照する。

| コマンド | 用途 |
| --- | --- |
| `task build` | 前後端の本番イメージをビルド |
| `task lint` | 前後端の整形・静的検査とE2Eの型検査 |
| `task lint service=e2e` | E2Eの型検査のみ（ブラウザ・DB不要） |
| `task lint service=.github` | actionlint によるワークフロー検査（明示指定時のみ。`task -d .github lint` でも実行可） |
| `task test` | 前後端の単体テストとカバレッジゲート |
| `task test:integration` | バックエンドの統合テスト（`*IntegrationTest`）を compose の使い捨て PostgreSQL・Redis で実行 |
| `task e2e` | 独立した使い捨てスタックで E2E（`grep=正規表現` で対象を限定） |
| `task format` | ホストの npm / Gradle による自動整形（差分を確認する） |
| `task up` / `task down` | 開発スタックの起動／停止 |
| `task -d infrastructure/development logs service=backend` | 開発環境の指定サービスのログ |
| `task clean` | ビルド済みイメージの削除。DB ボリュームは対象外 |

高速な反復では `frontend/` の npm scripts と `backend/` の Gradle wrapper を使える。バックエンドは **JDK 25** 必須（`.java-version` と daemon JVM 設定）。フロントエンドの Node.js は [Dockerfile](frontend/Dockerfile) の版に揃える。`task format` もホストの Node.js / JDK を使う。フロントエンド lint は `format:check`、`lint`、`lint:fsd`、`typecheck` の四つ。

カバレッジの正本は [Jest 設定](frontend/jest.config.cjs)と [Gradle 設定](backend/build.gradle)。単体テストが測定対象で、Jest は行／文 70%、分岐 60%、関数 55%、Jacoco は行 70% を要求する。生成 DTO・設定等の除外範囲も設定を参照する。

実 PostgreSQL テスト層は、部分一意索引・ロック・トリガ・Liquibase の実適用など、モックでは確かめられない振る舞いを `src/test` 内の `*IntegrationTest` クラスで検証する。かつての独立した `integrationTest` ソースセット（#975 で削除）の後継で、別のソースセットは作らない。各クラスは `@EnabledIfIntegration` を付け、接続は `IntegrationTestDatabase` から取る（テストクラスごとに使い捨てスキーマを一つ切る）。`task test` では環境変数 `KIZUNA_POSTGRES_TEST_JDBC_URL` が無いため飛ばされ（IDE などから個別に流す場合は `_JDBC_URL`・`_USERNAME`・`_PASSWORD` の三つを与える）、`task test:integration` が `backend/docker-compose.integration.yml` で tmpfs 上の `dhi.io/postgres:18-alpine3.23`（と認証失効の検証用の使い捨て Redis）を立てて全クラスを実行し、一つでも skip されたら失敗にする。コンテナとネットワークは成功・失敗にかかわらず破棄され、ボリュームは作られない。カバレッジゲートの対象外。

判定はコマンドの **exit code のみ**を使う。実施していない検証は未実施と記載する。

## CI と PR

[CI](.github/workflows/lint-and-test.yml) は `Lint and Test (frontend)`、`Lint and Test (backend)`、`Integration Test (backend)`、`Repo Lint` の四チェック。全量 E2E は PR では走らず、master への push ごとに別の [E2E](.github/workflows/e2e.yml) ワークフローが 4 分割で並列実行する。前後端はそれぞれ lint・単体テスト・本番 build を実行し、`Integration Test (backend)` は `task test:integration` を実行する。コード領域に触れない docs-only 差分では前後端の重いステップを省略するが、Repo Lint は常に走り、E2Eの型検査も含む。`frontend/`・`backend/` 等の配下の文書変更もコード領域判定に入る。

別の [CodeQL](.github/workflows/codeql.yml) ワークフローが Java と JavaScript / TypeScript を解析する。master 向け PR、master・`releases/**` への push、週次スケジュールが対象で、docs-only のスキップはない。主 CI の四チェックだけで全ワークフローを表すわけではない。

E2Eの静的型検査はRepo Lintで実行する。PR 作成前は `task lint`、`task test`、`task build`、バックエンドの永続化に触れる変更では `task test:integration`、変更に関係するシナリオだけの `task e2e grep=...` とローカルコードレビューを実施する。どのシナリオを選んだかと理由を PR に書き、レビューで選択を確認する。master の全量 E2E が落ちると issue が立つので、次の PR で直すか、原因の合併を revert する。結果は [PR テンプレート](.github/pull_request_template.md)の検証欄に記す。E2E の実行・成果物・日本語 Gherkin は [E2E ガイド](e2e/README.md)を参照する。

issue は [機能](.github/ISSUE_TEMPLATE/feature.md)／[不具合](.github/ISSUE_TEMPLATE/bug.md)テンプレートを使う。コミットの要約は短く、PR タイトルは conventional commit 形式と日本語を使う。非自明な判断は理由を説明し、関連 issue を紐づける。

master への同期は rebase。master を作業ブランチへ merge しない。PR のマージは所有者が手動で行う。Git・データ保護の禁止操作は [共通ガードレール](AGENTS.md#repository-wide-guardrails)に従う。
