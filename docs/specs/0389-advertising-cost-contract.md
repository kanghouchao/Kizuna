# 0389 広告費管理：第一切片と API 契約

## 承認と範囲

2026-10-09 11:59:52 UTC、利用者が主タスクの集中確認に「恩，按照这个方案继续」と回答し、コピー規則と以下の完全なAPI契約を承認した（`Sentinel_a1537c7e4f5c8191a99d5e2c75560a10`）。基点はmaster `f50a47d1dadd620a1e7fdd0a88bebd9b490e0f7a`。

件数は問い合わせ人数、区分は営業広告／採用広告、第一切片は店舗側のみ。月次ロックを設けず、随時変更と履歴保持を行う。同店前月から空の月へ区分・媒体・会社・プラン・金額をコピーし、問い合わせ人数をnullへ戻す。対象月に有効行があれば拒否する。閲覧・管理・出力の権限を分離し、実ロールへ自動授与しない。

原文の「招牌广告」は従来の「招聘广告」の文脈として主タスクが解釈して返信済み。平台操作、#388への金額接続、効果指標、実支払、外部媒体取込は今回の承認範囲に含めない。

## 事実・差分・依存

| 論点 | 確認した現在の事実 | 第一切片への影響 |
| --- | --- | --- |
| 広告費の正本 | #389 は OPEN。費用の entity / API / UI は master にない | 新規領域として必要 |
| 月次ロック | #389旧本文にはロック要件が残るが、利用者が2026-10-09 11:55:23 UTCにロックなし・随時変更・履歴保持を承認 | この明示決定で旧要件を置き換える。#386からの推論ではない。issue本文は本作業では未更新 |
| 五操作権限 | #382 の8/31裁定で広告費の独立能力は金流域の実需時に再設計へ変更 | 古い五権限をそのまま復活させない。ロール×授権店舗集合を使う |
| 帳票 | #388 / PR #991・#1022 は請求・固定報酬・保証補差・ボーナスを実装済み | 費用正本・月帰属・権限を定めてから接続。第一切片では既存帳票を変更しない |
| 月帰属と日別帳票 | 費用は対象月。既存帳票は任意の from/to と day/month/store を受ける | 月費を日割りしたり月初日へ仮置きしたりしない |
| 受注媒体 | Order.mediaName は任意の自由文字列（上限100）、ReceptionRoute は PHONE / MEMBER_WEB / GUEST_WEB | 受付経路と広告媒体は別。媒体 ID の共通マスターはない |
| 新規受注 | Order に初回／新規の保存済み判定はない。CustomerSelection の NEW は顧客作成入力 | NEW 入力、会員登録、媒体名、最古の現存受注を無条件に新規受注の分母にしない |
| 問い合わせ人数 | 利用者が件数の意味を回答済み。入力元となる統一イベント／個人重複排除規則はない | 第一切片は手入力人数を記録する。受注数や自動計測値に置き換えず、複数プランの人数を無条件に合算しない |
| 採用効果 | #396 は応募受付と添付まで実装。採否・在籍／招待・入店日・媒体照合・留存供給は未実装 | 「候補5未起票」は旧情報。依存は #396、在籍履歴は #383 |
| 外部媒体実績 | #393 は OPEN。実績の回流を定義する供給側 | 外部媒体自動取込を先行実装しない |
| 監査 | audit::recording の AuditWriter は既存業務取引への参加を要求する | 費用更新・履歴・監査・冪等応答を原子的に保存可能 |

原典の current-business 文書は現行ツリーから削除済みである。
削除前 `a9a7a4dbf1b2059bebe913108fddca1196cf2fe3` のフロー14は月ロックを明記し、分類表には掲載期間・担当者もある。
当初の調査では件数の単位・区分が未確定だったが、上記の利用者回答で解消した。コピーの対象項目も上記の承認で確定した。媒体の同一視規則は後続の指標契約で定める。
本書は古い文書を現行の裁定として復活させず、未確定事項を明記する。
リポジトリに `.agents/skills` は存在しなかった。AGENTS.md と現在の実装・テスト・履歴を優先した。

