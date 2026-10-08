# 0397 匿名件数の三方照合

状態: 主タスクの独立技術レビューで契約・配置・検証境界を承認済み。独立Nodeスクリプトとして実装・検証する。

## 問題と範囲

[#397 要件5](https://github.com/kanghouchao/Kizuna/issues/397) と [#371 裁定](https://github.com/kanghouchao/Kizuna/issues/371#issuecomment-5003238756) のうち、外部提供の匿名集計を使う件数照合だけを扱う。同じバッチ・スナップショット・対象範囲・集計定義で、旧側基準件数、新側導入件数、ID対応表行数を比較する。

この作業で使用するデータは合成データのみ。集計の取得元へ接続せず、ID対応表の内容・会員関連・残高・授権・公開動作を読み書きしない。親 #397 は開いたままとし、#173 / #355 の `not_planned` を変更しない。#1009 / #1010 の承認待ち作業を含めない。

## 利用者が得る結果

1. 担当者は三方の件数と差額を分類別に確認できる。
2. 欠落した分類を零件として扱わず、入力不備として確認できる。
3. 分類の重複や不正な件数を黙って合算・丸めず、入力不備として確認できる。
4. 数字が同じでも比較条件が違えば、比較不能と分かる。
5. 豁免件数と根拠を独立して確認でき、差額の控除には使われない。
6. 件数が一致しても金額・人工抽様・実移行は未評価だと分かる。
7. 同じ入力から同じ報告が得られ、照合が入力ファイルを変更しない。
8. 実際の移行対応関係や集計元の正確性まで検証済みと誤解しない。

## 配置と入口

基準コミット `c2e2d32638cc08bbdc1f454042c476880aa06371` に汎用オフラインCLIは存在しないが、独立した開発スクリプトを禁じる規約もない。`frontend/Dockerfile` は `node:24.7.0-alpine3.22` を使用し、ルートTaskfileは `service` に渡されたディレクトリの子Taskfileへ委譲する。`.github/Taskfile.yml` はアプリケーション以外の検査タスクの先例である。

配置は `scripts/count-reconciliation/` に `reconcile.mjs`、`reconcile.test.mjs`、`Taskfile.yml`、日本語READMEを置く。仕様・票・合成fixtureは現在のdocs配下に置く。Node標準の `fs`、`JSON`、`BigInt`、`node:test`、`node:assert/strict`、試験用 `node:child_process` だけを使い、package.json・lockfile・npm install・製品モジュール・HTTP入口を増やさない。

ホスト反復は `node scripts/count-reconciliation/reconcile.mjs < 入力.json`、最終受け入れは `task test service=scripts/count-reconciliation`。Docker経由の利用は `task --exit-code -d scripts/count-reconciliation reconcile < 入力.json` とし、Task既定のエラーコードへの変換を避ける。子Taskは既存と同じNodeイメージを使い、`--network none --read-only`、必要なスクリプトと合成fixtureのみの読取専用マウント、環境変数・認証情報の引渡しなしで動く。

CIのRepo Lintジョブへ子Taskの検査・試験を明示的に追加する。現在のdocs-only判定はルート `scripts/` を認識しないため、前後端ジョブの実行だけをもってスクリプト検証済みとはしない。子Taskのlintは `node --check` による構文検査と明記し、ESLint相当とは扱わない。

Node `24.7.0` のDocker環境で子Taskのlintと25組の受け入れ試験を実行し、exit 0を確認した。Task `3.54.0` の `--exit-code` を使用する。実装票は #1014、最新の最終検証結果はPRに記録する。

## 最小入力契約

厳格なUTF-8の単一JSONオブジェクトを受け取る。CLIは標準入力だけを入力境界とする。

### 読み取り・資源上限

- 入力全体はJSON前後の空白も含む原始バイトで最大1 MiB（1,048,576バイト）。標準入力をchunk単位で受け取り、判定は原始バイト順で行う。上限を超えるバイトを蓄積する前に停止し、入力を閉じて `INPUT_TOO_LARGE` を返す。EOFを待って無制限に全入力を読み終えてから判定しない。
- 上限内のバイト列をfatalモードのUTF-8デコーダで復号する。不正・切断された多バイト列を置換文字へ変えず `INVALID_UTF8` とする。UTF-8 BOMは受理せず `INVALID_JSON` とする。
- JSONのobjectとarrayをそれぞれ一層と数え、根を一層として最大64層。65層目へ入る前に `LIMIT_EXCEEDED`、pathは根の空文字列とする。文字列内の括弧は層数に含めない。
- `expected_categories` と三方それぞれの `counts` は各1〜1000項目、`exemptions` は0〜1000項目。1001項目以上は `LIMIT_EXCEEDED`、pathは該当する既知配列を指す。
- `count` は文字列型・最大19文字・正規十進表記を検証してから `BigInt` へ渡す。長さ超過は `COUNT_OVERFLOW`。19桁以下でも最大値を超えるものは同じコードで拒否する。

すべての上限違反・復号エラーは `INVALID`、exit 2とし、contextはnull、categoriesとexemptionsは空配列。部分成功を出さない。

| フィールド | 型と意味 |
| --- | --- |
| `version` | 数値 `1` のみ |
| `expected_categories` | 必須の1〜1000件の分類キー配列。重複不可 |
| `old` / `new` / `mapping` | 下記の匿名集計。三方すべて必須 |
| `exemptions` | 必須の0〜1000件の配列。該当なしも省略せず `[]` |

三方それぞれの集計は `batch_id`、`snapshot_id`、`scope_id`、`basis_id` と `counts` を持つ。前四項は匿名の非空識別子で、三方すべて同じ値でなければ比較しない。識別子・分類キー・豁免コードはASCII英数字と `._:-` の1〜120文字とし、自動trim・大文字小文字変換はしない。

`counts` は `{category, count}` の配列。`expected_categories` の各分類をちょうど一行ずつ含むことを要求する。未知分類、分類欠落、重複分類を拒否する。配列の入力順序は比較結果に影響しない。`expected_categories` 自体が指定範囲を定めるので、全分類の網羅を機械が証明したとは表示しない。

`count` は正規の十進数字列 `0|[1-9][0-9]*`、範囲は `0..9223372036854775807`。JSON数値、負数、負の零、先頭零、符号、小数、指数、空白、null、欠落、範囲超過を拒否する。数値へ暗黙変換しない。差額は正確な整数演算で求め、十進文字列にする。分類を跨ぐ合計値は単位が異なるため作らない。

すべてのオブジェクトで未知フィールド・重複JSONキーを拒否する。前検査はJSONの構造走査・深度制限・同一object内の復号済みキーの重複検出だけを担い、値を組み立てる汎用解析器にはしない。前検査後に標準 `JSON.parse` で値を構築し構文を検証する。キーの復号にも標準の文字列解析を使い、`"count"` と `"\u0063ount"` の重複を検出する。文字列内の偽キー・エスケープを正規表現やreviverで判定せず、evalを使わない。重複キーは `INVALID_JSON`、pathは根の空文字列。入力不備が一件でもあれば全体を無効とし、正常行だけの部分報告を出さない。

### 比較可能性の意味

`MATCHED` は提出された分類目録と件数の三方一致だけを証明する。目録の完全性、データが実際に匿名であること、抽出・写像の正しさ、移行準備完了を証明しない。`snapshot_id` は三方共通の論理照合スナップショットであり、三回の物理抽出の時刻や識別子ではない。

`basis_id` は外部で定めた分類別計数単位・抽出条件・重複処置・削除行の扱い・対応表行の粒度を指す。値の一致は提供者の申告の一致であり、実集計や一対一写像の正しさの証明ではない。

顧客・受注・明細・キャスト・残高を実際に何行と数えるかは本案では決めない。特にCast本人とCastEnrollment、残高行と金額、旧行と一対多の対応表行を同じ単位と仮定しない。実口径が未確認なら合成試験だけに留め、実データをこの契約へ当てはめない。

### 豁免

各項目は `{category, exemption_code, reason, count, bookkeeping_location, post_migration_constraint}` の六項目をすべて必須とする。分類は期待分類に存在する。同分類・同コードの豁免も独立した記録として保持し、重複除去しない。`count` は通常件数と同じ型・範囲。`bookkeeping_location` は件数の入帳先を指す匿名の集計位置参照で、ASCII英数字と `._:-` の1〜120文字とする。URL・ファイルパスとして解決・読み取りせず、報告へそのまま記載する。`reason` と `post_migration_constraint` は1〜1000 Unicodeコードポイント、空白のみ・制御文字を拒否する。個人情報や実IDを含めない。

豁免件数と旧側件数の大小関係は検証しない。旧側を超えても形式と整数範囲が正しければ受理する。豁免間の重複は判断できないため合計しない。報告に入帳先を含む各項目をそのまま独立表示し、三方の計数・差額から加減算しない。豁免の登録は業務上の豁免承認ではない。

## 最小出力契約

| フィールド | 意味 |
| --- | --- |
| `version` | `1` |
| `status` | `MATCHED` / `MISMATCH` / `INVALID` |
| `context` | 有効時は共通の四識別子、無効時は `null` |
| `categories` | 有効時は各分類の結果、無効時は `[]` |
| `exemptions` | 有効時は独立した全豁免、無効時は `[]` |
| `errors` | 有効時は `[]`、無効時は `{code, path}` の非空配列 |
| `not_evaluated` | 常に `["amounts", "manual_sampling", "real_migration"]` |
| `evidence_scope` | 常に `SUPPLIED_ANONYMOUS_COUNTS_ONLY` |

分類結果は `{category, old_count, new_count, mapping_count, new_minus_old, mapping_minus_old, status}`。三方が等しい場合だけ分類の `status` は `MATCHED`、他は `MISMATCH`。一分類でも差額があれば全体は `MISMATCH`。零は `"0"`、負差額は `"-1"` のように出力し、正符号は付けない。

分類結果をASCII/code-unitの分類キー順、豁免を同じ比較による分類キー・コード順に整列する。localeCompareは使わず、豁免の同順位は入力順を保持する。利用者の分類やキーはMap/Setで扱い、objectのprototype名と混同しない。エラーの `path` はJSON Pointer形式で入力位置を示す。既知フィールド名と配列添字だけを使い、未知キーの場合は直近の既知コンテナ、構文・復号・バイト上限・深度のエラーは根の空文字列を指す。入力値・未知キー名・JSON.parseの原文付き例外・stackを反映しない。エラーコードは `INPUT_TOO_LARGE`、`INVALID_UTF8`、`LIMIT_EXCEEDED`、`INVALID_JSON`、`INVALID_SCHEMA`、`INVALID_COUNT`、`COUNT_OVERFLOW`、`DUPLICATE_CATEGORY`、`MISSING_CATEGORY`、`UNKNOWN_CATEGORY`、`INCOMPARABLE_CONTEXT`、`INVALID_EXEMPTION`。一件以上の検出を要求するが全エラー列挙までは要求しない。同じ入力に対する選択順は固定する。

実行境界は標準入力からJSONを一件読み、標準出力に一件のJSON報告と末尾改行を返す。正常時の標準エラーは空。標準出力の完了を待ってexitCodeを設定し、process.exitによる非同期出力の切断を避ける。終了コードは一致 `0`、差額 `1`、入力不備 `2`。起動後にCLIが捕捉できるstdin/stdoutのI/O障害（EPIPEを含む）は `3` とし、標準出力に成功報告を出さず、標準エラーに固定の `IO_ERROR` だけを出す。NodeやDocker自体の起動失敗は外部実行基盤の終了コードであり、CLIの `3` と保証しない。入力ファイル・DB・ネットワークへ書き込まず、標準出力以外の成果物を自動保存しない。全体移行可否を表す `ready` 等は出力しない。

## 受け入れ計画

唯一の検証境界は「標準入力の原始バイト列 → JSON報告と終了コード」。内部関数だけを試験して合格としない。fixtureの分類はその試験の指定範囲であり、実体の移行対応標準ではない。入力例と期待報告は合成fixtureを参照する。以下を独立した入力に展開し、報告の完全一致または指定したエラーコードとJSON Pointer、終了コードを機械比較する。

| ケース | 入力・操作 | 期待 |
| --- | --- | --- |
| 一致 | 合成fixtureそのまま | MATCHED、exit 0、完全な期待報告 |
| 順序 | 各countsの配列を逆順にする | 基準と同じ報告 |
| 差額 | newのcustomerを `"9"` | MISMATCH、差額 `"-1"`、exit 1 |
| 対応表差額 | mappingのcustomerを `"11"` | MISMATCH、対応表差額 `"1"` |
| 豁免 | 上記の不足一件に同分類の豁免一件を付す | MISMATCHのまま、差額を控除しない |
| 重複豁免 | 同じ分類・コードの異なる記録と完全重複記録を投入 | すべて保持し、同順位は入力順、合計・承認・差額控除なし |
| 大きい豁免 | 旧件数10、新件数9、豁免件数11、入帳先あり | MISMATCHのまま差額-1、豁免11と入帳先を独立表示 |
| 零 | 全分類の三方に明示 `"0"`、豁免なし | MATCHED |
| 欠落 | old/new/mappingを一方ずつ省略 | INVALID_SCHEMA、exit 2 |
| 欠落分類 | 三方から一方ずつcustomer行を除く | MISSING_CATEGORY、exit 2、categories空 |
| 空・重複目録 | expected_categoriesを空または重複にする | INVALID_SCHEMA / DUPLICATE_CATEGORY |
| 重複分類 | 各方に同分類を追加 | DUPLICATE_CATEGORY、値が等しくても拒否 |
| 不明分類 | 目録外の行を追加 | UNKNOWN_CATEGORY |
| 型・表記 | countを `null`、数値1、`"-1"`、`"1.5"`、`"1e3"`、`"01"`、`" 1"` に変更 | INVALID_COUNT |
| 大整数 | 同分類の三方を `"9007199254740993"` | 精度を失わずMATCHED |
| 境界 | old=`"9223372036854775807"`、new=`"0"` | 正確な負差額 `"-9223372036854775807"` |
| 溢れ | `"9223372036854775808"` | COUNT_OVERFLOW |
| 比較不能 | 四識別子を各方で一つずつ違える | INCOMPARABLE_CONTEXT、部分比較なし |
| 豁免不備 | 省略、未知分類、負数、空理由、空制約、入帳先欠落・不正 | INVALID_SCHEMA / INVALID_EXEMPTION / 件数エラー |
| バイト上限 | 空白を含め1,048,576バイトと1,048,577バイト、後者はEOFを送らない | 前者は内容で判定、後者はEOF前にINPUT_TOO_LARGE、exit 2 |
| 複合不正入力 | 深度超過の後に不正UTF-8等を付け、同じバイト列を一括／分割入力する | 原始バイト順で同じ拒否理由を返し、EOF前の停止も保つ |
| UTF-8 | 正常な多バイト文字のchunk跨ぎ、不正列、末尾で切断した列、BOM | 正常跨ぎは許容、不正バイトはINVALID_UTF8、BOMはINVALID_JSON |
| 配列上限 | 分類目録・各方counts・豁免を各1000と1001件にする | 1000は内容で判定、1001はLIMIT_EXCEEDED |
| 深度上限 | JSON構造64層と65層、文字列内の多数の括弧 | 64層は通常のschema判定、65層はLIMIT_EXCEEDED、文字列内は数えない |
| 件数長 | 20桁以上の数字列、19桁の最大値・最大値+1 | BigInt変換前に長さを制限、溢れはCOUNT_OVERFLOW |
| 構文 | 不正JSON、通常・エスケープ同名の重複JSONキー、文字列内の偽キー、未知フィールド | 重複はINVALID_JSON、偽キーは重複と誤認しない、未知はINVALID_SCHEMA |
| エラーの秘匿 | 入力値・未知キーに合成の機密標識を含め、全拒否経路を通す | 標識をstdout/stderrへ出さず、context=null・categories=[]・exemptions=[] |
| 評価範囲 | 一致・差額・入力不備のすべて | 未評価三項とevidence_scopeを常に保持 |
| 副作用 | 同じ入力を二回、ネットワーク無効・入力読取専用で実行 | 同一報告、入力hash不変、保存物なし |

既存Nodeの標準ライブラリだけで子プロセスの標準入力・出力・終了コードを検証する受け入れ試験をTaskからDockerのネットワーク無効・読取専用マウントで実行する。合否はexit codeで記録する。Task/Docker経由のstdinと終了コード透過も別途確認する。

## 対象外と残る確認

金額規則、実際の分類粒度・写像口径、実移行の実行方式、ID対応表の生成・永続化、会員関連の生成、抽出・投入・切替、金額照合、人工抽様、個人情報の匿名化処理を含めない。実口径と金額ルールは業務側の確認が必要であり、数値比較の技術承認で代替しない。
