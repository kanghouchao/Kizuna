# 応募者の非公開画像添付

## 問題 / 目的

応募者の構造化記録は非公開で管理できるが、既存アップロードはバケット全体の匿名取得を許す。採用担当者が公開経路を使わずに画像資料を保存・取得できる独立経路を提供する。親課題は #396。基準コミットは #999 マージ後の `9351275ea034fedd96d35f5968f2d335196cc6cd`。

本書の技術契約は主タスクが承認済み。独立した領域・ストレージのfocused実装を先行し、回復書込契約の補足を記載し、既存の承認範囲でHTTP/UIへ接続する。対象は通常の画像資料の副本であり、原始証拠の保全や全ての証件/PDFへの対応を主張しない。実環境の bucket・credentials・アクセス方針の設定、実際の証件投入、既定ロールへの授権は行わない。最終採否・在籍・招待・保持期限は本片の前提にしない。

## 解決方法 / 利用者の操作

1. 採用担当者は専用権限を持つ場合だけ、応募者詳細に添付領域を表示する。
2. 閲覧担当者は完成済み添付の形式・容量・登録日時を確認し、明示的にダウンロードする。
3. 登録担当者は JPEG / PNG を一件ずつ選び、容量と画像の制限を事前に確認する。
4. 登録担当者は画像の安全な再符号化と付加情報除去を知ったうえで保存する。元ファイルの証拠保全を保証する機能とは扱わない。
5. 登録担当者は通信失敗後、同じファイルを同じ操作として再送できる。二重クリックで添付が増えない。
6. 登録担当者は未完了のアップロードを別領域で確認し、ファイルを再選択して回復できる。
7. 閲覧担当者は未完成オブジェクトを取得できない。
8. 同じ店舗の一般担当者も専用権限がなければ、一覧・アップロード・ダウンロードを利用できない。
9. 他店舗の担当者、未認証者、公開サイトの利用者はファイルを取得できない。
10. ストレージ未設定時には未設定と表示し、公開ストレージへ保存先を切り替えない。
11. 登録担当者は容量を占める回復待ち操作を認識できる。新しい操作キーで再送すれば解消すると案内しない。
12. 監査担当者は成功した添付登録を安全な識別子で追跡できる。画像、元ファイル名、ハッシュ、保存先は汎用監査に含めない。

## 実装判断

### 境界と設定

recruitment が添付の所有・アクセス・容量・アップロード操作を管理する。独立した private storage 適合層は専用 S3 client、endpoint、bucket、credentials を使い、公開 client・公開 bucket・`/static`・既存 FileStorageService を利用しない。未設定時の代替値を公開設定から取得しない。

AppProperties の独立設定は既定で enabled=false。enabled=true でも必要値が欠ければ利用不可。公開 endpoint / bucket / 資格情報との誤共有を起動検証で拒否する。公開側の initializer は私有 client を注入されず、私有側は bucket 作成・policy 書換を一切しない。実環境の有効化は別承認とする。

合成検証用だけに、別 endpoint・専用の合成資格情報・tmpfs・host portなし・Traefik公開labelなしのストレージを用意する。既存公開サービスの匿名取得契約を変更しない。匿名 GET/HEAD/LIST/PUT と公開 `/static` 経由の取得拒否を実証する。

### 授権

新規 STORE 権限 `RECRUITMENT_ATTACHMENT_VIEW` と `RECRUITMENT_ATTACHMENT_MANAGE` を追加し、いずれも既定ロール・実アカウントに付与しない。

- R: `RECRUITMENT_VIEW` + `RECRUITMENT_ATTACHMENT_VIEW`。
- W: R + `RECRUITMENT_ATTACHMENT_MANAGE`。
- 全経路で認証、X-Role: store、X-Store-ID、授権店舗集合と @StoreScoped を確認する。
- CAST_MANAGE / RECRUITMENT_MANAGE / RECRUITMENT_DECIDE は代替権限にしない。
- 取得・既存成功の再送は選考状態に依存しない。新規登録は RECEIVED / SCREENING / INTERVIEWED のみ。新規登録と未完了操作の回復は本文受信前にも現在状態を確認し、最終確定時にも再確認する。

### HTTP 契約

全 JSON は snake_case。成功・失敗を含む添付関連応答に `Cache-Control: private, no-store` を付ける。

