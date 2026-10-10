# 0389 営業広告の有効完了受注あたり記録費用

## 実施根拠と今回の切片

2026-10-09 23:24:43 UTCの利用者指示「继续做接下来的任务，按照最佳实践和行业标准来，自己制定计划直接执行即可」（`Sentinel_49df32f22c0c8191bffb8e321c79ed80`）に基づき、通常のAPI・実装設計は本書に明記して実施する。業界共通の獲得原価や因果帰属を定義したとの意味ではない。

基点はmaster `c3bbe6f1167e44b93cb0dbae8d1a1d52ab42abe0`。PR #1023〜#1025の費用記録・運営帳票・手入力人数集計を維持し、店舗広告費画面に「受注あたり記録広告費」を追加する。新しい媒体マスター、注文変更、DB列、実アカウント権限付与を作らない。

## 既存根拠と業務選択

- `order.reporting.OperationalReportReader`、`reporting.domain.OperationalReport.Accumulator.order`、#388／PR #991は原 `business_date` のCOMPLETEDかつ非無効の注文を一件ずつ数える。正の請求額を条件にしていない。この既存定義を採用し、無料・割引・ポイント等で請求0円になった有効注文も一件とする。人数paxではなくOrderの件数。取消・未完了・誤完了の無効化は除外、再提供は別の有効注文として数える。
- 広告費は同店・自然月の非削除SALESのみ。RECRUITMENTは営業注文の費用へ混ぜない。営業広告費の登録行がないことは実費0の証明ではない。
- 媒体は保存文字列の完全一致。広告費側は保存時にstrip、注文側は任意100文字で保存値を保持する。集計時のtrim、case fold、全半角変換、別名・Unicode正規化を追加しない。先頭末尾の空白差も別媒体とする。
- 注文のnull／空文字／Java `String.isBlank()`の媒体は「媒体未入力の有効完了受注」として件数だけ別掲する。架空の媒体名を作らず、どの費用にも割り当てない。その他の保存媒体名は不透明な記録名として扱う。
- 媒体行は営業費用の媒体と媒体入力済み有効注文の媒体の和集合。費用だけ／注文だけを落とさない。媒体名のJava `String.compareTo`昇順で一意に並べる。営業費用訂正・削除、注文無効化は次回読取りで原月へ反映する。完了後訂正で媒体・原営業日を変更する経路は追加しない。
- 両側の記録がある行だけ、登録営業広告費 ÷ 有効完了注文数を算出する。費用未登録または分母0はnull。登録0円かつ注文ありは既知0.00だが実費0を証明しない。小数2桁、HALF_UPで丸め、元の整数分子・分母を併記する。これは同月同名の記録比較であり、当該広告が注文を生んだ因果、初回獲得費用、利益、ROIではない。
- 月全体の件数・費用額・零円注文数・媒体未入力件数を示すが、未対応を混ぜた月全体の単価は出さない。手入力問い合わせ人数はこの分母に使わない。

## HTTP契約

| Method / Path | 要求 | 成功 | 権限 |
| --- | --- | --- | --- |
| GET `/store/advertising-order-costs` | month、page、size | 200 下記応答 | ADVERTISING_COST_VIEW ＋ ORDER_MANAGE |
| GET `/store/advertising-order-costs/exports` | month、format | 200 全件CSV／XLSX | 上記 ＋ ADVERTISING_COST_EXPORT |

注文を読める既存のORDER_MANAGEを追加要求し、広告費VIEWだけに注文件数を公開しない。OPERATIONAL_REPORT_*や新権限は導入しない。既存の費用・人数集計の権限は変えない。認証、storeBridge、`X-Role: store`、`X-Store-ID`、授権店舗集合とSTORE_VIEWによる店舗選択の条件を維持する。実ロールへ自動授与しない。

- monthは必須string `YYYY-MM`、年0001..9999。pageは非負整数（既定0）、sizeは正整数（既定20、100超は100）。非整数・Int上限超過・不正年月は400。offsetはlong、最終ページ超過は空content。
- formatは必須 `csv|xlsx`。出力は全件でpage/sizeを受理しない。両APIで未知・重複queryを400にする。sort/filterは追加しない。
- 401未認証・失効、403権限／店舗文脈／授権不足、400入力不正、503資源・精度・時間の上限または出力中、500固定内部エラー。未登録月は200の空。GETは書込みや冪等キーを持たず安全に再試行できるが、その間の訂正は反映され得る。通常404/409はない。
- エラーは既存のerror:stringと任意details:object。全応答 `Cache-Control: no-store`。

