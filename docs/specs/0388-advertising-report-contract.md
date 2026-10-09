# 0388 広告費の運営帳票接続：承認済み API 契約

## 状態と確認した差分

2026-10-09、利用者は「対象月で集計、日割りなし、受注・報酬・広告費を分列、不完全月は不適用」という業務方向を承認した。2026-10-09 13:45:08 UTCに利用者が具体的なAPI案へ「可以」と回答し、以下の契約による実装を承認した（`Sentinel_77aee48dc94c81919626f9a1b7c9bfe4`）。

基点は fetch 後の master / origin/master `71fbf15a6ebd2e8efb9a6bb985a9076c9df169a6`。#389 の最新本文・関連コメント、PR #1023、現在の advertising / reporting 実装・テスト・履歴を確認した。
広告費の店舗月次記録・訂正・留痕削除・前月コピー・独立CSV/XLSXは実装済み。報酬帳票接続も PR #1022 で実装済みであり、どちらも作り直さない。
未実装なのは advertising の最小公開事実境界と、既存運営帳票への費用接続である。

本切片は統計と出力だけを扱う。媒体効果、問い合わせ人数の集計、媒体名の同一視、ROI、利益、実際の支払、税、月次ロックを追加しない。他issueを開始しない。

## HTTP と要求

既存の四経路を拡張する。

| メソッド・経路 | 成功 |
| --- | --- |
| GET `/store/operational-reports` | 200、既存の報告JSON＋任意の広告費オブジェクト |
| GET `/platform/operational-reports` | 同上 |
| GET `/store/operational-reports/exports` | 200、CSV/XLSXのattachment |
| GET `/platform/operational-reports/exports` | 同上 |

新規queryは `include_advertising:boolean`、任意・既定false。`include_remuneration:boolean` は任意・既定falseのまま独立に選択する。

- `from` / `to`: 必須の `YYYY-MM-DD`、両端を含む、年0001..9999、開始≦終了、最大366日。
- `group_by`: `day`（既定）/ `month` / `store`。新しいyear値は追加しない。年次は01-01〜12-31の総計と月別／店舗別行で扱う。閏年も366日として受け付ける。
- 平台の `store_id`: 任意の正整数number。省略時は授権店舗集合。店舗側は既存の `X-Role: store` / `X-Store-ID` とstoreBridgeを使う。
- 閲覧の `page`: 既定0、非負整数。`size`: 既定20、1..100。Spring Pageを維持し、店舗ID・期間の全順序。上限超過やoffsetの不正は400。
- 出力の `format`: `csv` / `xlsx`、必須。page/sizeに依存せず全件を出力する。

既定falseでは既存JSON・v1/v2のCSV/XLSX・計算根拠を変更しない。trueでも既存の金額フィールドは意味を変えず、広告費を報酬合計から控除しない。

## 整月判定と不適用

広告費を適用する条件は **fromが月初、toが月末、group_byがmonthまたはstore**。複数の連続した自然月を許し、費用は保存済みmonthで選ぶ。

| 条件 | 広告費のstatus | 数値 |
| --- | --- | --- |
| 開始が月初でない、または終了が月末でない | `NOT_APPLICABLE_PARTIAL_MONTH` | 件数・金額とも必須null |
| 整月だがgroup_by=day | `NOT_APPLICABLE_DAY_GROUPING` | 件数・金額とも必須null |
| 整月かつmonth/store、有効な登録行あり | `RECORDED` | 登録済み行だけの件数・金額 |
| 整月かつmonth/store、有効な登録行なし | `NO_RECORDS` | 件数0・登録額0 |

両方の不適用条件を満たす場合はPARTIAL_MONTHを優先する。不適用は200の報告状態で、受注・選択済み報酬は指定日付のまま返す。期間内に完全な月が一部あっても、広告費だけその月を抜き出して総計へ混在させない。日別では総計も各日行も不適用とし、月費を月初日や各日へ置かない。不適用時は広告費事実を取得せず、費用由来の集計行・出力根拠行も作らない。

例: 2026-01-01〜03-31 / monthは適用、01-15〜03-31は期間全体で不適用、02-01〜02-28 / dayも不適用。2028-02-01〜02-29 / monthは適用する。

NO_RECORDSの零は既存#389と同じ「現在登録されている有効行の合計」。実費零、入力完了、全費用の把握済みを意味しない。画面とファイルにこの注記を載せる。削除後も有効行がなければNO_RECORDSとなり、削除履歴そのものは正本に残す。

## 応答