| メソッド・パス | 要求 | 成功 | 権限 |
| --- | --- | --- | --- |
| GET `/store/applicants/attachment-policy` | なし。attachment-policy は予約語 | 200、Policy | R |
| GET `/store/applicants/{applicantId}/attachments` | cursor?:string、size?:integer（既定20、1〜100） | 200、CursorPage<AttachmentSummary>。READY のみ、created_at DESC,id DESC | R |
| POST `/store/applicants/{applicantId}/attachments` | 必須ヘッダ Idempotency-Key: UUID、Content-Type: image/jpeg または image/png。body は画像バイト列そのもの。multipart・JSON・ファイル名パラメータなし | 201、AttachmentSummary。既存成功と同一内容の再送は200・同一id | W |
| PUT `/store/applicants/{applicantId}/attachment-uploads/{uploadId}/content` | 必須Idempotency-Key:元のUUID、Content-Type:元の宣言MIME、body:元画像バイト列。versionは要求しない | 回復・既存成功とも200、同一AttachmentSummary | W |
| GET `/store/applicants/{applicantId}/attachment-uploads` | cursor?:string、size?:integer（既定20、1〜100） | 200、CursorPage<UploadSummary>。未完了のみ、created_at DESC,id DESC | W |
| GET/HEAD `/store/applicants/{applicantId}/attachments/{attachmentId}/content` | bodyなし。Rangeは無視して全体200、Accept-Ranges: none | GETは200バイナリ、HEADは同じ安全ヘッダ・本文なし | R |

Policy は全項目必須: `configured:boolean`、`allowed_media_types:array<string>`、`max_file_bytes:long`、`max_image_pixels:long`、`max_image_dimension:integer`、`max_decoded_bytes:long`、`max_applicant_files:integer`、`max_applicant_bytes:long`。configured は設定充足を表し、疎通保証とはしない。falseでもPolicyだけは200を返す。他の経路は503で拒否し、ストレージを呼ばない。

AttachmentSummary は全項目必須: `id:string`、`media_type:string`、`size_bytes:long`（正規化後）、`created_at:OffsetDateTime`。状態は常にREADYなので型に含めない。氏名、連絡先、filename、hash、URL、bucket、object key、任意メモを型に含めない。

UploadSummary は全項目必須: `id:string`（操作id）、`idempotency_key:UUID`（再送用識別子であり認証秘密ではない）、`status:PENDING|RECOVERY_REQUIRED`、`media_type:string`、`size_bytes:long`（予約済み正規化後容量）、`created_at:OffsetDateTime`。任意 `failure_code:STORAGE_UNAVAILABLE|CONTENT_MISMATCH|NORMALIZER_UNAVAILABLE` は安全な分類のみ。hash・保存先・例外文言を返さない。別 DTO とし、通常添付一覧へ操作キーを混ぜない。

失敗本文は共通 `{error:string,details?:map<string,string>}`。401未認証、403権限不足/店舗集合外、404対象不在/現在店舗から不可視/非READY添付、400空ファイル・破損・署名不一致・不正UUID/ページング・終端応募者への新規登録、409同キー別内容・同キー実行中・容量予約超過・回復不能な正規化版本、413実バイト/寸法/画素数の上限超過、415非許可Content-Type/Content-Encoding、503未設定・ストレージ障害・画像処理枠満杯。枠満杯はRetry-After: 1。未処理500は固定文言とする。ヘッダやSDK/DB例外の値を応答・ログに転記しない。

### 入力・容量・内容検証