## 推奨する順序と第一切片

1. **店舗×対象月の費用管理を端から端まで提供する。** 一覧・登録・編集・誤登録削除・前月コピー・変更履歴・月の登録額集計・全件 CSV/XLSX を同じ契約で実装する。第一切片は費用行の保存と追跡だけで受け入れ可能とする。
2. 費用の帰属と修正規則の承認後、#388 の月次／年次に登録済み広告費を接続する。第一切片と混在させず、追加契約で承認する。
3. 媒体対応・反響・総受注／新規受注の定義が揃った範囲から一期効果指標を実装する。供給がない指標は空の数式や仮の零で完成扱いにしない。
4. 採用後2〜4か月の効果と外部自動取込は #396 / #393 の供給契約待ち。これらの issue を自動で再開しない。

第一切片は承認済みの「随時編集＋履歴保持」に従う。理由必須・履歴の具体的な項目は以下の承認済み契約に従う。月次確定・解除・再確定の状態や専用権限は導入しない。

## 承認済みの入力・コピー規則

人数は非負整数、未計測はnull、測定済み0人は0。金額はその店舗・対象月に帰属する円整数の費用額で、0円を許可し空欄・負数・小数を拒否する。コピーは全件を原子的に処理し、既存行への追加マージ・上書きは行わない。確認画面で金額継承と人数クリアを明示する。

効果指標の自動供給・重複排除・分母・媒体同一視は後続契約で定める。手入力人数の保存だけで重複排除済みの実人数集計が完成したとしない。

## 第一切片のデータ

新しい advertising モジュールが金額正本・版・履歴を所有する。媒体は入力時の記録名として保持し、Order.mediaName / Applicant.sourceMedia と自動結合しない。
同じ媒体・会社・プランの複数行を許す。名称の一致を重複と決めない。誤送信の重複排除は request_id で行う。

### フィールド

以下は JSON の型。非負整数は有限整数で、小数や文字列を受け付けない。IDは費用・変更が不透明なstring、店舗と操作者は既存型の正整数number。全レスポンスの金額合計は JavaScript 安全整数内に限定する。

| フィールド | 型・null・省略 | 意味／制約 |
| --- | --- | --- |
| id | string、応答必須・非null | 費用ID |
| store_id | 正整数number、応答必須・非null | ヘッダーで確立した店舗。要求本文には持たない |
| month | string、必須・非null | `YYYY-MM`、年0001..9999。登録後は固定。誤月は削除＋正しい月へ登録 |
| category | `SALES` / `RECRUITMENT`、必須・非null | 営業広告費／採用広告費。二分類は利用者回答済み |
| media_name | string、必須・非null、trim後1..200文字 | 入力された媒体名。共通ID・同一性を主張しない |
| agency_name | stringまたはnull | 広告会社、trim後最大200文字 |
| plan_name | stringまたはnull | プラン、trim後最大200文字 |
| inquiry_count | 整数numberまたはnull | 0..2,147,483,647人。手入力の問い合わせ人数。未知を0にしない |
| amount | 整数number、必須・非null | 0..2,147,483,647円。人数を掛ける単価ではなくその行の費用額 |
| version | 整数number、応答必須・非null | 0以上の楽観的排他版。更新・削除要求で必須 |
| created_at / updated_at | ISO 8601日時string、応答必須・非null | オフセット付き |
| request_id | UUID string、変更要求必須・非null | 同一操作の再送に再利用 |
| reason | string、更新・削除・コピー要求で必須・非null | trim後1..500文字。初回登録は不要 |

登録要求では agency_name / plan_name / inquiry_count は省略可（nullと同義）。空文字の任意名称はnullへ正規化する。
更新はPUTの全置換とし、この三項目もキー必須・null可。category / media_name / amount も必須。month / store_id / id は更新本文に持たず変更不可。未定義フィールドは既存の厳格なJSON処理に従い400。