include_advertising=trueのときだけ、トップレベルと各 `rows.content` 要素に `advertising` を追加する。falseではキーを省略する。オブジェクト内の全キーは必須で、nullableはグローバルNON_NULL設定に関係なく明示nullを返す。

| キー | JSON型 | 意味 |
| --- | --- | --- |
| status | 上記4値のstring、非null | 対象期間・集計単位の適用可否と登録有無 |
| entry_count | 非負整数numberまたはnull | 未削除の費用行数。問い合わせ人数ではない |
| sales_amount | 非負整数numberまたはnull | SALESの登録額 |
| recruitment_amount | 非負整数numberまたはnull | RECRUITMENTの登録額 |
| recorded_total_amount | 非負整数numberまたはnull | 上記二区分の登録額合計 |

件数・金額はJava longで集計し、JSONはJavaScript安全整数内に限定する。合計だけ別の丸めや通貨換算を行わない。問い合わせ人数は参照境界にも帳票DTOにも含めず、既存広告費管理でのnullと0の意味を保持する。

month行は店舗×対象月、store行は店舗×要求期間。受注も報酬もない広告費だけの店舗月も行に含める。広告費がない既存行はNO_RECORDSとする。全情報源が空の店舗月の行を新たに埋めず、既存stores一覧は無注文店も含めて保持する。既存の報酬未知額・本人向けDTOは変更しない。

basisは次の四通りとする。

| include_remuneration | include_advertising | basis |
| --- | --- | --- |
| false | false | `completed-orders-current-v1` |
| true | false | `completed-orders-remuneration-current-v2` |
| false | true | `completed-orders-advertising-current-v3` |
| true | true | `completed-orders-remuneration-advertising-current-v3` |

## 権限（承認済み）

既存の店舗ORDER_MANAGE／平台ORDER_SET_MANAGE、OPERATIONAL_REPORT_VIEW、出力時のOPERATIONAL_REPORT_EXPORTを維持する。報酬選択時のREMUNERATION_VIEWも維持する。

広告費を選択する場合の追加要件:

| 面 | 閲覧 | 出力 |
| --- | --- | --- |
| 店舗 | 既存 `ADVERTISING_COST_VIEW` | 既存VIEW＋`ADVERTISING_COST_EXPORT` |
| 平台 | 新規 `ADVERTISING_COST_SET_VIEW` | 新規SET_VIEW＋`ADVERTISING_COST_SET_EXPORT` |

新規の二権限はConsole.PLATFORM、既定付与なし。既存三権限はConsole.STOREのまま変更しない。これにより店側の広告費だけの利用者のstoreBridgeを壊さず、平台に店舗操作資格を追加しない。平台の費用変更・コピー・履歴操作は追加しない。
不適用期間でもinclude_advertising=trueには同じ追加権限を要求する。閲覧のみの利用者が広告費込み出力を要求した場合は403で全体拒否し、黙って費用を落とさない。falseの出力は既存権限のまま可能。
HTTPと公開事実読取境界の双方で面ごとの権限を検証し、既存の単店／集合Hibernate filterと明示的授権店舗検証を併用する。未授権店を事実読取に渡さない。実ロール・実アカウントへの権限付与は行わない。

## 正本・スナップショット・資源

`advertising::reporting` の最小事実射影を追加する。公開値はcost_id:string、store_id:number、month:YYYY-MM、version:number、category:SALES|RECRUITMENT、amount:非負円整数だけ。内部理由・操作者・履歴本文・媒体名・会社・プラン・問い合わせ人数を取得しない。
既存ReportSnapshotの同一REPEATABLE_READ取引内で、受注・選択済み報酬事実・未削除広告費を読む。別のHTTP呼び出しや独立取引の集計値を合流させない。
再照会・再出力では原月の訂正・削除を反映し、過去ファイルの内容を業務上の凍結値として扱わない。広告費管理の月版・変更履歴を複製しない。

広告費全件はmaxOrders（既定20,000、設定上限100,000）件を上限取得＋1で検証する。既存の受注件数、報酬入力総件数・生成人日、店舗件数の個別上限も維持する。追加取得は既存の共通DB10秒・生成25秒、400万文字・16MiB・プロセス内生成1件の予算内で行い、情報源ごとに時間・文字・出力サイズ予算をリセットしない。生成する全根拠と集計行に同じ予算を適用する。上限による末尾の切捨ては成功にせず503で全体失敗とする。

## CSV / XLSX