- 画像入力と正規化後ファイルは各最大10MiB（10,485,760 bytes）、空を拒否。幅・高さは各8192以下、積は16,777,216画素以下。設定の上限はこれを超えない。デコード済み画素配列は画像あたり64MiB以下とし、16bit等では画素数上限より先に容量上限で拒否する。PNGは宣言寸法から展開長を計算してIDATの実展開量を検証し、正規化出力は書込み中にも上限を適用する。画像処理の期限は15秒。
- JPEG/PNGのみ。SVG・HTML・PDF・GIF・WebP・APNG等の多フレーム画像、画像以外、圧縮Content-Encodingを拒否する。MIMEだけでは受理しない。署名・画像構造・全体のデコード可能性・寸法・フレーム数を検証する。
- Javaの画像読取器で寸法をデコード前に検査し、全体をデコード後、同形式の単一画像へ再符号化する。EXIF/GPS・コメント・任意chunk・末尾の異種ペイロードを保存しない。アニメーション/APNG検出を ImageIO の枚数だけに依存しない。JPEG再符号化は画質変化があり、元ファイルの完全保存を主張しない。UIでこの挙動を説明する。
- ファイル名をHTTP入力・保存メタデータに採用しない。object keyはサーバ生成の安全IDのみ。ダウンロード名は `attachment-{id}.jpg` または `.png` で生成し、CR/LF/NUL/パス区切りを混入させない。
- 鑑権・応募者の店舗所有・設定を確認してから入力ストリームを開く。`@RequestBody byte[]` や MultipartFile.getBytes による事前全量取得をしない。Content-Lengthは早期拒否用にのみ使い、省略・偽装時も実読取を上限+1で打ち切る。
- 各プロセスの画像受信/変換を同時2件に制限し、permitは読取前に即時取得する。待ちキューを作らず、失敗・切断時にpermitを解放する。制限付きの一時ファイルを使い、通常終了時にfinallyで消去する。一時領域は専用tmpfsで最大128MiB、古い一時ファイルがあれば空き不足を安全に拒否する。アップロード画像をヒープへ丸ごと保持しない。
- 入力受信期限30秒、ストレージAPI全体15秒・単一試行5秒、完了応答を待つフロントエンドの当該要求timeoutは120秒とする。遅い入力を実際に中断できる境界をfocusedテストで確認し、期限チェックだけをタイムアウト保証と呼ばない。
- 応募者あたり20件/200MiB。READYと未完了予約を両方数える。原始データと正規化版を二重にオブジェクト保存しない。予約容量は正規化後サイズ。失敗予約は自動失効しない。

### トランザクション・冪等性・回復

DBとS3の原子コミットは存在しない。操作記録は汎用監査の代用ではなく、非公開オブジェクトの所有・冪等性・回復の正本とする。

1. 認可後に上限付きで一時入力を受け、原始SHA-256と宣言MIMEを確定して既存操作を検索する。READYの再送は原始hash/MIMEの一致で既存結果を返し、現在の符号化器の動作に依存しない。新規操作だけ全体検証と正規化を行う。未完了の回復は保存済み版本/hash/サイズで既存オブジェクトを検証し、欠損した場合だけ元の版本で再生成する。
2. 短いトランザクションで店舗の防削除共有ロック→応募者をロックし、既存の `(store_id,applicant_id,idempotency_key)` を照合する。同じキー・原始hash・MIMEのREADYは新規登録条件より先に既存成功として扱う。新規だけ選考状態と容量を検査し、PENDING操作、予約量、不変のobject IDを確定する。操作行を確定するまでオブジェクトを書かない。この段階は既存操作のロックを待たず、応募者を持ちながら既存操作ロックへ進む経路を作らない。
3. 別トランザクションで店舗の防削除共有ロック→操作ロックを取得する。応募者ロックを保持したまま遠隔ストレージを呼ばない。遠隔処理後に応募者をロックして状態を最終確認し、そのロックはREADY・監査のcommitまで保持する。同キー実行中は待ち続けず409。順序は共有店→操作→最終応募者で固定し、逆順を禁止する。共有の店舗保護で同店の別アップロードを直列化しない。
4. 未知のオブジェクトを上書きせず、条件付き作成 `If-None-Match: *` を利用する。互換ストレージで実証できなければ利用可と判定しない。SDK自動再試行も同じ条件付き作成だけに限定する。条件PUTの412は既存オブジェクトの検証へ進める。条件競合/結果不明は再照会または回復待ちとし、無条件PUTへ切り替えない。
5. タイムアウトは結果不明として扱う。存在するオブジェクトは信頼できるサーバ検証checksum、または上限付きGETで実バイトSHA-256を照合して再利用する。ETagや自分で付けたmetadata hashだけで同一性を断定しない。不一致はRECOVERY_REQUIREDで保持し、上書き・削除しない。
6. 添付READY・操作READY・#395の成功業務監査を同一DBトランザクションで確定する。監査失敗を含むロールバックでは既存PENDING予約が残り、オブジェクトは認可ダウンロード対象にならない。commit成功・応答消失なら再送は同一添付を200で返し、監査やオブジェクトを増やさない。実行トランザクション終了後、安全な失敗状態を独立した短いトランザクションで記録する。操作をロックしてまだ非READYであることを再確認し、別の再送が成功したREADYを降格させない。失敗記録の保存も失敗した場合は、所有が確定しているPENDINGを残す。
7. 回復は未完了一覧から同ファイルを選び、PUT attachment-uploads/{uploadId}/contentへ同じIdempotency-Keyで明示再送する。POSTの同キー再送にも同じ規則を適用する。操作IDはpathで固定し、店舗/応募者/ID/key/原始SHA-256/宣言MIMEを照合する。別scopeのIDは404、同scope内のkeyまたは原始内容違いは409。操作の入力と保存先は不変なのでversionの上書き操作を設けず、操作キーと行ロックで直列化する。原始バイトが異なれば、正規化後の画像・hashが同じでも409。別keyの新規登録には内容の自動重複除外を行わない。成功した同一操作の再送は200で同じ添付IDを返し、再監査・再保存しない。権限・設定・応募者状態を再確認する。原始ファイルが違えば409。既存正規化オブジェクトは保存済み版本/hashで検証し、ランタイム更新後の再符号化値で上書きしない。元の版本で再現できずオブジェクトもなければ409で保持する。

