# アンケートの版管理と回答の手動受付 — 394-B 契約案

実装票: [#1007](https://github.com/kanghouchao/Kizuna/issues/1007)。親課題: [#394](https://github.com/kanghouchao/Kizuna/issues/394)。基点: master `4a426636ba67db894bbe7a309dae62e228d464c7`。状態: **2026-10-07 主タスクがHTTP・状態・正規化・計数・回復・試験面と単独縦切片を承認済み。実装可能**。

## 課題と解決

担当者が受け取ったアンケート回答について、当時の設問・選択肢を特定し、原回答を残して訂正・撤回を追跡する仕組みがない。店舗が設問版を作成して受付を開始し、権限を持つ担当者が既に受け取った回答を手動登録できるようにする。同じ版の回答記録を一覧・詳細・件数で確認する。

これは回答者本人によるオンライン投稿ではない。回答者の本人性・来店資格・匿名性を証明せず、口コミの承認・公開許可とも独立する。画面は「スタッフによる記録」「回答者の本人確認は行っていません」と表示する。

## 現行の根拠と本片の範囲裁定

- #394 が求めるのは回答の収集である。現行実装には設問・回答の管理API／画面はなく、Order の survey_status と baseline の survey_points はこのフローの正本ではない。本片では読み書きしない。
- 旧業務資料は #897 で現行仕様ではないと明示され、#975 で削除されている。過去の質問票・報酬・運用頻度を新しい規則として採用しない。#809 の現行特典種別は紹介・ログイン・来店であり、回答報酬を暗黙追加しない。
- 2026-10-07 の主タスク裁定: 人工受付、店舗自作の版付きテキスト／単一選択設問、本人・受注への関連項目なし、不変回答と理由付き撤回／訂正系列、個票と同版件数を本片に含める。
- これは本片の実装範囲であり、将来も手動受付だけに制限する恒久方針ではない。設問・選択肢の実文は権限を持つ担当者が入力し、プログラムは業務用の質問文・満足度尺度を生成しない。
- 保持期限は未設定・未裁定。期限による自動削除も物理削除APIも提供しないが、無期限保存の承認とは表現しない。実運用の保持・削除方針は別途決める。

## 利用者の振る舞い

1. 閲覧担当者は、自店舗のアンケートと設問版を探し、どの版が受付中か確認できる。
2. 設問管理者は、題名とテキスト／単一選択の設問を自分で入力し、未開始の版を保存・修正できる。
3. 設問管理者は、必須設問・選択肢・順序を確認して受付を開始できる。
4. 設問管理者は、開始済みの設問を上書きせず、新しい版を準備できる。
5. 設問管理者は、受付中の旧版を明示的に終了してから新しい版を開始できる。
6. 受付担当者は、版・取得経路・受領日時を確かめて既存の回答を手動登録できる。
7. 受付担当者は、未回答の任意設問と、回答済みの内容を区別できる。
8. 閲覧担当者は、回答一覧から保護された詳細を開き、当時の設問・回答・受付者を確認できる。
9. 受付担当者は、回答原文を上書きせず、理由を添えて取り下げられる。
10. 受付担当者は、同じ設問版に対する訂正を新しい回答として登録し、旧回答との系列を辿れる。
11. 閲覧担当者は、同版の有効件数と取り下げ件数を区別し、訂正を回答者の増加と誤認しない。
12. 操作者は、通信結果が不明でも同じ要求で確認でき、連打で回答・版・履歴を重複作成しない。
13. 権限を失った操作者は、古い画面・JWT・成功要求の再生から回答にアクセスできない。
14. 担当者は、匿名保証・本人確認・外部公開・回答報酬が本画面で提供されていないことを理解できる。

## 領域と状態

### アンケートと設問版

Survey は店舗のアンケート系列を識別するIDで、現在の最新版・受付中版・下書き版を追跡する。タイトルや設問は SurveyRevision に属し、系列自体に編集可能な別のタイトルを二重保持しない。一覧のタイトルは最新版のものと明示する。

SurveyRevision は同系列内で1から増える revision_number と独立したID、競合検出用versionを持つ。revision_number は業務上の改版番号、version は保存ごとの楽観ロック値であり混用しない。

- DRAFT → OPEN → CLOSED。DRAFT → CLOSED も可能で、未使用の下書きを理由付きで終了する。CLOSED は終態で、再開・下書きへの復帰はしない。
- 系列につきDRAFT、OPENはそれぞれ最大1件。OPENと次のDRAFTは同時に存在できる。
- 初回作成は第1版DRAFT。同系列の最新版がDRAFTでなければ、その最新版を based_on_revision_id で指定して次のDRAFTを作れる。旧版の終了後にも新しいDRAFTを作れる。
- 新版作成は題名・全設問を明示的に送る。画面の「改版」操作は前版の内容を編集初期値として提示できるが、サーバで暗黙コピーしない。
- DRAFTだけ全内容の置換を許す。DRAFTは回答を持てない。OPEN以降の題名・設問・型・必須区分・選択肢・順序は不変で、終了時にも書き換えない。
- 開始には同系列のOPENが存在しないことを要求する。旧版の終了と新版開始は独立した明示操作で、新版開始が旧版を自動終了させることはない。間に受付中版がなくなる時間を許容する。
- 開始／終了は理由必須。別キーの反復・終了済みの開始・OPENの編集は409。形式不正は400。
- 全版の設問識別子 question_key と選択肢 option_key は担当画面が生成する版内の記号で、業務上の意味・横断集計の同一性を持たない。他版に同じキーがあっても別設問として扱う。

### 回答正本

SurveyResponseRecord は一つの設問版に属する。intake_source はサーバ固定 STAFF_RECORDED、状態は ACTIVE → WITHDRAWN の一方向。設問別の回答、received_via、received_at、初回受付者・日時は作成後不変である。受付者は操作をしたSTAFFであり回答者ではない。

received_via は PAPER（紙面）、VERBAL（口頭）、EXISTING_RECORD（既存記録）の三値。自由な取得経緯欄、回答者の名前・連絡先・IP・Customer／Member／Order ID、原資料添付は追加しない。ただし自由文に個人情報が入り得るため「匿名データ」とは呼ばず、権限付きの機密入力として扱う。

初回受付は指定版がOPENで、revision_versionが一致する場合だけ可能。received_atは現実に受け取った時刻を担当者が記録する。システム内の開始時刻より前に紙面等で受領した回答も登録でき、開始時刻以降であることを本人資格の代わりに強制しない。CLOSEDへの新規受付は409。

WITHDRAWNは復帰しない。取り下げは理由必須で、別キーで既に取り下げ済みなら409。取り下げ後も原回答・専用履歴を閲覧でき、物理消去済みとは表示しない。

訂正は、旧回答に後継がないこととversionを検証し、同一取引で旧回答をWITHDRAWNにし、新しいACTIVE回答を作る。旧回答が既にWITHDRAWNでも後継がなければ訂正できる。その場合、過去の取り下げ理由や日時は上書きしない。新回答は同じ設問版に限定し、別版への回答移送はしない。版の受付終了後も訂正・取り下げは可能で、設問版を再開しない。

訂正要求は理由・全回答・取得経路・受領日時を明示する。旧回答者の認証や同一人物性を主張しない。supersedes_id / superseded_by_id は同店・同版の一対一の系列で、一つの旧回答から二本の後継を作れない。後継にさらに訂正を作ることは可能。元記録の反復訂正は409。保存・履歴・汎用監査に失敗したら旧回答の変更も全て巻き戻す。

回答者の識別情報を持たないので、別の受付要求が同じ回答者かどうかを判定しない。業務上の「一人一回答」を実装したと表現しない。冪等キーは通信上の要求重複だけを防ぐ。

### 件数

一つのrevision_idに限定し、total_records、active_records、withdrawn_recordsを返す。total_records = active_records + withdrawn_records。同一DB読取スナップショットの条件付き集計で三値を求め、回答原文は読まない。

訂正前1件ACTIVEなら (total=1, active=1, withdrawn=0)、訂正確定後は (2,1,1)、後継も取り下げたら (2,0,2)。これは記録件数であり、回答者数・来店者数・回答率ではない。個票一覧でstatusを絞っても件数APIは当該版全体を数える。複数版の合算・設問別選択数・スコア・平均・分布は本片に含めない。

## 授権と機密

SURVEY_VIEW、SURVEY_MANAGE（設問版の作成・編集・開始・終了）、SURVEY_RECORD（回答受付・取り下げ・訂正）はSTORE権限。全変更はVIEWと各操作権限を要求する。MANAGEとRECORDは互いを含意せず、既定ロールへの授与なし。権限カタログの追加は実際のスタッフへの授与ではない。

全HTTP端点はSTAFF・enabled・対象店舗を現在のDBで検証する。通常は現在の店舗集合と必要権限を満たすロールを照合する。有効な緊急昇格の場合は現在認証主体と発動者、対象店舗、ACTIVE、発動時刻≦現在時刻＜失効時刻を照合する。不正・撤回・期限切れのelevation claimを通常権限へフォールバックしない。SERVICE／CAST／MEMBERは受付主体にしない。

JWTのmethod securityと機構的storeFilter／storeSetFilterに加えて、全読取・変更・成功要求の再生時に上記を行う。X-Role: store、X-Store-IDが必要。匿名・public・platform向け端点は設けない。別店舗または不存在の対象は404、店舗文脈不成立や現在授権の欠如は403。

題名・設問・選択肢・自由文回答・操作理由は機密入力として扱い、要求本文・バリデーションの入力値・生の例外メッセージをログに残さない。通常のHTTPログへ表示文言や本文を埋め込まない。汎用auditには対象ID、操作種別、状態／版の前後、操作者のみを渡し、設問・回答・理由・冪等の要求本文・fingerprintを渡さない。理由は閲覧権限で保護した専用履歴に保存する。冪等台帳はfingerprintを保存し、生要求の複製を持たない。

## HTTP契約案

### 共通の型と正規化

全リソースIDとActor.idは1〜32桁の数字文字列。Actor.display_nameは文字列で、回答者情報ではない。versionとrevision_versionは0〜2,147,483,647のJSON整数、小数・文字列・booleanを拒否する。revision_numberは1以上。件数は非負JSON整数。日時はoffset必須のISO 8601で、UTCマイクロ秒に正規化する。

以下で`?`は要求の省略またはnullを許すことを示す。応答で`?`は値がない時に共通Jacksonのnon_null設定に従って省略することを示す。クライアント内部ではnullも欠如として扱えるが、HTTPの正規形は省略である。他の全フィールドは必須・非null。未知フィールドは新DTOの境界で400にして、identityや注文関連を受け取ったように見せない。共通Jackson設定全体は変更しない。

文字長はJava／TypeScriptと同じUTF-16単位。全自由文でNULを拒否し、CRLFと単独CRはLFに統一する。title、question.prompt、option.label、reasonは前後空白を除去し、空白のみ不可。answer.textは前後空白を保存し、空白のみ不可。Unicode正規化・連続空白圧縮・HTML変換はしない。UIは全てプレーンテキスト表示としHTMLとして実行しない。

question_key／option_keyは`[A-Za-z0-9_-]{1,40}`で、大小を区別する。前後空白を除去してから検査する。題目と選択肢は配列順を保持し、重複したquestion_key、同設問内のoption_key、同設問内の正規化後同一labelを400にする。文字列に型の暗黙変換をしない。

dedupe_keyは前後空白除去後、英数字と`. : _ -`の1〜120文字（空白は不可）。received_atは2000-01-01T00:00:00Z以降・サーバの現在時刻以下。成功再生では現在時刻に依存する条件を再判定しない。入力上限・型・enum・未知キー・不正日時・不正cursorは固定の説明で400にする。

### 端点

省略表記`S=/store/surveys`、`R=/store/survey-responses`。`{sid}`は系列ID、`{rid}`は設問版ID、`{aid}`は回答ID。親子IDの不一致も404。

| メソッド・パス | 要求 | 成功 | VIEWに加える権限 |
| --- | --- | --- | --- |
| POST S | SurveyCreateRequest | 初回201／再生200 SurveyRevisionWriteResponse | MANAGE |
| GET S | 下記系列一覧条件 | 200 Page<SurveySummaryResponse> | なし |
| GET S/{sid} | なし | 200 SurveyResponse | なし |
| POST S/{sid}/revisions | SurveyRevisionCreateRequest | 初回201／再生200 SurveyRevisionWriteResponse | MANAGE |
| GET S/{sid}/revisions | page、size | 200 Page<SurveyRevisionSummaryResponse> | なし |
| GET S/{sid}/revisions/{rid} | なし | 200 SurveyRevisionResponse | なし |
| PUT S/{sid}/revisions/{rid} | SurveyRevisionReplaceRequest | 200 SurveyRevisionWriteResponse | MANAGE |
| POST S/{sid}/revisions/{rid}/openings | SurveyRevisionActionRequest | 200 SurveyRevisionWriteResponse | MANAGE |
| POST S/{sid}/revisions/{rid}/closures | SurveyRevisionActionRequest | 200 SurveyRevisionWriteResponse | MANAGE |
| GET S/{sid}/revisions/{rid}/history | cursor、size | 200 CursorPage<SurveyRevisionHistoryResponse> | なし |
| POST S/{sid}/revisions/{rid}/responses | SurveyAnswerCreateRequest | 初回201／再生200 SurveyAnswerWriteResponse | RECORD |
| GET S/{sid}/revisions/{rid}/responses | 下記回答一覧条件 | 200 Page<SurveyAnswerSummaryResponse> | なし |
| GET S/{sid}/revisions/{rid}/response-counts | なし | 200 SurveyResponseCountsResponse | なし |
| GET R/{aid} | なし | 200 SurveyAnswerResponse | なし |
| POST R/{aid}/withdrawals | SurveyAnswerActionRequest | 200 SurveyAnswerWriteResponse | RECORD |
| POST R/{aid}/corrections | SurveyAnswerCorrectionRequest | 初回201／再生200 SurveyAnswerWriteResponse | RECORD |
| GET R/{aid}/history | cursor、size | 200 CursorPage<SurveyAnswerHistoryResponse> | なし |

GETは副作用を持たない。DELETE、回答原文のPUT/PATCH、状態を直接代入する汎用端点は設けない。S/Rの省略は文書上だけで、実際のHTTP契約は上記の完全な接頭辞を用いる。

### 要求型

| 型 | 全フィールド |
| --- | --- |
| SurveyDefinitionInput | title:string 1〜120、questions:QuestionInput[1〜20] |
| QuestionInput | question_key:string、type:TEXT / SINGLE_CHOICE、prompt:string 1〜500、required:boolean、options:OptionInput[] |
| OptionInput | option_key:string、label:string 1〜120 |
| SurveyCreateRequest | SurveyDefinitionInputの全フィールド、dedupe_key:string |
| SurveyRevisionCreateRequest | based_on_revision_id:ID、SurveyDefinitionInputの全フィールド、dedupe_key:string |
| SurveyRevisionReplaceRequest | version:整数、SurveyDefinitionInputの全フィールド、dedupe_key:string |
| SurveyRevisionActionRequest | version:整数、reason:string 1〜500、dedupe_key:string |
| AnswerInput | question_key:string、text?:string 1〜2000、option_key?:string |
| SurveyAnswerCreateRequest | revision_version:整数、received_via:前述enum、received_at:日時、answers:AnswerInput[1〜20]、dedupe_key:string |
| SurveyAnswerActionRequest | version:回答の整数、reason:string 1〜500、dedupe_key:string |
| SurveyAnswerCorrectionRequest | version:旧回答の整数、reason:string 1〜500、received_via:前述enum、received_at:日時、answers:AnswerInput[1〜20]、dedupe_key:string |

TEXTのoptionsは必ず空配列、SINGLE_CHOICEは2〜10件。単一選択に「その他自由入力」を混在させない。questions／optionsにnull要素を許さない。配列の合計上限は上記から設問20・選択肢200・回答文40,000文字で有界とする。

回答は指定版に存在するquestion_keyだけを一回ずつ受け付ける。TEXTはtextだけ非null、SINGLE_CHOICEは当該設問のoption_keyだけ非nullとし、もう一方は省略またはnull。キーの重複・未知設問・他設問の選択肢・型不一致を400にする。必須設問は回答必須、任意設問の未回答はanswersから省く。任意の未回答を空文字の回答に変換しない。全設問が任意でも少なくとも一問に回答が必要で、空の受付は作らない。

回答配列はfingerprint算出・保存・応答時にその版の設問順へ整列し、入力配列順の差で別要求にしない。省略した任意のtext／option_keyとnullは同一。設問定義の配列順は意味を持ち、変更すると別内容となる。normalized inputをfingerprintと保存の双方へ同一に渡す。

### 応答型

Actorは `{id:ID文字列, display_name:string}`。下表で入力型の再利用は同じ値の形を意味し、DTO型は要求と応答で分ける。DRAFT置換後を除き、設問の文言や回答を読取時に再正規化して変更しない。

| 型 | 全フィールド |
| --- | --- |
| SurveySummaryResponse | id:系列ID、latest_revision_id:ID、latest_revision_number:整数、latest_title:string、latest_status:DRAFT/OPEN/CLOSED、open_revision_id?:ID、draft_revision_id?:ID、created_at:日時 |
| SurveyResponse | SurveySummaryResponseの全フィールド、created_by:Actor、retention_policy:NOT_CONFIGURED |
| SurveyRevisionSummaryResponse | id:版ID、survey_id:ID、revision_number:整数、title:string、status:DRAFT/OPEN/CLOSED、version:整数、created_at:日時、opened_at?:日時、closed_at?:日時 |
| SurveyRevisionResponse | SurveyRevisionSummaryResponseの全フィールド、based_on_revision_id?:ID、questions:QuestionResponse[1〜20]、created_by:Actor、retention_policy:NOT_CONFIGURED |
| QuestionResponse | question_key:string、type:TEXT/SINGLE_CHOICE、prompt:string、required:boolean、options:OptionResponse[0〜10] |
| OptionResponse | option_key:string、label:string |
| SurveyAnswerSummaryResponse | id:回答ID、survey_id:ID、revision_id:ID、revision_number:整数、intake_source:STAFF_RECORDED、received_via:前述enum、received_at:日時、created_at:日時、status:ACTIVE/WITHDRAWN、version:整数 |
| SurveyAnswerResponse | SurveyAnswerSummaryResponseの全フィールド、answers:AnswerResponse[1〜20]、recorded_by:Actor、supersedes_id?:ID、superseded_by_id?:ID、retention_policy:NOT_CONFIGURED |
| AnswerResponse | question_key:string、text?:string、option_key?:string |
| SurveyResponseCountsResponse | survey_id:ID、revision_id:ID、total_records:整数、active_records:整数、withdrawn_records:整数 |
| SurveyRevisionWriteResponse | revision:現在のSurveyRevisionResponse、operation:SurveyOperationResponse |
| SurveyAnswerWriteResponse | answer:現在のSurveyAnswerResponse、operation:SurveyOperationResponse |
| SurveyOperationResponse | id:操作ID、type:下記操作enum、resource_id:結果対象ID、committed_version:整数、replayed:boolean |
| SurveyRevisionHistoryResponse | id:履歴ID、type:下記設問版enum、created_at:日時、actor:Actor、before_version?:整数、after_version:整数、before_status?:設問版status、after_status:設問版status、reason?:string |
| SurveyAnswerHistoryResponse | id:履歴ID、type:下記回答enum、created_at:日時、actor:Actor、before_version?:整数、after_version:整数、before_status?:回答status、after_status:回答status、reason?:string、related_response_id?:ID |

設問版の履歴／操作enumは DRAFT_CREATED、DRAFT_REPLACED、OPENED、CLOSED。回答の操作enumは RECEIVED、WITHDRAWN、CORRECTION_RECEIVED。回答履歴にはさらにCORRECTION_LINKEDがある。新旧回答の訂正履歴は同じ理由を二重保存せず、旧側CORRECTION_LINKEDに理由と新ID、新側CORRECTION_RECEIVEDに旧IDを記録しreasonはnullとする。訂正理由は旧側履歴で確認できる。

設問版のreasonはOPENED／CLOSEDのみ必須。回答のreasonはWITHDRAWN／CORRECTION_LINKEDのみ必須。他はnull／省略。DRAFT_CREATED、RECEIVED、CORRECTION_RECEIVEDのbefore_*はnull／省略。他の履歴はbefore_*必須。related_response_idは訂正の両履歴のみ非null。改版元は版詳細のbased_on_revision_idで確認する。

一覧DTOに設問本文・選択肢・回答原文・操作理由・受付者を含めない。タイトルは問卷一覧の識別のため意図して返すが、全文設問を添えない。回答詳細は版IDから設問版詳細を別取得して組み立て、各回答に文面を複製しない。設問版は初回回答時点で不変なので過去の回答表示が変わらない。

訂正のWriteResponseは新回答を返す。operation.resource_id／committed_versionは当該操作で確定した結果を指す。初回と再生でoperation.idは同一であり、返すrevision／answerは返却時点の現在値。その後終了・取り下げ・再訂正があればcommitted_versionと詳細versionは異なり得る。系列の現在ポインタは書込み後にGET S/{sid}で再取得する。

### 一覧・履歴

全Page一覧はpage既定0・0以上、size既定20・1〜100。Spring Pageの既存snake_case外殻（content、number、size、total_elements、total_pages等）を使用する。無制限の配列は返さない。

系列一覧はq?:最新版titleの前後空白除去後1〜120文字の大小無視部分一致、survey_id?:ID完全一致、latest_status?:DRAFT/OPEN/CLOSED、sort?:CREATED_DESC（既定）/CREATED_ASC。qが空白のみなら条件なし。条件はAND。LIKEの`%`、`_`、`\`は文字として扱い、設問／回答文検索を追加しない。created_at、idを同方向に並べる。

版一覧はrevision_number DESC、id DESC固定。回答一覧はresponse_id?:ID、status?:ACTIVE/WITHDRAWN、sort?:RECEIVED_DESC（既定）/RECEIVED_ASC/CREATED_DESC/CREATED_ASC。時刻の後ろにidを同方向へ並べる。status省略は両方を返す。別版への横断検索は作らない。

履歴はCursorPageのcontentとnext_cursor（次がなければ省略）。size既定20・1〜100、cursor任意。id DESCで全順序を作り、cursorも同じIDの排他的比較を用いる。履歴の対象はURLのリソースから限定し、cursorから権限や店舗を決めない。不正sort／enum／cursor・範囲外page／sizeは400。

ページ間や個票一覧と件数API間の同時更新による差はあり得る。永続スナップショット・回答率・網羅的エクスポートを保証しない。件数読取り自体の三値は同じDBスナップショットで整合させる。

### 冪等・競合・失敗と取引

全変更操作に店舗＋現在操作者ID＋dedupe_keyの一意範囲を共用する。fingerprintには操作種別、URLの対象ID、正規化した全要求（version、based_on_revision_idも含む）を含める。同キーの操作／対象／内容差は409。キーや本文をGET queryに移さない。

形式と現在授権の検証後、成功済みの同じ要求を現在の状態／版／開始可否／受領日時上限より先に再生する。後続の取り下げ、版終了、権限失効を古い応答で取り消さない。失敗要求は成功台帳を消費しない。再生で設問版・回答・履歴・auditを増やさない。

系列の版追加／置換／開始／終了では系列ロックを先に取得し、対象版を次にロックする。based_on_revision_idが現在の最新版と異なる場合、新版とDRAFTを二重作成しようとした場合、別のOPENがある場合は409。版置換／開始／終了のversionは対象版の現在値と一致させ、成功時に増加する。

初回回答では対象版ロックの下でOPENとrevision_versionを再確認して受付を確定する。終了との競争は「終了前に受付成功」か「終了後に受付409」のどちらかとなる。回答受付で設問版versionは増やさず、同版に複数回答を記録できる。回答取り下げ／訂正は旧回答ロックとversionで直列化し、同じロック順を保つ。訂正後継の一意制約でも枝分かれを拒否する。

同キーの並行初回が競争した場合はDB一意制約で一件だけ確定させ、負けた取引をロールバックした後、新しい取引で現在授権とfingerprintを再検査して再生する。aborted transaction内で問い合わせを続けない。他のDB制約違反を冪等再生に読み替えない。

保存・専用履歴・成功台帳・audit::recordingは一つのDB取引。監査失敗時も全て巻き戻す。外部I/O・通知・ジョブ登録は存在しない。既存のaudit正本を使用し別の監査保存先を作らない。

| 失敗 | 条件 |
| --- | --- |
| 401 | 未認証・失効した認証セッション |
| 403 | 現在のSTAFF／enabled／権限／店舗／緊急昇格検証に失敗 |
| 404 | 系列・版・回答が不存在／他店、親子ID不一致 |
| 400 | 型・上限・必須・時刻・設問／選択肢参照・回答型・cursor等の入力不正 |
| 409 | 旧version・旧改版元・キーの内容差・状態競合・二重DRAFT／OPEN／訂正・別キー反復 |
| 500 | 予期しない保存等の失敗。既存の固定文言とし生例外を出さない |

共通エラーは `{error:string, details?:{field_path:string}}`。detailsキーもsnake_case（例answers[0].option_key）。不正入力値・SQL・制約名・設問・回答の断片をerror/detailsへ埋め込まない。

## モジュール・永続化と共有変更

surveyモジュールが系列、設問版、回答正本、専用履歴、冪等台帳を所有する。reviewのエンティティ・公開投影・order::review-originに依存しない。Customer／Member／Orderへの関連や照会を設けない。認証主体の現在DB確認と有効な緊急昇格の扱いは現行実装に揃え、別の共通権限基盤を本片で発明しない。

設問と回答は上限付きの版／回答所有値として保存し、保存形の選択で上記HTTP契約を変えない。系列と版、版と回答、訂正元と後継は同店参照をDB制約で保証する。設問・選択肢参照の整合は不変の版を使うドメイン検証で保証し、TEXTの選択肢混入や単一選択の本文混入を拒否する。

全表はt_接頭辞とstoreFilter／storeSetFilterを持つ。系列＋revision_number、系列あたりDRAFT／OPENの各部分一意制約、訂正元の一意性、店舗＋操作者＋キーをDBで保証する。FKはonDeleteを明示し、同一店舗削除の既存方針に合わせる。スタッフ削除等で履歴の原文が連鎖消去されないよう主体のスナップショットを保持する。系列／版／回答を物理削除する業務APIは設けない。

DBはpre-launch baselineへ終端形を追加し、増分移行を作らない。主タスクはschema番号17（survey-intake）とCRMメニュー順4を本片へ割り当て済み。三権限の目録・frontend union・件数試験、baseline include、CRM「アンケート管理」のVIEWメニューが共有変更の最小範囲。基点の42権限に三つを加えるが、#1004私有添付の二つが先行合流した場合は47となり両方を保持する。実際の授与は行わない。

## 管理画面と回復

店舗CRM「アンケート管理」に系列一覧、版一覧・詳細、下書き編集、開始／終了、回答一覧・詳細、人工受付、取り下げ／訂正、専用履歴、同版件数を用意する。VIEWだけは読取専用。MANAGEとRECORDを別々に表示制御し、片方の権限だけで他方の操作を出さない。

設問編集はTEXT／SINGLE_CHOICE選択、必須区分、順序、選択肢の追加・削除を上限内で扱う。事前に業務文面を埋めた質問票は作らない。「下書き」「受付中」「受付終了」を示し、OPEN以降は読取表示へ切り替える。改版フォームの保存は新しい版を作ると明示し、既存回答に影響しないことを説明する。

受付画面は版番号と当時の設問を表示し、OPEN時だけ新規受付できる。閲覧中に終了した場合は409で入力を保ち、最新状態を取得する。回答を別版に黙って切り替えない。訂正は同じ版で固定し、「旧回答を取り下げ、新しい回答記録を作成する」と理由付き確認を行う。取り下げを「削除」「個人情報の消去」と表示しない。

一覧には原回答の抜粋を出さず、取得中と適用済み条件を区別する。回答詳細と設問版詳細の組合せは同じ店舗・版・要求世代に揃える。古い詳細を新しい設問へ結び付けない。店舗・対象変更、権限拒否時は既存の機密表示と未送信入力を破棄する。

送信中は同じ操作の連打を禁止する。タイムアウト／通信切断／5xxで結果不明なら、同じキーと元入力を当該店舗画面のメモリ内に保持し、同じPOST／PUTを再送して確定結果を確認する。未確定のまま内容を変えて別キーで保存しない。ダイアログを閉じても同じページが生きている間は未確定要求を保持し、再確認へ戻れるようにする。

400は入力を保持して修正し、未確定操作でないことを確認後、新しいキーで送信する。401／403は操作を止め機密表示を外す。404は存在確認で親／版／回答の欠落を区別し、消えた対象への操作を外す。改版のbased_on_revision_idが不在の場合は系列自体を再照会し、系列が見つかる間はフォームを誤って消さない。409は元の入力を保持し最新状態を表示して、担当者の再判断を要求する。

本文・理由・設問・未確定要求をlocalStorage／sessionStorageへ保存しない。画面を離れた後の自動再送はしない。再訪時は一覧・履歴の確認を経て担当者が判断する。これは無制限に再開できる配送保証ではない。

画面上に「保持期限は未設定」を明示する。「匿名」「本人確認済み」「外部公開済み」「回答報酬付与済み」を成功状態にしない。顧客への招待・メール送信・通知キュー投入はない。

## 試験面と受け入れ

主な試験面は現在DB授権を通るstaff HTTPと実管理画面。既存reviewのHTTP／PostgreSQL／入力ログ／UI回復試験を先例とするが、レビュー承認等の業務規則は複製しない。新たなHTTPモック専用の成功sinkを作らない。実装詳細をなぞるだけの試験より、利用者が観測できる結果と取引不変条件を優先する。

- 設問作成→DRAFT編集→OPEN→回答受付→照会→取り下げ／訂正→同版件数を、一つの縦切片として実証する。
- TEXT／SINGLE_CHOICE、任意／必須、未回答、省略／null、空白・改行・NUL・型・上限・不正key・他設問の選択肢・未知フィールドをHTTPで確認する。
- 改版時のDRAFT一意性、OPEN一意性、旧版の固定、終了後の初回拒否と訂正許可、下書き終了と次版作成を確認する。
- 同版件数が定義通りとなり、訂正後は有効数を二重加算せず、全レコード数には履歴上の旧回答が残ることを確認する。
- 現在DBのSTAFF／enabled／店舗集合／必要権限、有効昇格、期限境界・撤回・別主体・別店舗・不正claimを読取／書込／再生で確認する。通常権限への不正フォールバックも拒否する。
- 機構的店舗隔離、同店FK、部分一意、版終了対受付、並行改版／開始、同キーの初回競争、二重訂正、監査失敗時のロールバックを実PostgreSQLとapplication取引で検証する。
- 各書込みの同キー再生、同キー異内容、正規化同値、操作違い、旧version、状態の別キー反復、後続変更後の現在詳細、権限失効後の拒否を確認する。
- 一覧型から機密原文が除かれ、固定エラー・ログ・汎用auditに設問／回答／理由が混入しないことを確認する。
- Pageの全sort、同時刻ID順、検索リテラル、history cursor、親子ID不一致、DRAFTでの空回答一覧／0件数を確認する。
- UIの権限別導線、店舗／対象切替、古い設問版の応答、二重クリック、未確定→同要求再送、400/404/409回復、閉じたフォームの未確定保持を確認する。
- キーボード、390px／通常幅、明暗テーマ、長い質問・選択肢・原文、モーダルと通知の重なりを実ブラウザで確認する。
- 最終はTaskfile lint/test/build/e2e、独立したSpec／Standardsレビュー。重いE2Eは主タスクと枠を調整し、専用タグ・stack・portsを使う。仕様作成段階で起動しない。

試験用の役割・回答は隔離環境の合成データだけで作り、実スタッフへの権限授与や実顧客への連絡を検証の前提にしない。

## 対象外と後続

本人オンライン回答、招待URL／秘密token、本人資格・一人一回答・頻度制限、匿名保証、Customer／Member／Orderとの関係、マーケティング同意・業務連絡同意の推定、顧客への自動招待・実送信、口コミの承認・公開への変換、対外公開、複数選択・数値評価・条件分岐、回答報酬、横断集計・スコア・CSV／エクスポート、添付、保持期限・自動消去は独立した未実装範囲である。

これらの恒久的不採用を決めるものではない。本切片完了を #394 全体の完了とせず、通知の未接続業務、オンライン等の後続裁定、実公開等を親課題へ残す。

## 承認対象

主タスクは、上記17端点・型・ページング、DRAFT／OPEN／CLOSEDと明示的版切替、同版のみの訂正、件数の定義、入力正規化・授権・冪等・回復、HTTP／PG／UIの試験面、一枚の縦切片票としての粒度を承認済み。実顧客への送信・実スタッフへの権限授与は承認に含まれない。
