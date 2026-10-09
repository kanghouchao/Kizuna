# 0389 媒体別の広告費・問い合わせ記録集計：承認済み契約

## 状態と基点

2026-10-09 14:54:40 UTC、利用者が「嗯，就按这个方案做吧」と回答し、本書の業務規則と二つのGET契約を承認した（`Sentinel_d5039e0862f08191898c4f7703205e01`）。確認元は主タスクの `Sentinel_2a800b6ace388191ac8fd6c29d8be8be`。人数は重複未除外の入力値の和、媒体は完全一致、店舗側の照会とCSV/XLSX出力に限定する。

remote master を fetch し、`8d063c163681fae71d783867f8e30203237449f6`（PR #1024）を確認した。同コミットから独立 worktree `/tmp/kizuna-issue-389-media-summary`、ブランチ `codex/issue-389-media-summary` を作成した。既存 worktree・WIP は変更しない。

PR #1023 の費用記録・コピー・履歴・独立出力と、PR #1024 の運営帳票への広告費接続は完成済み。#388 は完了したため再実装しない。#387 の日次確認・税額は今回の対象ではない。

## 調査で確認できた事実

| 論点 | 現在の実装・契約 | 今回の扱い |
| --- | --- | --- |
| 媒体名 | `AdvertisingValues.text` が保存時に Java `String.strip()` を適用。最大200文字、必須。大小文字・全半角・別名の正規化はない。`AdvertisingDomainTest` に首尾空白除去の試験がある | 保存済み文字列の完全一致を採用。媒体の同一性を新設しない |
| 費用の単位 | 店舗×自然月の有効な費用行。区分は SALES / RECRUITMENT。会社・プランが異なる同名媒体の複数行も合法 | 店舗×月×区分×保存媒体名でグループ化 |
| 問い合わせ | 行単位の任意非負整数。null は未計測、0 は計測済み零。個人・イベント・重複排除キーはない | 実人数や問い合わせ件数として断定しない。承認済みの加算結果の意味を明示する |
| 月次の変更 | 月ロックなし。変更・削除・コピーで月版が進み、コピーは人数を null にする | 現在の有効行を再集計。保存済み帳票や月確定は作らない |
| スナップショット | `AdvertisingService.snapshot` は `@StoreScoped` と REPEATABLE_READ、DB読取り制限、全件上限を持つ | 集計・総計・月版を一回の読取りから生成する |
| 運営帳票 | `advertising::reporting` の公開境界は費用ID・店舗・月・版・区分・金額のみ | 本切片は advertising 内で完結。媒体や人数を #388 の既存応答へ追加しない |
| 権限 | 店舗に VIEW / MANAGE / EXPORT、平台の帳票に SET_VIEW / SET_EXPORT | 店舗の VIEW と EXPORT を再利用。平台へ拡張しない |

照合対象はリポジトリのコード・テスト・仕様・履歴と GitHub issue／PR。実店舗の本番データや個人記録は取得しておらず、現在の媒体表記の分布や問い合わせ重複率は断定できない。

## 承認された切片

店舗の広告費画面に「媒体別集計」の読取り面を追加する。選択店舗・選択月について、媒体ごとの登録広告費と入力済み人数の状況を示し、同じ契約で全件 CSV / XLSX を出力する。

- 営業広告と採用広告を別行にする。会社・プランでは分割しない。
- 媒体名は**保存済み文字列の完全一致**。英字大小・全半角・内部空白・Unicode正規化・別名を自動で同一視しない。新しい trim やデータ書換えも行わない。例えば `媒体A` と `媒体Ａ`、`ABC` と `abc` は別行。
- 金額はグループの非削除行の合計。分類別登録額は全グループを対象とし、表示ページだけを合計しない。広告会社・プランが違っても同じ保存媒体名・区分なら費用を合計する。
- 人数は非null値の算術和を **「入力済み人数の合計（重複未除外）」** として表示する。これは実人数・媒体の完全な反響数ではない。異なる媒体や営業／採用を横断した人数の総計は出さない。
- 同じグループの入力済み行数と未入力行数を併記する。全行未入力なら合計はnull、0を含む入力が一つでもあれば既知分の和を返す。一部未入力を「0人」「計測完了」と見せない。
- 問い合わせ単価・獲得単価・受注単価・新規受注単価は算出しない。媒体別の登録記録集計だけで #389 の反響指標全体を完了扱いにしない。
- 0円登録・未登録・未入力を区別する。変更・削除・区分／媒体の訂正は原月の次回読取りへ反映する。月をコピーした直後の人数は未入力として表示する。