`AdvertisingCostSummary` は id/store_id/month/category/media_name/agency_name/plan_name/inquiry_count/amount/version/updated_at のみ。
`AdvertisingCostResponse` はSummary＋created_at。nullableの三項目は応答でもキー必須・nullを明示する。
掲載期間・担当者は旧棚卸しにあるが今回の6項目切片には含めない。旧データの完全移行を主張しない。

## HTTP 契約（店舗側のみ・月次ロックなし）

全経路で認証、`X-Role: store`、`X-Store-ID`、授権店舗集合、storeBridgeを要求する。
読取りは @StoreScoped、変更は同一店舗文脈で所有権・版を検証する。異なる店舗の費用IDを指定しても返さない。

| メソッドと経路 | 入力 | 成功 | 権限 |
| --- | --- | --- | --- |
| GET `/store/advertising-costs` | month必須、page/size | 200、Spring PageのSummary | VIEW |
| GET `/store/advertising-costs/{id}` | id | 200、Response | VIEW |
| POST `/store/advertising-costs` | month/category/media_name/agency_name?/plan_name?/inquiry_count?/amount/request_id | 201、Response（idを含む） | VIEW＋MANAGE |
| PUT `/store/advertising-costs/{id}` | category/media_name/agency_name/plan_name/inquiry_count/amount/version/reason/request_id | 200、Response | VIEW＋MANAGE |
| DELETE `/store/advertising-costs/{id}` | JSON本文 version/reason/request_id | 204、本文なし | VIEW＋MANAGE |
| GET `/store/advertising-cost-months/{month}` | month | 200、月サマリー | VIEW |
| POST `/store/advertising-cost-months/{month}/copies` | source_version/target_version/reason/request_id | 201、コピー結果 | VIEW＋MANAGE |
| GET `/store/advertising-cost-months/{month}/changes` | cursor?/size | 200、CursorPageの変更Summary | VIEW |
| GET `/store/advertising-cost-months/{month}/changes/{changeId}` | month/changeId | 200、変更詳細 | VIEW |
| GET `/store/advertising-costs/exports` | month必須、format=csv\|xlsx必須 | 200、全件ファイル | VIEW＋EXPORT |

`exports` は永久予約語。GETで月レコードを作らない。DELETEのJSON本文は理由・版・冪等キーをURLへ出さないために使い、axiosではdataに指定する。配送経路をE2Eで検証する。

### 権限

第一切片の権限は ADVERTISING_COST_VIEW / ADVERTISING_COST_MANAGE / ADVERTISING_COST_EXPORT の三つ（いずれもConsole.STORE）。VIEWだけの店舗閲覧者も既存のstoreBridge判定で資格を取得できる。ORDER_MANAGEや給与権限を広告費閲覧の前提にしない。画面の店選択には既存STORE_VIEWの明示授与が必要で、広告費の権限を持つだけで未授権店舗を閲覧できない。

権限目録だけを追加し、既定ロール・実アカウントへの授与は一切行わない。検証では使い捨てのロールと店舗集合を明示設定する。手動削除はMANAGEで扱い、履歴を消去する経路を作らない。

平台側の広告費操作は第一切片に含めない。後続の#388集合帳票への接続時に平台の行使面と権限を別途承認する。HQへSTORE権限を自動付与して橋渡ししない。

### ページ・月サマリー・コピー

費用一覧は page既定0、size既定20・最大100。負pageと1未満sizeは400、100超は100へ制限。id昇順の全順序で、フィルタは当該monthだけ。合計はページとは別の月サマリーで返す。

月サマリーは store_id:number、month:string、version:number（未作成月は0）、entry_count:number、sales_amount:number、recruitment_amount:number、recorded_total_amount:number、generated_at:日時string。全て必須・非null。
金額は当該月の未削除行の登録額合計。未登録月のentry_count=0／合計0は「登録なし」であり実際の費用が無い証明や月の入力完了を表さない。問い合わせ人数は同一媒体の複数プラン間で重なる可能性があるため、第一切片では行の入力値だけを表示し、媒体横断の実人数合計を返さない。