成功添付も失敗オブジェクトも自動削除しない。オブジェクトは全て先行確定した操作に紐づき、不可視な「所有不明の孤児」を作らない。回復不能な予約は容量を占め続ける。この制約をUIで示し、人による放棄・削除と期限処理は別の明示裁定に残す。自動purge/TaskHandler/ライフサイクル設定は追加しない。一時転送ファイルのfinally処理は、永続した応募資料の保持方針とは分ける。

保持未裁定のまま親削除でオブジェクトの対応記録を失わないよう、操作/添付がある応募者・店舗の物理削除をNO ACTION参照と業務409で防ぐ。既存の店舗削除経路への最小ガード追加は共有変更として調整する。削除時にS3を呼ばない。

### ダウンロードと監査

権限と所属をトランザクション内で検査し、READYの不変な保存先情報だけを内部へ渡す。DBトランザクションとStoreContextを切り離し、上限付きで専用一時ファイルへ読み、容量とcanonical SHA-256を検証してからファイルストリームを応答する。非同期スレッドで残存StoreContextに依存しない。保存済み画像は予約時に確定した容量と固定の10MiB安全上限で検証し、現在の新規アップロード上限を下げても取得を妨げない。異常サイズ・同サイズの内容不一致・欠損・ストレージ不通は本文開始前に503。取得用permitも読取前に最大2件とし、画像登録と合わせて一時領域の上限を守る。応答中の切断は転送中断でありJSON成功に変換しない。取得一時ファイルとpermitは切断を含む終了時に解放する。

Content-Typeは検証済み形式、Content-Lengthは確定容量。`Content-Disposition: attachment; filename="attachment-{id}.ext"`、`X-Content-Type-Options: nosniff`、`Cache-Control: private, no-store`、`Accept-Ranges: none`。HEADにも同一認可とヘッダを適用し、画像本文を取得しない。公開URL・署名URL・inlineプレビューは提供しない。ブラウザは既存axiosでblobを受け取り、一時object URLをダウンロード直後に破棄する。

共有業務監査は添付のREADY確定を一度だけ記録する。actorは認証主体から取得し、安全な店舗/応募者/添付/操作IDだけを渡す。操作種別・日時・actorは既存監査の枠組みに従う。ファイル名・原始hash・保存先・画像・自由記述を渡さない。GET/HEADは業務記録を変更せず、ダウンロード完了を監査したと主張しない。ダウンロード監査の要件が生じた場合は #395 の読み取り監査契約へ別途接続する。

### 画面

応募者詳細へ「非公開の添付画像」を追加する。取得失敗はRegionError、未設定は明示文言、権限不足では領域と取得要求を出さない。ファイル名は選択中のローカル表示だけに留め、一覧は生成した添付名・形式・容量・日時・ダウンロード操作を示す。無権限者の応募者一覧/詳細DTOへ添付情報を追加しない。

登録中の二重操作を防ぎ、失敗時は入力と操作キーを保持する。完了/応募者切替/店切替でファイル参照を破棄する。未完了一覧は登録権限者だけに示し、再選択と回復待ちの説明を提供する。既存axios、Base UI、意味tokenを使い、light/dark/390px/キーボード/通知を検証する。