### 承認済みの業務確認

**同じ保存媒体名・同じ区分の複数費用行について、入力人数を「入力済み人数の合計（重複未除外）」として足し、未入力行数とともに表示してよいか。媒体名は完全一致だけでまとめる方針と合わせて承認された。**

推奨は上記の記録値集計である。同一人物を数え分ける仕組みがないため「ユニーク人数」にはしない。これを望まない場合は人数合算を実装せず、行別の人数表示を維持し、別途媒体月次人数の正本・重複排除・既存値移行を決める。その追加設計を今回の承認に含めない。

本書の店舗限定、出力を含む HTTP 契約も同時に承認された。注文分母の質問は今回の実装に不要なので後続へまとめる。

## HTTP 契約

新規の二つの GET のみ。既存の費用更新・コピー・履歴・月サマリー・17列出力および #388 の経路と形式は変えない。

| メソッド・経路 | 入力 | 成功 | 権限 |
| --- | --- | --- | --- |
| GET `/store/advertising-media-summaries` | month、page、size | 200 `AdvertisingMediaSummaryResponse` | ADVERTISING_COST_VIEW |
| GET `/store/advertising-media-summaries/exports` | month、format | 200 全件CSVまたはXLSX | ADVERTISING_COST_VIEW ＋ ADVERTISING_COST_EXPORT |

認証、`X-Role: store`、`X-Store-ID`、storeBridge、授権店舗集合が必須。店舗IDを query/body で選ぶ経路は設けない。アプリケーション層で `@StoreScoped` を使用し、手書き store 条件だけで代替しない。既存の店舗選択に必要な STORE_VIEW の条件を維持する。SET_VIEW/SET_EXPORT だけではこの店舗APIを行使できず、実ロールへの授与も行わない。

### 要求とページング

- `month`: 必須string、`YYYY-MM`、年0001..9999。省略・空・不正月は400。サーバ既定月を作らない。画面は現在の広告費画面で選択済みの月をそのまま渡す。
- 照会の `page`: 省略時0、0以上の整数。`size`: 省略時20、1以上、100超は100に制限する。非整数・負page・1未満sizeは400。ページオフセットはlongで安全に計算し、最終ページ超過は空contentを返す。
- 並び順は SALES、RECRUITMENT の順、各区分内は保存媒体名の Java `String.compareTo` による昇順。媒体名と区分の組自体が集計行の一意キー。DB照合順序やロケールに依存させない。任意sort・媒体検索・分類filterは今回提供しない。
- 出力の `format`: 必須string、`csv` または `xlsx`。大文字・未知値・省略は400。出力に page/size は受理せず400。出力は選択月の全分類・全媒体。
- 今回の二経路では契約外queryキーと同一キーの重複を400にする。GET本文は使わない。

### JSON応答

すべてのキーは省略せず、以下でnull可と明記したもの以外は非null。全ての数値は有限の整数JSON number、0..9,007,199,254,740,991の範囲とし、丸めや小数・数値文字列を返さない。

| フィールド | 型 | 意味 |
| --- | --- | --- |
| store_id | 正整数number | 認証済み選択店舗 |
| month | string | 要求のYYYY-MM |
| month_version | 非負整数number | 同一スナップショットで読んだ広告費月版。未作成月は0 |
| generated_at | ISO8601日時string（offset付き） | 今回の生成日時 |
| basis | string固定 `advertising-media-records-v1` | 記録値集計の形式 |
| media_matching | string固定 `EXACT_STORED_NAME` | 保存名の完全一致 |
| inquiry_basis | string固定 `MANUAL_RECORDED_SUM_NOT_DEDUPLICATED` | 手入力値の和、重複未除外 |
| entry_count | 非負整数number | 月の有効費用行数 |
| recorded_total_amount | 非負整数number | 月の登録費用額合計（円） |
| category_totals | 配列、常に2要素 | SALES、RECRUITMENTの順。各要素はcategory、entry_count、recorded_amount |
| rows | Spring Page<AdvertisingMediaSummary> | 全集計行の総数とページ情報、content |