月versionは行の作成・編集・削除・コピーで増分する。未登録月にも初回書込みを直列化する店舗＋月キーが必要で、GETの0から同時に書いた処理は一つだけ勝つ設計ではなく、通常の別行登録は順に成功可能とする。既存行更新は行versionで競合検出する。

コピー元は同店舗の直前の自然月だけ。対象0001-01は400。確認画面は元月の全行（ページで閲覧可）、費用行数、金額合計、コピー対象項目、問い合わせ人数を空に戻すことを示す。読取り中に月versionが動いた場合は確認をやり直す。
source_version / target_version は0以上の整数で必須。実行時に元月・対象月を一定順序でロックして再検証する。元月なし／有効行なしは400、対象有効行あり・版変化は409。全件コピーの途中成功はない。

コピー結果は id:string（操作ID）、store_id:number、source_month:string、target_month:string、copied_count:number、source_version:number、target_version:number（更新後）、created_at:日時string。全て必須・非null。各費用行の履歴にはsource_cost_idを保存する。結果から一覧を再読込みでき、巨大な裸配列を応答に載せない。

### 履歴

変更一覧はsize既定20、CursorPage.MAX_SIZE=2000を適用し、(occurred_at DESC,id DESC) と同じ組でcursor比較する。cursorは不透明string、初回省略、不正は400。応答は content と任意next_cursor（末尾で省略）。
変更Summaryは id:string、cost_id:string、action:CREATED|UPDATED|DELETED|COPIED、actor_id:number、occurred_at:日時string、version_before:number|null、version_after:number|null。nullableキーも必須。削除された行の変更も月履歴に残す。
変更詳細はSummary＋reason:string|null、source_cost_id:string|null、before:費用Response|null、after:費用Response|null。作成前・削除後はnull。コピーは元行IDを持つ新規作成として記録する。生の入力要求やトークンは保存しない。
詳細の変更レコードも指定店舗×月で照合し、IDだけで無関係な月へ飛べない。操作者の停止後もID参照を保つ。一般監査には対象・操作・版・金額等の限定値を渡し、自由記述の理由や媒体名の全文は費用履歴の正本だけに置く。
削除は通常一覧・合計から除くが、理由と削除前値を残す。復活操作は設けず、必要なら新規登録する。

### 成功・失敗・競合・冪等性

- 401 未認証、403 機能権限・店舗文脈・授権不足、404 存在しない／店舗内で不可視／削除済みの費用や存在しない変更詳細。
- 400 入力不正・不正月・形式不正・不正cursor・コピー元空。409 行版競合、コピー時の月版競合／対象非空、同キー別要求。500は既存の固定内部エラー文言。エラーは既存のerror文字列＋任意details（snake_caseのfield path）とする。
- 全変更は(store_id,actor_id,request_id)で直列化する。比較対象はメソッド・対象ID／月・正規化済み要求全文。キーは広告費の変更全体で共有し、別操作への使い回しも409。
- 同じキー・同じ要求の成功再送は、権限・店舗を再検証した後、元の成功コードと応答（DELETEは204）を再現する。古いversionの再判定より成功受領記録の照合を先に行う。成功後に別操作で更新・削除されても元の応答は回復できる。UIは回復後に現況を再取得する。
- 未成功要求は成功受領記録を残さない。同キー並行要求は一回だけ業務変更・履歴・監査を作る。別キー同一行の旧版は409。別キーの同一内容新規登録は合法な別費用として扱う。
- 費用、月version、変更履歴、一般監査、成功受領記録は同一DB取引。一つでも失敗すれば全体をロールバックする。受領記録はこの切片で自動期限削除しない。
- 送信前キャンセルは無書込み。送信後の閉じる／中断はサーバ取消の保証ではない。結果不明時は元のキーと送信内容を維持して同じ要求を再送し、新しいキーを自動生成しない。結果不明の間は内容変更・別送信を止め、回復導線を残す。
- 未解決要求は本人×店舗×月×操作でブラウザの当該タブのセッションに保持し、ダイアログ再開・画面再読込みでも元の内容で回復できる。成功後またはログアウトで破棄する。成功受領照合後の確定業務拒否（400/404/409/422）も未成功が確定したため解除し、401/403や通信障害では元要求を維持する。別本人・別店舗に再利用しない。
- 店舗／月／対象を変えた後の旧読取り・旧保存応答・旧ダウンロードは新画面へ反映しない。結果不明要求は元文脈の回復対象として保持する。