### 応答の型

全キーを返す。nullable明記以外は非null。整数はJSON number、0..9,007,199,254,740,991の安全整数（store_idは正）。

| フィールド | 型・内容 |
| --- | --- |
| store_id | 正整数 |
| month | string、要求の自然月 |
| month_version | 整数、広告費月版のみ。注文全体の版ではない |
| generated_at | offset付きISO8601日時string |
| basis | `recorded-sales-cost-per-valid-order-v1` |
| media_matching | `EXACT_STORED_NAME` |
| order_basis | `VALID_COMPLETED_ORIGINAL_BUSINESS_DATE_INCLUDING_ZERO` |
| rounding | `HALF_UP_2_DECIMAL_YEN` |
| cost_entry_count | 月の有効営業費用行数 |
| recorded_sales_amount | 整数またはnull。費用行0ならnull、登録零は0 |
| valid_completed_order_count | 月の全有効完了注文数、媒体未入力も含む |
| zero_amount_order_count | 上記のうち請求額0の注文数（内数） |
| unnamed_media_order_count | 上記のうち媒体未入力の注文数（内数） |
| rows | Spring Page<OrderCostSummary>、全集計媒体数とページ情報 |

行の全キー:

| フィールド | 型・意味 |
| --- | --- |
| media_name | 非空string、保存値（費用側最大200文字） |
| cost_entry_count | 非負整数、媒体の営業費用行数 |
| recorded_sales_amount | 非負整数またはnull（費用未登録） |
| valid_completed_order_count | 非負整数、有効完了注文数 |
| zero_amount_order_count | 非負整数、そのうち請求0円の数 |
| cost_per_order | 非負10進string、固定小数2桁、またはnull。二進浮動小数への暗黙丸めを避ける |
| calculation_status | `CALCULATED` / `NO_COST_RECORDS` / `NO_VALID_ORDERS` |

両側とも存在しない媒体行は生成しない。費用あり・注文0はNO_VALID_ORDERS、注文あり・費用0行はNO_COST_RECORDS。空月の金額はnull、件数0、rows.content=[]。費用が採用分だけの場合も営業費用は未登録。新客件数・新客単価は本APIのフィールドに追加しない。

## 読取り・資源・境界

advertisingのユースケースが `@StoreScoped` とREPEATABLE_READ取引を所有する。その同一取引内で広告費の既存snapshotと、orderのNamedInterface `reporting`の専用最小射影（保存媒体名・請求0円か）を読む。order側もStoreScopedとMANDATORY取引・ORDER_MANAGEを要求し、手書きstore条件だけに依存しない。人物ID・顧客ID・電話・理由・報酬・注文本文を外部へ渡さない。

既存 `app.advertising-cost` の予算を再利用する。元広告費行（採用分も上限判定に含む）と有効注文をそれぞれ既定20,000件、設定可能1..100,000件まで。各読み取りは上限+1で検査し、過剰データを途中成功にしない。DB10秒、全体25秒、文字量4,000,000、出力16MiB。媒体群は両元集合の和以下。全件の文字量・合算・時間を検査し、出力は既存費用・人数集計と同じ一プロセス一枠を共有する。成功・例外後に枠とXLSX一時ファイルを解放する。

合計はchecked long＋JS安全整数。比率はBigDecimal整数除算をHALF_UP・scale2で行い文字列化する。XLSX整数セルは既存15桁上限を維持し、比率は小数文字列セルで保持する。別ページ・別ダウンロードを跨ぐ保存スナップショットは作らない。

## 出力互換

新出力は固定18列:

`record_type,generated_at,basis,media_matching,order_basis,rounding,store_id,month,month_version,media_name,cost_entry_count,recorded_sales_amount,valid_completed_order_count,zero_amount_order_count,unnamed_media_order_count,cost_per_order,calculation_status,interpretation`