`category_totals` のcategoryは `SALES | RECRUITMENT`、entry_countとrecorded_amountは非負整数number。登録なしの分類も各値0で返す。分類別の問い合わせ人数総計は返さない。

各 `AdvertisingMediaSummary` のキー:

| フィールド | 型・null | 意味 |
| --- | --- | --- |
| category | SALES / RECRUITMENT | 区分 |
| media_name | string | 保存媒体名（1..200文字）。不透明な表示名で、共通媒体IDではない |
| entry_count | 正整数number | グループの費用行数 |
| recorded_amount | 非負整数number | グループの登録額合計（円） |
| recorded_inquiry_entry_count | 非負整数number | 人数が非nullの費用行数。0人入力も含む |
| unrecorded_inquiry_entry_count | 非負整数number | 人数がnullの費用行数 |
| recorded_inquiry_count_sum | 非負整数numberまたはnull | 入力済み人数の和。入力済み行が零の場合のみnull |
| inquiry_status | UNRECORDED / PARTIAL / RECORDED | 全行未入力／一部未入力／全行入力済み |

`RECORDED` は行の入力状況だけを示し、現実の反響の全量取得や重複排除完了を意味しない。既知0と未入力の混在は `PARTIAL`・合計0・未入力行数>0。空の月は200で `rows.content=[]`、各費用合計0、分類2要素を維持し、架空の媒体行や人数零行を作らない。

例: SALES・同名媒体に `amount=1000,inquiry_count=5`、`amount=2000,inquiry_count=null` がある場合、entry_count=2、recorded_amount=3000、recorded_inquiry_entry_count=1、unrecorded_inquiry_entry_count=1、recorded_inquiry_count_sum=5、inquiry_status=PARTIAL。同名のRECRUITMENTは独立した別行。

### 失敗・並行性・予算

- 401: 未認証／失効。403: 権限・店舗文脈・storeBridge・授権不足。400: 要求形式不正。503: 件数・資源・数値精度の安全域超過、読取り／生成時間超過、出力処理中。500: 既存の固定内部エラー。
- 固有IDを指定するAPIではないため、月の未登録による404はない。書込み・版の条件要求がないため通常409もない。既存の店舗解決による403を独自の404へ変えない。
- エラーは既存の `error:string`＋任意 `details:object`（field pathはsnake_case）。SQL・内部例外を公開しない。
- 一回のREPEATABLE_READ取引で有効費用行・月版を取得し、そこからページ前の全グループと分類総計を計算する。ページ間や別途ダウンロードの時点まで固定する保存スナップショットではなく、それぞれのGETは最新値を読む。
- 新規／訂正／削除と並行しても一応答の値は同一時点で揃う。月ロック・監査書込み・冪等キー・DBスキーマ追加は不要。GETの安全な再試行は可能だが、その間の変更は反映され得る。
- `app.advertising-cost` の既存設定を使用する。元の有効費用行は既定20,000件（maxOrders、設定可能範囲1..100,000）。集計後に少数になる場合も元行上限を回避しない。DB読取り10秒、全処理25秒、文字量4,000,000、出力16MiBの既定を維持する。上限超過を途中集計・省略・切捨てで成功にしない。
- 合算はlongのchecked加算とJavaScript安全整数検査。XLSX数値セルは既存の15桁上限も検査する。エラー後も出力枠を解放し再試行できる。
- ページと出力は `Cache-Control: no-store`。画面は店舗／月／ページ／表示面の変更で古い応答を捨てる。元ページへ戻った場合も応答世代を照合し、古い応答が新しい要求を上書きしない。失敗時に旧値を成功表示として残さない。

### CSV / XLSX

既存の費用明細出力を変更せず、新しい集計出力を提供する。安全なCSV/XLSXレンダラー・DocumentBudget・既存広告費の出力枠を再利用し、明細出力と媒体出力を合わせてプロセスあたり同時1件とする。#388の出力仕様は変更しない。

CSVの固定16列:

`record_type,generated_at,basis,media_matching,inquiry_basis,store_id,month,month_version,category,media_name,entry_count,recorded_amount,recorded_inquiry_entry_count,unrecorded_inquiry_entry_count,recorded_inquiry_count_sum,inquiry_status`

