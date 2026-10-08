# 匿名件数のオフライン三方照合

外部から提供された同一バッチ・同一の論理照合スナップショット・同一範囲・同一口径の件数を、旧側基準／新側導入／ID対応表行数の三方で比較する開発スクリプト。実行主体やDBへの接続設定は持たない。

`MATCHED` は提出された分類目録と件数の三方一致だけを示す。分類の網羅性、データが実際に匿名であること、抽出条件や写像の正しさを証明せず、移行準備完了を意味しない。`snapshot_id` は共通の論理照合スナップショットを指し、三回の物理抽出の日時や識別子ではない。金額・人工抽様・実移行は常に未評価。

## 実行

Nodeイメージは既存の `frontend/Dockerfile` から取得する。初回のイメージ取得だけがネットワークを使用し、照合・試験コンテナはネットワーク無効、ファイルシステムとマウントは読取専用で動作する。資格情報・ホスト環境・Docker socketをコンテナへ渡さない。

```sh
task -d scripts/count-reconciliation prepare
task --exit-code -d scripts/count-reconciliation reconcile \
  < docs/specs/0397-count-reconciliation-fixtures/matched.input.json
```

標準入力はUTF-8のJSON一件、標準出力はJSON一件と改行。TTYを使わない。入力や参照先へ書き込まず、報告の自動保存もしない。Taskの `--exit-code` を省くとTask自身のエラーコードに変換される。TaskやDockerの実行診断は標準エラーへ出る場合があるが、JSONの標準出力へ混ぜない。

| 結果 | CLI終了コード |
| --- | --- |
| 指定範囲で三方一致 `MATCHED` | 0 |
| 差額あり `MISMATCH` | 1 |
| 入力不備・上限超過 `INVALID` | 2 |
| CLIが捕捉した標準入出力障害、固定診断 `IO_ERROR` | 3 |

通常のCLI標準エラーは空。NodeやDocker自体が起動できない場合の終了コードは実行基盤に従う。

## 契約

[入力・出力仕様](../../docs/specs/0397-count-reconciliation.md)と[合成fixture](../../docs/specs/0397-count-reconciliation-fixtures/README.md)を参照する。原始入力1 MiB、JSONのobject/array構造64層、分類／豁免各1000件、件数19桁かつ非負int64を上限とする。不正UTF-8、BOM、復号後の重複JSONキー、欠落・重複・未知分類を拒否し、部分結果を返さない。

差額は `new_minus_old` と `mapping_minus_old`。豁免は必須の入帳先を含め独立に保持し、旧件数より多くても件数の型・範囲が正しければ受理する。同分類・同コードの記録も重複除去しない。豁免の合計・承認・差額控除は行わず、`bookkeeping_location` は文字列参照として出力するだけで解決しない。

## 検証

```sh
task lint service=scripts/count-reconciliation
task test service=scripts/count-reconciliation
```

lintは `node --check` の構文検査。試験はNode標準 `node:test` から実CLI子プロセスを起動し、完全な報告・終了コード・stdin/stdout障害を確認する。DBやアプリケーションスタックは不要。Repo LintのCIジョブはこの検査を常に実行する。

高速反復では `node scripts/count-reconciliation/reconcile.mjs < 入力.json` と `node --test scripts/count-reconciliation/reconcile.test.mjs` を利用できる。最終検証は上記Task/Docker経由で行う。