- criteria行は共通条件と月の費用・注文件数・未入力件数、媒体・比率・状態は空欄。末尾interpretation列に記録比較で因果・利益・ROIを示さないこと、零円受注を含むことと丸めの日本語説明を保持する。
- media行は共通条件と各媒体行、unnamed_media_order_countとinterpretationは空欄。nullは空欄、既知0は0、比率は0.00等の文字列。全ページの媒体を同一順序で出す。
- CSVはUTF-8 BOM、引用と数式開始文字保護。XLSXは「条件と総計」「受注あたり記録広告費」の2シート、同じ18列・型と意味を保持する。
- attachmentファイル名は `advertising-order-costs-<store_id>-<YYYY-MM>.csv|xlsx`、CSV `text/csv;charset=UTF-8`、XLSX既存標準MIME。
- 費用明細17列、手入力媒体人数集計16列、#388の全契約は変更しない。

## UI・受け入れ

既存広告費ページの店舗／月を共有し、ORDER_MANAGEを持つ場合のみ第三タブ「受注あたり記録広告費」を表示する。費用／人数集計は従来権限で維持する。表は媒体・登録営業広告費・有効完了注文（うち0円）・一件あたり記録費用・未算出理由。金額未知を0円にしない。媒体名の空白を表示で保持し、前後に空白がある名前にはその旨を表示する。小数2桁の概算であること、零円注文を含むこと、記録比較であり獲得因果ではないことを画面と出力条件で明示する。新客単価は未提供と理由を短く説明する。

- [x] 同店同月同名の一致と不一致、空白・大小・全半角、採用除外、媒体未入力、注文だけ／費用だけ、空月を検証する。
- [x] 有効完了・零円・取消・未完了・無効化・再提供、原営業日／別月、費用訂正／削除を実DBで確認する。
- [x] 並行して費用や注文が更新されても一応答の分子・分母・月版が同じ読み取り時点になる。
- [x] null／0.00、100/3の33.33等の丸め、安全整数・XLSX15桁、2,001以上の媒体、全件出力、元行・文字・時間・byte上限と失敗回復を検証する。
- [x] 直接APIでAD_VIEW・ORDER_MANAGE・AD_EXPORTの独立性、他店、失効・誤コンソール、未知／重複queryを検証する。
- [x] useListPageと応答metadataを使用し、失敗時の旧値破棄、領域内再試行、切店／切月／タブ／ページ往復と遅延応答、出力連打／遅延／権限を実ブラウザ検証する。既存書込み回復を壊さない。
- [x] 明暗・狭幅・長名・キーボードを実ブラウザ確認し画像保存。Taskfile lint/test/build/全量E2Eと規約／仕様レビューを記録する。CIと最新SHA公式Codexレビューの結果はPRに記録する。

## 後続として残すもの

新客の判定は顧客NEW入力と同義ではない。顧客未設定、店舗別Customer、任意Member関連・事後帰属、同店顧客統合、過去データ不足、初回注文無効化がある。現存注文の最小日や会員の初回有料特典を全体の初回と代用しない。次批で同店の記録範囲と不明状態を定義できるか検討するが、本批は新客指標を提供しない。

外部自動収集#393、採用・留存#396/#383、共通媒体識別・別名対応、平台の媒体指標、税・資金移動・利益・ROIは対象外。#389全体をcloseしない。今回の完全一致は分析上の記録比較だけで、横断媒体マスターの完成とはしない。

## 検証記録

- Taskfileの最終lint・test・buildはexit 0。frontend 187 suite／1,752 test成功。backendは2,074 test中1,940成功・134条件skip、failure／error 0。
- PostgreSQL 18の専用tmpfs DBへ接続した広告費テストは41成功・skip 0（実庫13を含む）。Dockerの接続条件付きskipとは区別する。
- 顧客統合の既存 `CustomerMergeConfirmDialog` テストで一度タイミング依存の失敗があった。該当14テストの単独実行と全量Taskfile再実行がexit 0。該当機能のコードは変更していない。
- ローカルcode-reviewはStandards／Specとも残存0。出力の解釈説明、出力種別の共通定義、空白名の視認性とhover時の文字色を修正して再確認した。
- 実ブラウザで本機能のシナリオ成功。切店のブラウザ確認はページ遷移で行い、同一コンポーネントの世代切替はunit testでも確認した。明暗・760px幅・長名・キーボード画像を `docs/screenshots/0389-order-cost-*.png` に保存。
- 広告費E2Eの店舗IDはログイン後のURLから取得し、fixture・注文操作・ナビゲーション・ファイル名へ渡す。共有helperの省略時既定値は既存呼出しとの互換を維持する。
- `task e2e` は102シナリオ成功・skip／retry 0、exit 0。CIと最新SHA公式Codexレビューの確定結果はPRで記録する。