include_advertising=falseではv1の16列・3シート、v2の33列・5シートを保持する。
trueでは選択済みのv1/v2列の順を保持し、末尾に次の10列を追加する: 広告費状態、広告費登録件数、営業広告登録額（円）、採用広告登録額（円）、広告登録額合計（円）、広告費ID、広告費版、広告費対象月、広告費区分、広告費額（円）。広告費だけなら26列、報酬との併用は43列。

CSVには `advertising_cost` 行を追加し、XLSXには「広告費根拠」を1シート追加する（4または6シート）。根拠は未削除全行を店舗ID・対象月・費用IDの全順序で出し、店舗・対象月・費用ID・版・区分・金額を保持する。氏名・内部理由・操作者・問い合わせ人数を出力しない。
総計と集計行の費用欄にはstatusと登録額を入れ、不適用額は空欄、NO_RECORDSは0と状態で区別する。無関係な行種別の列は空欄。総計へ根拠額を再加算しない。不適用でも根拠シートはヘッダーだけ作り、条件と総計に不適用理由を明記する。

既存のファイル名、認証付きattachment、Cache-Control:no-store、UTF-8 BOM・CRLF・全セル引用・文字列の単引用符保護、XLSXの数値／文字／真偽型を保持する。CSVは十進整数、XLSXは15桁上限999,999,999,999,999を維持し、超過・異常値を丸めず全体503。失敗時の部分ファイル・attachmentを返さず、一時ファイルを破棄する。

## エラーと画面

200成功。401未認証、403機能／面／店舗集合不足、400入力不正、404指定店舗不存在、503件数・時間・容量・同時実行・安全整数上限超過。既存error文字列＋任意detailsを用いる。読み取り専用なので409や書込み冪等キーを追加しない。不完全月・日別集計はエラーではなく上記の不適用状態。

閲覧権限を持つ利用者に独立した「広告費を含める」を既定OFFで示す。適用済み条件を照会・出力で共有し、切店・切日付・集計単位変更・両オプションの変更後に旧応答／旧ファイルを採用しない。登録額の三列と状態を示し、未登録は「登録なし」、不適用は「対象外」と具体的な理由を示す。日割りや利益・支払の表示を追加しない。

## 承認後の検証計画

- 両面の権限組合せ、storeBridge維持、授権店だけの集合、直接API・出力の拒否、実ロールの付与なし。
- 整月／不完全月／日別集計、優先状態、閏月・跨年・366日、費用だけの店舗月、0円行と登録なし、削除後と原月訂正。
- 受注・出勤・保証・賞与・広告費の同一スナップショットを並行更新下で実DB検証。
- 四オプション組合せのJSON・CSV・XLSX、必須null、数式保護、2,001件以上の全件、金額精度と共通予算、失敗後の資源解放。
- UIの切店・期間・分組・選択変更、再照会／出力連打、過期応答、テーマ・狭幅・キーボード。
- 最終task lint / test / build / e2e、ローカルレビュー、draft PR、CI成功後の公式Codex最新headレビュー。マージは親タスクの厳密なSHA確認後。実行成功・skip・未実行を分けて記録する。

#388はこの切片の受け入れ後に残要件を再照合してから完了を提案する。#389の媒体効果など未実装項目はOPENのまま残す。

## 実装・検証記録

advertising::reporting の有効費用射影を既存ReportSnapshotへ接続し、承認済みの四状態・二つの選択・単店／集合権限を実装した。新規JPQLは実体の完全修飾名を使い、E2Eの汎用要求はstore-apiに置く。

- Docker `task lint` は最終実行でexit0。レビュー時の引数名変更による初回の整形違反は修正済み。
- Docker `task test DOCKER_TAG=issue-388-advertising-reports` はexit0。frontend185 suites／1,741 tests成功、backend250 suites／2,058登録・131条件付きskip・1,927実行成功。backendテストイメージのsrc全件と現在のsrcが一致することも確認した。
- 対象を限定した実スタックE2Eは1件成功、17.9秒、exit0、再試行なし。五種類の事実源の同一スナップショット、原月訂正／削除、権限、2,001行と20,001行超過、精度、古い応答を実PostgreSQL／ブラウザで検証した。条件付きDB単体試験のskipを成功には含めない。
- JDK25の対象50 tests、前端の対象34 tests、Repo Lintはいずれもexit0。
- ローカルcode-reviewはStandards／Specとも未解決0件。

最終Docker `task build` と `task e2e` はexit0。全100シナリオ成功、13.2分、再試行なし。最終コミット、CIと公式Codexレビューの結果はPRに記録する。
