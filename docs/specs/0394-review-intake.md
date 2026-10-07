# 口コミの手動受付・承認・取り下げ — 394-A 契約案

親課題: #394。実装票: [#998](https://github.com/kanghouchao/Kizuna/issues/998)。基点: master `a047a45fe0e2f9650a1cf3b1db223b60332cd69b`。状態: **主タスクが契約・単独縦切片・試験面を承認済み。実装可能。実公開・外部送信の許可は含まない**。通知切片 #995 の受け入れとは別の切片である。

## 課題と解決

担当者が紙面・口頭・既存の電子記録から受け取った口コミについて、原文、受付経緯、内部の承認判断、公開許可とその撤回を追跡できない。手動受付の記録を顧客本人の認証済み投稿と混同せず、内容を固定した受付正本と独立した許可記録を設ける。

本切片は店舗の管理画面で完結する。内部承認には公開許可を必須としない。#392 へ渡せる投影は、読取時点で承認済み・公開許可有効・未取り下げの三条件を満たすものだけとする。投影への適格性は公開済みを意味せず、公開ページや公開期間・順序の管理は実装しない。

## 確定事項と承認された設計判断

既定裁定は [#370](https://github.com/kanghouchao/Kizuna/issues/370#issuecomment-5002855510)、[#394](https://github.com/kanghouchao/Kizuna/issues/394)、[#392](https://github.com/kanghouchao/Kizuna/issues/392) に従う。口コミの収集・承認と公開の二段階を分離し、公開三要素は #392 に任せる。適格な原文と表示名だけを公開向け投影に含め、許可根拠・判断理由・受注や顧客の参照を公開情報へ混ぜない。通知の業務連絡許可から公開許可を推定しない。

以下は今回の契約審査で採用された判断であり、既存業務から自明に導かれた前提とは区別する。

1. 任意の受注関連は「同店で現在可視、COMPLETED、完了無効化なし」を受付・訂正時に要求する。指定時のみ現在の ORDER_MANAGE も要求する。受注なしの受付は可能で、いずれも本人投稿・購入者確認の証明とはしない。後日の受注訂正は口コミや許可へ自動波及させない。
2. 公開許可は当該店舗の自社サイトで当該本文と表示名を掲載する範囲に限定する。証拠種別・許可を受けた日時・根拠メモをスタッフが記録する。原資料アップロードや本人確認は含めない。
3. 本文等は不変。訂正は旧記録の取り下げと新しい PENDING 記録の作成を原子的に行い、旧承認と旧許可を継承しない。WITHDRAWN は口コミ状態の終態とする。
4. 許可撤回は口コミの取り下げと別の操作とし、REVOKED は当該受付の許可の終態とする。再許可が必要なら訂正再受付で新しい記録・承認・許可を得る。
5. 現在の STAFF・店舗集合・VIEW と操作権限をDBで再確認する。MANAGE は受付・訂正・取り下げ・許可記録／撤回、MODERATE は承認／却下だけを担当する。既定授与はしない。
6. 全ての変更操作に操作者・店舗単位の冪等キーを使う。同じ成功要求は後続状態が変わっても再適用せず、元の操作識別子と現在の詳細を返す。
7. 一覧検索は表示名の部分一致と口コミIDの完全一致だけに限定する。本文・許可根拠・判断理由・受注ID・顧客連絡先を検索対象にしない。独立した外向けHTTP投影は作らない。

下記の要求、状態系列、試験面、一つの縦切片をまとめて承認済み。実装票と完全な仕様をGitHubに掲載し、親課題 #394 はOPENを維持する。

## 利用者の要求

1. 受付担当者として、受け取った口コミを手動受付であると明示して登録したい。
2. 受付担当者として、受付経路と実際の受取日時、システム記録日時を区別したい。
3. 受付担当者として、受注を関連付けなくても記録し、任意で同店の適格な受注を関連付けたい。
4. 閲覧担当者として、連絡先や本文を一覧へ載せず、表示名・口コミID・状態から対象を見つけたい。
5. 承認担当者として、固定された本文と受付事実を確認し、理由付きで承認または却下したい。
6. 承認担当者として、公開許可のない口コミも内部で承認し、公開は許可されていないと判断できるようにしたい。
7. 受付担当者として、具体的な原文と表示名に対する公開許可の取得経緯を記録したい。
8. 受付担当者として、公開許可を単独で撤回し、承認済みの記録も直ちに公開候補から外したい。
9. 受付担当者として、口コミ全体を理由付きで取り下げたい。
10. 受付担当者として、原文を上書きせず訂正再受付し、旧記録との対応を辿りたい。
11. 閲覧担当者として、却下・取り下げ・許可撤回を含む変更履歴と記録者を確認したい。
12. 操作者として、二重クリックや通信結果不明後の再照会で受付や履歴を増やしたくない。
13. 操作者として、他の担当者による変更との競合を認識し、古い版の判断を上書きしたくない。
14. 管理者として、現在の役割・店舗集合・アカウント停止に反する読み書きを防ぎたい。
15. 公開側の利用者として、適格な表示名と本文だけを得て、許可根拠や受注情報を取得しないようにしたい。
16. 監査担当者として、汎用監査へ原文や許可メモを複製せず、誰が何の状態を変えたかを追跡したい。

## 領域と状態

### 不変の受付

Review は店舗所有。intake_source はサーバが STAFF_RECORDED に固定する。received_via は PAPER / VERBAL / ELECTRONIC。電子記録からの手動転記も外部システムによる本人検証やメール受信機構とは扱わない。verified_customer という属性や表示は設けない。

body、display_name、received_via、received_at、origin_order_id は作成後不変とする。customer_id、連絡先、通知IDを入力項目にしない。匿名表示は display_name=null とし、実名必須にはしない。受注参照はスタッフの関連付けであり、当該受注の顧客が原文を書いたという証明ではない。

status は PENDING / APPROVED / REJECTED / WITHDRAWN。PENDING のみ承認・却下できる。PENDING / APPROVED / REJECTED は取り下げ可能。WITHDRAWN から復帰せず、APPROVED と REJECTED の相互変更も行わない。既に終わった遷移を別キーで行うと409。同一成功要求の再生は後述の例外として、状態を変えず成功応答を返す。

### 公開許可

permission_status は NOT_GRANTED / GRANTED / REVOKED。新規受付と訂正再受付は必ず NOT_GRANTED で開始する。

許可の記録は NOT_GRANTED かつ口コミが PENDING または APPROVED の場合にだけ可能。許可 scope は OWN_STORE_WEBSITE に固定し、受け付けた不変本文と表示名だけを対象にする。basis_type は WRITTEN / VERBAL / ELECTRONIC_RECORD、granted_at は取得日時、evidence_note は担当者が確認した根拠の要約。recorded_by、recorded_at はサーバが現在のスタッフから記録する。受付・許可・履歴の記録者名は操作時点のスナップショットを保持し、後日の停止・改名で書き換えない。

許可撤回は GRANTED なら口コミ状態にかかわらず可能で、REVOKED とし、withdrawal_received_at・reason・記録者・記録日時を追加保持する。取り下げ済みの口コミにも許可撤回を記録できるが、口コミ状態は変えない。許可撤回で APPROVED を PENDING に戻さず、内部承認と許可を独立させる。許可取得・撤回の日時は事実の記録であり、効力は操作のDB確定時点から反映し、過去時点の公開を正当化したり未来まで撤回を遅延させたりしない。

取得根拠は上書きせず、誤記は撤回と訂正再受付で扱う。REVOKED への再許可は行わない。電子記録のURL・添付ファイル・本人の連絡先は収集しない。メモ入力では必要最小限の根拠を案内するが、任意文に個人情報が含まれないという保証はせず、機密として保護する。

### 訂正再受付

POST /store/reviews/{id}/corrections は最新の受付（訂正先のない記録）を基に新規受付を作る。旧記録が WITHDRAWN でなければ同じ取引で WITHDRAWN にし、既に WITHDRAWN なら状態と元の取り下げ理由を保持したまま訂正先を結ぶ。本文が同じでも、許可根拠を取り直す目的で再受付できる。

新規受付の全不変入力は明示的に渡し、既存の受注関連や許可を暗黙コピーしない。新記録は PENDING / NOT_GRANTED。旧記録の承認・取得許可・履歴は変更しない。supersedes_id と superseded_by_id は同店の一対一の訂正系列で、同じ旧記録から別キーで二本目の訂正を作れない。旧版との競合・既存の訂正先は409。入力・受注検証・保存・監査が失敗すれば旧記録の取り下げも含めてロールバックする。

WITHDRAWN は口コミ状態の終態であるが、独立した許可撤回、訂正系列の参照追加、過去要求の再生は許す。いずれも旧口コミを公開候補へ戻さない。

## 認可と機密

REVIEW_VIEW / REVIEW_MANAGE / REVIEW_MODERATE は STORE 権限で、既定ロールへの授与なし。全HTTP端点は現在有効な STAFF と指定店舗を要求し、通常の店舗集合・必要権限、または対象店舗への有効な緊急昇格をDBで検証する。昇格は現在の認証主体と発動者、対象店舗、ACTIVE状態、発動後かつ期限前を照合し、撤回・期限切れ・別主体・別店舗を拒否する。昇格claimが不正なら通常権限へフォールバックしない。JWTと画面の表示だけに依存しない。

全書込みは REVIEW_VIEW と各操作権限を要求する。origin_order_id を指定した新規受付・訂正だけは ORDER_MANAGE も現在のDBで検証する。受注を表示する権限がなくても、REVIEW_VIEW により既に記録済みの関連IDは詳細で確認できるが、受注内容・顧客情報の取得権は追加しない。

現在のDB授権（緊急昇格では有効な発動記録）の検証後に冪等再生を判断する。旧要求の成功は失効した閲覧・操作権限を復活させない。店舗指定の不成立は既存403、対象が不存在または他店の場合は404。

本文・表示名・許可根拠・理由は口コミの保護された正本と専用履歴にだけ保持する。汎用監査は対象ID・操作分類・前後の状態／版・主体だけを記録し、自由文・受注ID・customer IDを含めない。固定の監査説明を使い、自由入力の理由は専用履歴から辿る。要求本文・例外の生メッセージ・冪等の生要求をログへ出さない。

## 承認済みHTTP契約

全IDは1〜32桁の数字文字列。version は非負整数。offset付き日時はUTCマイクロ秒精度で返す。新規の received_at / granted_at / withdrawal_received_at は2000年以降・サーバの現在時刻以下。未来予約は扱わない。同一成功要求の再生では移動する現在時刻条件を再判定しない。

文字長は既存のJava／TypeScript入力と同じUTF-16単位。本文はCRLFをLFに正規化するが、前後空白は保存し、空白のみとNULを拒否する。表示名・理由・根拠・キーは前後空白を除去して比較し、空文字／NULを拒否する。省略したdisplay_nameとnullは同じ匿名表示。空文字を匿名に読み替えない。キーは英数字と `. : _ -` の1〜120文字。Unicode正規化・連続空白の圧縮・HTML変換は行わない。fingerprintはこれらの規則で実際に保存する値に結び付ける。許可取得後に本文・表示名・根拠を再正規化して書き換えない。

| メソッド・パス | 要求 | 成功 | 必要権限（VIEWは全て必須） |
| --- | --- | --- | --- |
| POST /store/reviews | ReviewCreateRequest | 初回201、再生200 ReviewWriteResponse | MANAGE、受注指定時ORDER_MANAGE |
| GET /store/reviews | 下記一覧条件 | 200 Page<ReviewSummaryResponse> | VIEW |
| GET /store/reviews/{id} | id | 200 ReviewResponse | VIEW |
| POST /store/reviews/{id}/decisions | ReviewDecisionRequest | 200 ReviewWriteResponse | MODERATE |
| POST /store/reviews/{id}/withdrawals | ReviewActionRequest | 200 ReviewWriteResponse | MANAGE |
| POST /store/reviews/{id}/permissions | ReviewPermissionRequest | 初回201、再生200 ReviewWriteResponse | MANAGE |
| POST /store/reviews/{id}/permission-revocations | ReviewPermissionRevocationRequest | 200 ReviewWriteResponse | MANAGE |
| POST /store/reviews/{id}/corrections | ReviewCorrectionRequest | 初回201、再生200 ReviewWriteResponse | MANAGE、受注指定時ORDER_MANAGE |
| GET /store/reviews/{id}/history | cursor任意、size既定20・1〜100 | 200 CursorPage<ReviewHistoryResponse> | VIEW |

### 要求型

| 型 | 全フィールド（?は省略またはnull可） |
| --- | --- |
| ReviewCreateRequest | body:string 1〜5000、display_name?:string 1〜60、received_via:上記enum、received_at:日時、origin_order_id?:ID、dedupe_key:string |
| ReviewActionRequest | version:整数、reason:string 1〜500、dedupe_key:string |
| ReviewDecisionRequest | version:整数、decision:APPROVE/REJECT、reason:string 1〜500、dedupe_key:string |
| ReviewPermissionRequest | version:整数、basis_type:上記enum、granted_at:日時、evidence_note:string 1〜500、dedupe_key:string。scopeはサーバが固定 |
| ReviewPermissionRevocationRequest | version:整数、withdrawal_received_at:日時、reason:string 1〜500、dedupe_key:string |
| ReviewCorrectionRequest | version:旧記録の整数、reason:string 1〜500、および新規受付の全フィールド（dedupe_keyは1つ） |

承認／却下・取り下げ・許可撤回・訂正の理由は全て必須で、空白のみ不可。承認理由を口コミ本文や公開文面に流用しない。許可の記録はreasonの代わりに必須のevidence_noteを用いる。本文変更用のPUT/PATCH/DELETEや、状態を直接代入する汎用操作は設けない。

### 一覧と応答型

一覧条件は page:既定0・非負、size:既定20・1〜100、q?:表示名の大小無視部分一致1〜60、review_id?:ID完全一致、status?:上記口コミenum、permission_status?:上記許可enum、sort?:RECEIVED_DESC（既定）/RECEIVED_ASC/CREATED_DESC/CREATED_ASC。条件を併記した場合はAND。qの前後空白を除去し、空なら条件なし。`%`・`_`・`\` は文字そのものとして検索する。received_at または created_at の後ろに同方向のidを付けた全順序を使う。未知のsort・enum・不正cursor・範囲外page/sizeは400。本文・受注・理由・根拠に対する検索を追加しない。

ReviewSummaryResponse は id、intake_source、display_name（null可）、received_via、received_at、created_at、status、permission_status、version。本文・理由・許可根拠・受注ID・顧客情報は型に含めない。

ReviewResponse は Summary に body、origin_order_id（null可）、origin_checked_at（null可）、origin_order_version（null可）、recorded_by（id/display_name）、supersedes_id（null可）、superseded_by_id（null可）、permission（null可）、publication_eligible:boolean、publication_blockers:enum配列、publication_connection:NOT_CONFIGURED を加える。顧客IDや連絡先は持たない。

permission は id、scope:OWN_STORE_WEBSITE、basis_type、granted_at、evidence_note、recorded_by、recorded_at、revocation（null可）。revocation は withdrawal_received_at、reason、recorded_by、recorded_at。許可未取得ならpermission=null、取得済みなら撤回後も原記録を保持する。

publication_blockers は口コミが WITHDRAWN ならWITHDRAWN、それ以外でAPPROVEDでなければNOT_APPROVED、許可がNOT_GRANTEDならNO_PERMISSION、REVOKEDならPERMISSION_REVOKEDをこの順に返す（最大2件）。空の場合だけpublication_eligible=true。適格でも本切片では常にpublication_connection=NOT_CONFIGUREDであり、画面は「公開連携は未設定」と表示する。

ReviewWriteResponse は review:現在のReviewResponse と operation:{id、type、review_id、committed_version、replayed:boolean}。type は RECEIVED / APPROVED / REJECTED / WITHDRAWN / PERMISSION_GRANTED / PERMISSION_REVOKED / CORRECTION_RECEIVED。review_idとcommitted_versionは返却対象の結果記録を指す。訂正は新規口コミのIDと作成時versionをoperationに持ち、新規口コミをreviewに返す。旧記録はsupersedes_idから辿る。初回と再生でoperation.idは同じ。committed_versionは当該操作の確定時点、review.versionは返却時点の最新値であり、一致するとは限らない。

ReviewHistoryResponse は id、type、created_at、actor:{id,display_name}、before_version（受付時null可）、after_version、before_status（受付時null可）、after_status、before_permission_status（受付時null可）、after_permission_status、reason（理由のない受付・許可取得時null可）、related_review_id（訂正時のみ、それ以外null）、permission_record_id（許可操作時のみ、それ以外null）。履歴はid DESC、cursorも同じidで比較する。許可根拠は詳細のpermissionから取得し、履歴へ複製しない。種類はRECEIVED / APPROVED / REJECTED / WITHDRAWN / PERMISSION_GRANTED / PERMISSION_REVOKED / CORRECTION_RECEIVED / CORRECTION_LINKED。

### 冪等・競合・失敗

店舗＋現在の操作者ID＋dedupe_keyを全変更操作に共通の一意範囲とし、種類・対象ID・正規化された要求（元versionも含む）のfingerprintを保持する。別の操作・対象・内容・versionに同じキーを使うと409。要求本文のコピーは冪等台帳に保存しない。

現在の閲覧・操作授権を確認し、既存の同一成功要求を状態／version／受注の現時点資格検査より先に再生する。再生は履歴・汎用監査・許可・訂正を増やさず、元のoperationと現在の詳細を返す。後続の取り下げや許可撤回を古い成功応答で戻さない。失敗した操作のキーは成功記録として消費せず、権限回復後等に同じ要求を試せる。

初回変更では対象行のロック＋version照合で、承認と取り下げ、許可取得と撤回、訂正を直列化する。別キーでもversionが古い場合は409。承認済みへの別承認要求、撤回済みの別撤回要求など、反復遷移も409。同キーの並行初回は1回だけ確定し、片方がその成功要求を再生する。単純な「既に同じstatusだから成功」は採用しない。

標準失敗は401未認証、403現在権限・店舗集合・主体不適格、404口コミ／関連受注の不存在または他店、400形式・日時・文字数・関連受注の未完了／無効化、409版・キー・状態・訂正先競合。共通error/detailsを使い、入力値や内部例外をそのままメッセージへ載せない。読取の不存在と権限不成立の既存方針を混同しない。

## モジュール契約と保存

review モジュールが口コミ、許可取得・撤回、専用履歴、変更要求の冪等を所有する。通知・transport・task・schedulerは変更も再実装もしない。監査は既存のaudit::recordingを同じ取引で使用し、既存の本人・緊急昇格記録の仕組みを利用する。

order::review-origin を追加する。ReviewOriginLookup.find(orderId) は現在のStoreContextと機構的な店舗filterを使用し、Optional<ReviewOriginFacts>を返す。factsはorder_id、order_version、status、completion_invalidatedだけ。不存在／他店は空で、review側が404に写す。review側がCOMPLETEDかつ未無効化を要求し、確認時刻・版を保存する。読み取った版に対する受付時の関連検証であり、顧客本人性・公開許可・後日の受注状態を保証しない。報酬や連絡先を含む既存の完了結果DTOは利用しない。この条件は今回承認された契約であり、旧来の業務裁定とは区別する。orderへの変更は限定した読取契約とその試験に限定し、並行作業中のOrderServiceの監査入口には触れない。

review::publication は ReviewPublication.current(storeId, reviewIds) を持ち、重複除去済みIDを最大100件受ける。明示storeIdが検証済みStoreContextと一致することを要求し、現在の承認・有効許可・未取り下げを確認し、適格なものだけを返す。結果は ApprovedReviewProjection{review_id、review_version、display_name（null可）、body} のID別Map。範囲外・不存在・不適格なものは欠落する。店を指定できない呼出しは失敗させる。他店IDの混在は当該項目を除外し、店の不一致・未設定は失敗させる。上限超過や不正IDは呼出し側の誤りとして拒否し、キャッシュや永続したeligibleフラグは用いない。

この投影に許可根拠、許可日時、判断理由、受注ID、顧客ID、記録者を含めない。匿名HTTP端点を新設せず、#392 が自身の公開属性を通したID集合について毎回この契約を呼ぶ。未統合の間はモジュールの契約試験で閉じる。将来の公開側は独立した公開スイッチ・公開期間・表示順を適用し、撤回時のキャッシュ失効または都度再照会を実装する。取得済みの本文を恒久保持して承認・許可撤回を迂回してはならない。読取スナップショット以降の同時変更まで原子的に外部表示へ同期するという保証はしない。

正本・許可・履歴・冪等記録は全てstoreFilterとstoreSetFilterを持つ。受注参照・訂正参照は同店をDB制約でも保証し、履歴と許可を操作側の削除で失わない。口コミの物理削除APIは作らない。前公開baselineに終端形を追加し、FKのonDeleteを明示する。訂正元の一意性、店舗＋操作者＋キー、許可の一受付一記録を制約で固定する。

共有変更は三権限と対応する型・試験、baselineのinclude、CRMの「口コミ管理」メニューのみ。現在39権限からこのブランチで42、採用の私有添付二権限を取り込む場合44を想定し、実際の合流時に一覧で検証する。基点のbaselineには16が未使用で、通知は10、採用は15を使う。主タスクの調整により口コミには16、メニュー順はCRMの3を割り当てる。実装前に最新baselineを再確認し、他分支のWIPを複製しない。個人情報の保持期限は未裁定で、本切片から削除ジョブや無期限保持方針の承認を導かない。

## 管理画面

店舗CRMの口コミ管理に一覧、手動受付、詳細、判断、許可記録／撤回、取り下げ、訂正再受付、履歴を揃える。「スタッフによる記録」を一覧と詳細で常時示し、「本人確認済み」「購入確認済み」は表示しない。許可と承認は別の状態表示とし、「承認済み」を「公開済み」と表示しない。

VIEWのみは読取専用。MANAGEとMODERATEは別々に操作を表示する。許可なし承認の確認では「内部承認のみ。公開には別途許可と公開設定が必要」と明示する。許可撤回と全体取り下げを別操作にし、取り下げ済みでも許可撤回が可能な場合だけその操作を残す。不可逆操作は理由付き確認を経る。

訂正フォームは旧本文等を明示的な入力初期値として提示するが、許可をコピーせず、受注指定の追加権限を再検証する。保存時に旧記録が取り下げられ、新規受付が再承認を要することを確認画面に出す。許可記録の入力が揃っていないことを、内部承認を禁止する理由にしない。

一覧は適用済み検索・page条件と取得中の条件を区別する。店舗変更時は古い一覧・詳細・応答を捨てる。401/403、404、409、通信結果不明を区別し、404は操作を除去して一覧へ戻る。409は入力を黙って適用し直さず、最新状態を確認して新しい操作として再決定する。

送信中は連打を防ぎ、結果不明の場合は同じキーと正規化前の入力を画面のメモリに保持し、同じ要求を再照会する導線を出す。未確定のまま内容を変えて別要求として再送しない。フォームを閉じても同じ店舗画面が生きている間は未確定要求を保持する。本文・許可根拠はlocalStorage／sessionStorageへ保存しない。全画面離脱後の自動再送は行わず、一覧確認を経て担当者が判断する。

関連受注の不存在404は新規受付のフォームを維持する。訂正の404では元口コミを再照会し、その照会も404の場合だけ口コミ失効として扱う。それ以外は入力本文・日時・受注指定・訂正理由をメモリ内で復元する。結果不明の間は同じ要求キーで結果を確認し、404で未完了が確定した後に入力を修正して送る場合は新しいキーを使う。

## 試験面と受け入れ

主な試験面は認証付きHTTPと管理画面の操作とする。機構的な店舗隔離・Liquibase制約・ロック・一意性・audit原子性は隔離PostgreSQLと実applicationを通して検証する。orderとreviewの新しいモジュール契約は実店舗文脈から確認し、公開側を装う成功sinkや公開ページを追加しない。

- 無許可PENDINGを内部承認できるが投影は空、許可取得後は本文・表示名だけが得られる。
- 許可撤回は内部承認を残し、次の投影読取りで除外する。WITHDRAWNも同様で、別の公開属性を操作しても復活しない。
- 許可撤回済み・取り下げ済み・却下済みへの不正遷移、同キー再生、別キー反復、同キー異内容、後続変更後の再生を確認する。
- 訂正時に旧投影が消え、新記録は許可・承認を引き継がない。二重訂正・跨店訂正・保存／監査失敗時の原子性を確認する。
- 任意受注なし、正しい同店完了、他店、不在、未完了、取消、完了無効化、ORDER_MANAGEなしを確認する。いずれも本人確認済みの表示・属性は生じない。
- 並行操作が1回の確定履歴に収束し、許可撤回と承認、取り下げと承認、同キー初回競争で結果が失われないことを実DBで確認する。
- 検索対象、LIKE文字の扱い、同時刻のID順、全sort、ページ境界、履歴cursor、型による機密項目の除外を確認する。
- 不正入力のログと汎用auditに本文・根拠・理由を含めないことを確認する。公開投影は意図した本文・表示名だけを持ち、根拠・受注/customer IDを含めない。保護された詳細本文の表示は意図した動作として区別する。
- 現在授権、無効化、別店舗、旧レスポンス、二重操作、通信不明→再照会、409/404復帰、キーボード、390pxと明暗テーマを確認する。
- 最終はTaskfile lint/test/build、独立したcode-review、専用タグ／stackのE2E。重E2Eは主タスクと順番を調整する。仕様段階では起動しない。

## 対象外

公開ページ・公開三要素、アンケート、顧客本人の自助投稿、匿名公開受付、招待URL・秘密token、通知や連絡先への接続、実外部送信、販促判断、ポイント等の報酬、添付原資料、保持期限の自動適用は含めない。本切片を完成させても #394 全体や #392 を閉じない。