### 独立出力と既存帳票互換

第一切片のexportは選択店舗×月の未削除費用全件と総計。ページ指定を受けず、一回のREPEATABLE_READスナップショットで総計と全明細を生成する。
CSVは条件／総計／費用のrecord_type、XLSXは「条件と総計」「広告費明細」の2シートとする。

出力列は record_type, generated_at, basis, store_id, month, entry_count, sales_amount, recruitment_amount, recorded_total_amount, cost_id, version, category, media_name, agency_name, plan_name, inquiry_count, amount の17列。
basisは `advertising-cost-current-v1`。費用行には条件列と費用列、条件／総計行には条件列と集計列を入れ、非該当列を空欄にする。inquiry_countのnullは空欄、0は数値0。履歴本文・理由・操作者・顧客／応募者個人情報は出力しない。
ファイル名は `advertising-costs-<store_id>-<YYYY-MM>.csv|xlsx`。認証付きattachment、Cache-Control:no-store。

既存ReportRendererのCSV保護（UTF-8 BOM、CRLF、全セル引用、文字列セルの単引用符）とXLSX型保持を利用できる境界へ抽出し、既存帳票の契約は変えない。
既定上限は全費用20,000行、400万文字、16MiB、生成25秒、DB10秒、プロセス内同時生成1件。設定可能行数上限は100,000。コピーも20,000行・DB10秒を既定上限にする。超過は503で全体失敗し、末尾を切って成功扱いにしない。生成一時ファイルは成功・例外とも破棄する。
月合計は既知行の集計値として安全整数を検査し、XLSXの整数は15桁上限を別に検査する。異常値や上限超過で丸めや部分出力を行わない。

既存 #388 のJSON、v1の16列／3シート、報酬込みv2の33列／5シートは第一切片では変更しない。

## 後続切片の接続方針（別途契約承認）

#388へは広告費モジュールの公開NamedInterfaceから店舗・月・費用ID・版・区分・額の最小事実を渡す。報表が費用tableを直接変更したり監査履歴を合算したりしない。同一REPEATABLE_READで受注・報酬・費用を読む。

推奨は既存4経路にinclude_advertising_costs:boolean（既定false）を追加する明示選択式。falseなら現行v1/v2を完全維持。trueには広告費参照権限、出力には広告費出力権限も要求する。平台向け権限とConsole分類は後続契約で、既存のstoreBridgeを変えずに定める。

月費は日へ割り当てない。trueではfromが月初・toが月末の完全な自然月範囲、group_by=month|storeに限定し、部分月やdayは400とする案を推奨する。これで自然年も366日上限内で照会できる。任意日帳票はfalseのまま使用できる。登録費用だけで受注がない店舗・月も行を生成する。
新basis、追加列、広告費根拠シート、報酬オプションとの組合せは次切片で固定する。現段階で既存フィールドの意味や位置を変えない。費用総額を「実支払」や「完全な利益」に改称しない。

効果指標の契約では、媒体の正規IDと別名対応、対象状態、原営業月／受付月、無媒体、同一人物の新規判定を先に固定する。0分母は単価nullと件数0を返し、未供給と区別する。元受注が持たない新規判定を推測して保存しない。
#396からは将来、応募元媒体と実入店・在籍対応の最小事実を受け、連絡先・面接・添付は受け取らない。#393からは将来、媒体参照・外部実績ID・対象期間・計数種別・値・取得時刻と重複訂正規則が必要。供給未定の段階で空実装のHTTP接口や自動ジョブを作らない。