- `record_type=criteria`: 共通条件列と月のentry_count／recorded_amount（recorded_total_amountに対応）、他列は空欄。
- `record_type=category_total`: 共通条件列とcategory、分類のentry_count／recorded_amount、媒体・人数欄は空欄。必ず2行。
- `record_type=media`: 共通条件列と全集計フィールド。同一の順序で全媒体行を出す。
- null人数は空欄、既知0は数値0。媒体名・月・basis等は文字列。CSVはBOM、引用と数式開始文字保護、XLSXは明示的文字列／数値型を使用する。
- XLSXは「条件と総計」「媒体別集計」の2シート。同じ16列を持ち、前者にcriteriaとcategory_total、後者にmediaを配置する。人数の意味を条件列にも保持する。
- ファイル名 `advertising-media-summaries-<store_id>-<YYYY-MM>.csv|xlsx`、認証付きattachment、CSV `text/csv`、XLSX標準MIME。金額や人数は表示ページだけでなく全件を出す。
- 行別会社・プラン・費用ID・顧客／応募者情報・履歴・理由・操作者は含めない。既存の費用明細出力は根拠確認用として存続する。媒体からのドリルダウンAPIは今回追加しない。

## UIと受け入れ条件

既存の店舗広告費ページの月選択を共有し、「費用記録」「媒体別集計」のTabsで切り替える。集計は読取り専用。画面名は「媒体別集計」とし、広告効果やユニーク人数を計測済みと誤認させない。表・空・失敗・ページングは既存ListPage／TableCardと共有取得フックに従う。

- [x] 同店・月・区分・保存名の完全一致だけで集計する。会社／プラン差はまとめ、大小文字・全半角・内部空白差・他店・他月・他区分は分ける。
- [x] `[null,null]`→null/UNRECORDED、`[0,null]`→0/PARTIAL、`[5,null]`→5/PARTIAL、`[0,0]`→0/RECORDED、`[5,7]`→12/RECORDED。ラベルと入力／未入力行数を常に併記する。
- [x] 無登録、零円登録、コピー後の未入力、修正・削除・媒体／区分変更を原月へ再計算し、ページ外を含む分類総計と整合する。
- [x] 費用の通常変更と並行する実PostgreSQL試験で、月版・分類総計・媒体集計が同一スナップショットになる。
- [x] VIEWだけで照会可能、EXPORT不足で出力拒否、授権外店・誤コンソール・失効を直接APIでも拒否する。実ロールを自動変更しない。
- [x] 2,001以上の媒体行と複数ページ、設定した元行上限の超過、安全整数、CSV数式保護・引用・日本語・先頭零、XLSX型とnull／0、空月出力、失敗後の出力枠解放を検証する。
- [x] 連打・通信失敗・切店／切月／ページ／タブ往復と遅延応答、遅延ダウンロード、領域内再試行、出力権限喪失を実ブラウザで検証する。既存の未解決書込み回復をタブ変更が破棄しない。
- [x] 両テーマ、長い媒体名、狭幅、キーボードとフォーカス、人数の限定表示を実ブラウザ確認し、画像を保存する。
- [ ] `task lint` / `task test` / `task build` / 全量 `task e2e` をexit codeで判定し、DB試験の実行・条件skipを区別する。最新版SHAでローカルレビュー、CI、公式Codexレビューを確認し、所有者へマージ判断を委ねる。

## 後続の注文分母調査