## 検証判断

主境界は応募者HTTP経路と実DBトランザクションとする。storage適合層だけを置換して障害を注入し、内部メソッドの呼出し順ではなく応答・行数・取得可否・監査件数で検証する。既存の応募者権限/店舗分離/E2Eと監査ロールバックの検証方法を再利用する。

- 同店の権限全組合せ、店外/偽ID/偽store、既定ロール無授権、HEADを含む匿名拒否。
- 未設定の零ストレージ呼出し、DTOからURL/key/hash/filename/内容が型として除外されていること。
- 長さ省略/偽装/上限ちょうど/超過、空・不正MIME・署名偽装・破損・SVG/HTML/APNG・画素爆弾・安全な再符号化と付加情報除去。
- 同キー同内容再送/別内容409/並行同キー、異なるキーの並行容量予約、後から応募者が終端化した成功再送。
- PUT成功後の応答timeout、旧PUTの遅延完了、条件付き作成衝突、PUT後監査失敗、commit成功後応答消失、異なるcanonical版本、不一致オブジェクトを上書き/削除しないこと。
- オブジェクトの存在とREADY可視性の分離、未完了操作一覧からの回復、容量が回復待ちを含むこと、親削除の409。
- 独立した合成ストレージで署名なしGET/HEAD/LIST/PUT拒否、公開/static拒否、正規のAPI登録/取得、条件付き作成と実checksum検証。
- リソース枠、入力期限、切断、SDK期限、一時領域満杯で安全に停止すること。単体テストだけで通信期限や実際のアクセス拒否を証明したと扱わない。
- UIの両テーマ・狭幅・キーボード・失敗回復・blob破棄、404時の回復導線、別店舗への切替で残留ファイルがないこと。

focused検証を先行し、重いTaskfile検証は主タスクが調整する395→381の後の独立枠で行う。独立stack名・image tagを用いる。最終lint/test/build/e2eとCodexレビューは実施後にのみ成功と記録する。

## 対象外

実ストレージ設定・実証件投入・採否判断・在籍/招待・外部通知・原本証拠保全・PDF/動画/任意形式・自動削除・期限決定・ダウンロード完了監査・公開/署名URL・店舗横断画面。

## 承認と共有変更

全体技術契約、上記テスト境界、画像正規化とJPEG/PNG限定、容量/期限、永続オブジェクトの非削除は承認済み。仕様と一つの完全な縦切り票をGitHubへ公開する。回復PUTと原始hash一致の補足を主タスクへ報告し、補足を共有したうえで回復UIへ接続する。

調整対象はPermissionCode/件数テスト/前端union、独立設定とS3client注入、413/415等の安全な例外変換、店舗削除ガード、合成E2Eサービス定義。DBはrecruitmentの既存prelaunch baselineへ追記し、不要な増分履歴/総includeを追加しない。共有menu/E2E helperは直接競合編集しない。

## 基準更新と所有範囲（2026-10-07）

- #995 で権限総数は39、通知権限4件が追加済み。本片の専用権限2件を加える場合は41となる。PermissionCode、件数/STORE分類テスト、前端unionの最小差分を主タスクへ調整依頼し、通知権限を維持する。他片の追加が入れば確定headから再計算する。
- 本片のDB定義は既存recruitment baselineへ追加する。新しい総includeや通知定義の変更は不要。追加済みのnotification-delivery includeを維持する。
- 設定は独立したprivate storage項目だけを追加する。#995の通知/イベント再配送設定を変更せず、private clientを適合層の専有フィールドとして生成し、既存public S3Client beanを注入しない。
- ルートTaskfileのbuild/testはDOCKER_TAGを子タスクへ伝達するよう修正済み。本片は固有タグ `issue-396-private-attachments` を指定し、Taskfileそのものの追加修正は予定しない。E2Eは専用worktree由来のstack/tagを使う。
- 381の認証/セッション領域、395の注文監査領域は所有外。既存BusinessAuditの公開境界を利用し、これらの実装を変更しない。
- HTTP・権限・容量・画像検証・回復の契約は本書の提案を維持する。全体契約の承認に基づき独立領域・ストレージfocused実装を開始済み。明示PUTの補足を共有済みで、HTTP/UIへ接続する。

## 検証境界