## 受け入れと検証計画

- 店舗×月の6項目を保存・再表示・編集・削除でき、区分別と全体の登録額が全明細と一致する。零と未入力を区別する。
- コピーは前月・同店・空の対象月に限定し、問い合わせ人数リセット、金額継承、削除行除外、元と先の同時変更、並行二操作、失敗時の全体ロールバックを検証する。
- 全変更の履歴から変更前後・主体・時刻・理由を確認できる。削除後も到達可能。監査保存失敗時に費用だけ残らない。
- 直接APIでも権限なし・授権外店・偽造トークン・storeBridgeなし・別月履歴を拒否する。閲覧だけでは変更／出力不可、MANAGEだけでもVIEWなしは不可。
- HTTP応答消失・同一キー並行・同キー別内容・旧版・削除成功再送・結果不明時の閉じる／再開／再読込みを実DBとUIで検証する。
- 切店・切月・ページ切替・モーダル取消再開の旧応答を破棄し、二重クリックで新しいキーを生まない。出力権限なしと権限を失った後の直接取得も拒否する。
- CSV/XLSXは2,001行以上の先頭・中間・末尾、合計、先頭零、日本語、引用、数式開始文字、null、0、数値型を確認する。同時編集で総計と根拠が混じらず、上限超過／例外後にも再生成できる。
- UIはDESIGN.mdの一覧・フォーム・領域エラーを使い、明暗テーマ・長い文字列・狭幅・キーボード・overlay上通知を実ブラウザで確認する。
- 過去月も同じ変更権限と版検証で編集でき、月次確定・解除を要求しない。業務上の月次ロックは導入せず、コピーと変更の競合を防ぐ短時間のDB排他は維持する。
- 実装の最終検証は `task lint` / `task test` / `task build` / `task e2e`。条件付きPostgreSQL試験の実行・skipを区別し、全量E2Eのexit codeを記録する。PR/CI/公式Codexレビューは最新SHAに対して確認し、マージは所有者の手動操作に委ねる。

実装・検証の結果は完了時に追記する。以前のPRの成功を本機能の検証として数えない。#389と#388は後続項目があるため未完了のまま維持する。

## 根拠

- [#389 現行本文・依存更新コメント](https://github.com/kanghouchao/Kizuna/issues/389#issuecomment-5007924260)
- [#382 2026-08-31裁定](https://github.com/kanghouchao/Kizuna/issues/382#issuecomment-5472559869)
- [#386 現行境界](https://github.com/kanghouchao/Kizuna/issues/386)、[#388 現行残件](https://github.com/kanghouchao/Kizuna/issues/388)、[#393](https://github.com/kanghouchao/Kizuna/issues/393)、[#396](https://github.com/kanghouchao/Kizuna/issues/396)
- [削除前の現行業務フロー](https://github.com/kanghouchao/Kizuna/blob/a9a7a4dbf1b2059bebe913108fddca1196cf2fe3/docs/current-business/workflows.md)、[データ棚卸し](https://github.com/kanghouchao/Kizuna/blob/a9a7a4dbf1b2059bebe913108fddca1196cf2fe3/docs/current-business/data-model.md)
- [既存帳票契約](0388-operational-reports.md)、[報酬接続契約](0388-remuneration-report-contract.md)、[応募受付契約](0396-applicant-intake.md)
- 実装照合: `Order.mediaName` / `ReceptionRoute` / `CustomerSelectionRequest` / `OperationalFacts` / `ReportCriteria` / `ReportSnapshot` / `AuditWriter` / `RemunerationManagementService.once` / `PermissionCode` / `StoreIdInterceptor`。
- テスト照合: `OperationalReportScopeTest`、`RemunerationReportRenderingTest`。履歴照合: PR #991 / #1022 / #956 / #897 のコミット。