| 論点 | 既存事実 | 広告費分母としての不足 |
| --- | --- | --- |
| 完了／無効／取消 | #388の有効完了件数はCOMPLETEDかつ非無効。取消・未完了は除外、無効数は別記。`OperationalReportReader`と`OperationalReport.Accumulator.order`に実装 | 広告の「総注文」もこの有効完了件数に合わせるかは未承認。予約・申請件数ではない |
| 無料・割引注文 | 既存件数集計は正の請求額を条件にせず、有効な零円完了も一件。ポイントや割引で零になっても件数は減らない | 広告指標でも零円を含めるか要決定。金額閾値を無断追加しない |
| 時間帰属 | `business_date` は原営業日。完了時刻や照会時刻の月ではない。訂正・無効化は原期の再照会へ反映 | 媒体獲得日・問い合わせ月・受注月のどれを分母にするか要承認。遅れて成約した注文の広告費月への紐付けは存在しない |
| 単位 | 既存件数はorder ID数であり、paxや人数・売上額ではない。再提供は独立受注 | 指標名称・再提供扱いを定義する必要がある |
| 受注媒体 | `Order.mediaName`は任意の最大100文字、広告費側は必須200文字。共通媒体IDなし。受付経路PHONE/MEMBER_WEB/GUEST_WEBは別概念 | 空媒体・表記差・訂正・複数媒体寄与・媒体費用だけ／受注だけの場合の対応が未定義。完全一致の費用グループ規則を注文対応へ自動拡張しない |
| 既存公開境界 | `OperationalFacts.Order`は原営業日・状態・金額・在籍本人等を公開し、mediaName/customerIdは含めない | 注文媒体集計が必要になった時点で専用の最小事実境界を設計する |
| 新規顧客 | `CustomerSelectionRequest.NEW`はCRM行を作る入力。初回受注フラグ・確定初回キーはOrderにない。顧客未設定も正規に存在する | NEWを新客分母にしない。初回予約／有効完了／初有料のどれか、同日同時の順序、初回無効化後の再判定が未決 |
| 本人と店舗 | Customerは店舗別。Memberは全体だが非会員注文もあり、Member関連は期間と解除を持つ。顧客統合は注文のcustomerIdを存続行へ付け替える | 同店初回かグループ初回か、顧客なし・会員なし・統合・過去データ不足・移行をどう扱うか未決。最古の現存注文を無条件で初回としない |

推奨する後続確認順は、(1)総注文を既存の原営業日・有効完了件数へ合わせるか、(2)媒体対応と未対応の明示、(3)同店／全体・初回判定・不明を含む新客の定義。分母零なら単価を零にせず未算出とする設計も、その契約で承認する。本書では分母・単価・注文の新APIを実装しない。

## 対象外と根拠

#393 外部媒体取込、#396/#383 採用・在籍・留存の対応、跨領域の媒体マスター・別名辞書、平台の媒体集計、実支払、資金流水、税、利益・ROI、#387日次確認は対象外。今回の承認や完了を他issueの自動再開・closeに使わない。

- [#389 最新範囲](https://github.com/kanghouchao/Kizuna/issues/389)、[PR #1023](https://github.com/kanghouchao/Kizuna/pull/1023)、[PR #1024](https://github.com/kanghouchao/Kizuna/pull/1024)
- [費用記録契約](0389-advertising-cost-contract.md)、[広告費帳票接続契約](0388-advertising-report-contract.md)、[運営帳票契約](0388-operational-reports.md)、[CONTEXT.md](../../CONTEXT.md)
- `AdvertisingValues` / `AdvertisingRecords` / `AdvertisingService.snapshot` / `AdvertisingExportService` / `AdvertisingDomainTest` / `AdvertisingPostgresTest`
- `OperationalReportReader` / `OperationalFacts` / `OperationalReport` / `OperationalReportTest.correctionsAndInvalidationStayInOriginalMonthWithoutCountingTwice`
- `Order` / `CustomerSelectionRequest` / `OrderCreateRequest` / `OrderCorrectionRequest`。関連履歴: #956（顧客選択）、#947（無効化・再提供）、#991（有効完了集計）、#1023/#1024。

## 実行した検証

- `task lint`、`task test DOCKER_TAG=issue389-media`、`task build DOCKER_TAG=e2e-kizuna-issue-389-media-summary`、全量 `task e2e` はすべてexit 0。
- フロントエンドは186 suites・1,747 tests成功。バックエンドは1,934件実行成功・132件条件skip。広告費関連33件は別途実PostgreSQL付きで実行し、全件成功・skip 0（PostgreSQL試験11件を含む）。
- 元行上限のDB試験は設定を1件に下げ、2件目で503になる境界を実測した。既定20,000件を20,001件投入する性能試験は実施していない。
- 全量E2Eは101件成功（12.9分）。広告費4場面は編集・削除・コピー・履歴、2,001行の全件出力、結果不明の書込み復旧、媒体集計・未入力・権限・表示切替を含む。
- [ライト](../screenshots/0389-media-summary-light.png)、[ダーク](../screenshots/0389-media-summary-dark.png)、[狭幅と長い媒体名](../screenshots/0389-media-summary-narrow.png)を実ブラウザで確認した。
- ローカルの規約・仕様レビューは再確認後に未解決指摘0件。CIと最新SHAの公式Codexレビュー結果はPRへ記録し、所有者のマージ判断を待つ。