実装票は #997。#1003同期後の権限は42→44件（STOREは23→25件）で、既定ロールへの追加はない。schemaは既存store/15-recruitment.yamlを使用し、総include・メニュー順は変更しない。口コミ片のstore/16-review-intake.yamlおよびCRM sort3は使用しない。

2026-10-07、独立した使い捨てPostgreSQLで5件の取引テストを実行した。予約の先行確定、監査失敗によるREADYの巻戻し、同一操作のNOWAIT、店外隔離、リモート保存中の辞退、親の物理削除制限を確認した。独立SeaweedFSでは署名PUT200・同キー条件PUT412・署名GET200、匿名GET/HEAD/LIST/PUT403を確認した。いずれも合成データだけを使用した。

実HTTPの低速応答に対して全体期限を検証した。非公開S3はSDKのApache transportでソケットを中断し、既存公開S3はURLConnection transportに明示固定する。UIには19件のfocused回帰検証があり、ファイル再選択時の操作キー維持、回復成功後のキー解放、失敗時の入力保持、画面離脱後のダウンロード抑止を含む。ローカル二軸レビューの仕様・規範の指摘を修正済み。これはTaskfile、CI、Codexの承認を代替しない。

Codex初回指摘の回帰では、一時領域のI/O障害を503、画像の解析失敗を400として区別する。正規化方式の不一致・処理枠不足・期限超過・再現失敗はNORMALIZER_UNAVAILABLEを保存し、既存画像での回復成功後は分類を消去する。実PostgreSQLの6件（skipなし）とHTTP境界・実HTTP低速転送を含むfocused検証で確認した。

#1003の口コミ片を含むmaster `4a426636ba67db894bbe7a309dae62e228d464c7` への同期では、REVIEWの3権限と添付の2権限を両方保持する。口コミのschema・include・CRMメニューと応募者のschema・HRMメニューを維持し、競合は権限件数の断言だけを再計算した。

## 操作結果の照合

`GET /store/applicants/{id}/attachment-operations/{key}` は、既存の添付管理権限（RECRUITMENT_VIEW・RECRUITMENT_ATTACHMENT_VIEW・RECRUITMENT_ATTACHMENT_MANAGE）で単一操作を読み取る。keyは既存Idempotency-Keyと同じUUIDの正規形式をround-tripで検証し、短縮表記は400にする。キーは認証用の秘密ではなく、認証・店舗・応募者の範囲検査を常に行う。無権限店舗の指定は403、認可店舗内で不可視・不存在の応募者や操作は404とする。

200は既存AttachmentUploadResponseのid、idempotency_key、status、media_type、size_bytes、created_atと、省略可能なfailure_codeを返す。statusはPENDING・RECOVERY_REQUIRED・READY。NON_NULLによりnullのfailure_codeは省略される。閲覧用AttachmentSummaryResponseへ操作キーは追加しない。200/400/401/403/404/503はprivate,no-store/nosniff境界に含める。読み取りは独立プロキシのStoreScoped/readOnlyトランザクションで行い、終端応募者も照会できる。アップロードpreflight、予約ロック、画像変換、S3、監査書込みは行わない。固定エラー文言と既存要求IDを使い、キー・画像・本文のログを増やさない。

未完了一覧は一つの取得結果を使い、同じ操作のフォームを重複表示しない。新POSTと回復PUTは、成功応答または同じキーのREADY照合でのみ入力を清掃する。404は読取時点の未発見であり、原要求が後から確定し得るため、原File/keyを保持して同キーの明示再送と再照会を可能にする。照会失敗は対象操作だけを止める。古い応答が新しい操作・分類を上書きしてはならない。

入力訂正は送信前の検証失敗、または400による処理終了と精確照合404の両方が確認できた場合に可能とする。後者もキーは維持する。通信断・5xxなど結果不明の原画像を別画像へ差し替えない。添付の保存完了後に選ぶ次の画像は、新しい操作キーを使う。

既存予約の回復で原画像照合を409で拒否され、同じ操作の存在を精確照合できた場合も、正しい原画像を選び直せる。キーは固定し、サーバーの元画像hash照合を維持する。

原画像の訂正可否は409全般ではなく、既存の固定エラー「元のアップロードと同じ画像・形式・操作キーで再送してください」に限って判定する。処理中や変換方式の競合では原Fileを保持する。
