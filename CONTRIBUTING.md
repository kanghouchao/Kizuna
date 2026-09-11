# 開発・貢献ガイド

変更は一つの目的に絞り、実装と検証結果をレビューできる単位で提出する。環境の準備は [README](README.md)、領域の規約は [AGENTS.md](AGENTS.md) から辿る。

## 実装前

- 用語は [CONTEXT.md](CONTEXT.md)、既存の設計判断は [ADR](docs/README.md) を確認する。
- API の追加・変更は [API 契約規約](backend/AGENTS.md#api-contract)に従う。前後端にまたがる変更では、端点・メソッド・成功／失敗コード・要求／応答の型と省略可否・授権・ページングを実装前に提示して承認を得る。
- UI は [デザインシステム](frontend/DESIGN.md)、スキーマは [DB 規約](backend/src/main/resources/db/AGENTS.md)を確認する。
- 人向け文書、コメント、issue、PR、コミットメッセージは日本語。AI 指令は英語。識別子とコマンドは翻訳しない。

## コマンドと検証

最終検証は Taskfile を使い、Docker によって CI と条件を揃える。省略可能な `service=frontend` / `service=backend` は対応するタスクで対象を限定する。全コマンドは `task help` を参照する。

| コマンド | 用途 |
| --- | --- |
| `task build` | 前後端の本番イメージをビルド |
| `task lint` | Repo Lint と前後端の整形・静的検査 |
| `task lint-repo` | actionlint によるワークフロー検査 |
| `task test-unit` | 前後端の単体テストとカバレッジゲート |
| `task test-integration` | バックエンド統合テスト |
| `task test` | 前後端単体テストとバックエンド統合テスト |
| `task e2e` | 独立した使い捨てスタックで E2E |
| `task format` | 自動整形（差分を確認する） |
| `task up` / `task down` | 開発スタックの起動／停止 |
| `task logs service=backend` | 指定サービスのログ |
| `task clean` | ビルド済みイメージの削除。DB ボリュームは対象外 |

高速な反復では `frontend/` の npm scripts と `backend/` の Gradle wrapper を使える。バックエンドは **JDK 25** 必須（`.java-version` と daemon JVM 設定）。フロントエンド lint は `format:check`、`lint`、`lint:fsd`、`typecheck` の四つ。

カバレッジの正本は [Jest 設定](frontend/jest.config.cjs)と [Gradle 設定](backend/build.gradle)。単体テストが測定対象で、Jest は行／文 70%、分岐 60%、関数 55%、Jacoco は行 70% を要求する。生成 DTO・設定等の除外範囲も設定を参照する。

判定はコマンドの **exit code のみ**を使う。実施していない検証は未実施と記載する。

## CI と PR

[CI](.github/workflows/lint-and-test.yml) は `Lint and Test (frontend)`、`Lint and Test (backend)`、`Repo Lint` の三チェック。前後端はそれぞれ lint・単体テスト・本番 build を実行する。コード領域に触れない docs-only 差分では前後端の重いステップを省略するが、Repo Lint は常に走る。`frontend/`・`backend/` 等の配下の文書変更もコード領域判定に入る。

統合テストと E2E は CI で実行しない。PR 作成前は `task lint`、`task test`、`task build`、`task e2e` とローカルコードレビューを実施し、[PR テンプレート](.github/pull_request_template.md)の検証欄に結果を記す。E2E の実行・成果物・日本語 Gherkin は [E2E ガイド](e2e/README.md)を参照する。

issue は [機能](.github/ISSUE_TEMPLATE/feature.md)／[不具合](.github/ISSUE_TEMPLATE/bug.md)テンプレートを使う。コミットの要約は短く、PR タイトルは conventional commit 形式と日本語を使う。非自明な判断は理由を説明し、関連 issue を紐づける。

master への同期は rebase。master を作業ブランチへ merge しない。PR のマージは所有者が手動で行う。Git・データ保護の禁止操作は [共通ガードレール](AGENTS.md#repository-wide-guardrails)に従う。
