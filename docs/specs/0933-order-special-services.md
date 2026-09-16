# 受諾済み特殊サービスの受注適用・拒否修復 — API 契約

状態: **承認済み・実装検証済み（2026-09-15）。**

対象: [#933](https://github.com/kanghouchao/Kizuna/issues/933)。正本は [#384](https://github.com/kanghouchao/Kizuna/issues/384)、裁定索引は [#919](https://github.com/kanghouchao/Kizuna/issues/919)。調査基点は `d4a2fea4`。依存 #930・#931 は CLOSED であり、本人意思・設定版本・コース試算の既存基盤を利用する。

## 共通規約

- JSON は snake_case。新規 ID は string、版は整数、金額は整数円、日時はタイムゾーン付き ISO 8601。以下で「任意」と記さない字段は必須。既存字段の型・必須性は変更を明記したもの以外維持する。
- M = ORDER_MANAGE、C = ORDER_MANAGE と ORDER_CORRECT、P = ORDER_SET_MANAGE。店舗端点は認証・店舗コンソール資格・`X-Role: store`・`X-Store-ID`・店舗作用域を必要とする。サービス設定権限は要求しない。平台は授権店舗集合の参照だけを許す。
- 共通失敗は 401（未認証）、403（権限・店舗資格不足）、400（不正入力・不正遷移・負の合計）、404（対象なし・作用域外）、409（受注版・確認値・提供資格の競合）。外殻は `{error: string, details?: Record<string,string>}`。
- 新設 Page は `page?: integer = 0`、`size?: integer = 20`（1..100）。新設 CursorPage は `cursor?: string`、`size?: integer = 20`（1..100）、応答 `{content: T[], next_cursor?: string}`。不正範囲・cursor は 400。単件・試算・保存はページングなし。
- 新設の `special-service-candidates` は受注 ID として予約する。POST 試算では永続書込みを行わない。

## 追加・変更する端点

| メソッド・パス | 授権 | 要求・変更 | 成功応答 |
| --- | --- | --- | --- |
| GET `/store/orders/special-service-candidates` | M | `cast_id: string`、`search?: string`、page、size | 200 Page<SpecialServiceCandidate> |
| GET `/store/orders/{id}/special-service-revisions` | C | `search?: string`、cursor、size | 200 CursorPage<SpecialServiceRevision> |
| GET `/store/orders/{id}/special-service-events` | M | cursor、size | 200 CursorPage<SpecialServiceEvent> |
| POST `/store/orders/preview` | M | CreateInput に特殊サービス選択を追加 | 200 Preview |
| POST `/store/orders` | M | CreateInput と必須 confirmation_token | 201 OrderResponse |
| POST `/store/order-applications/{id}/confirmation-preview` | M | ConfirmationInput に同じ選択を追加 | 200 Preview |
| POST `/store/order-applications/{id}/confirmation` | M | ConfirmationInput と必須 confirmation_token | 201 OrderResponse |
| POST `/store/orders/{id}/preview` | M | UpdateInput に特殊サービス編集を追加 | 200 Preview |
| PUT `/store/orders/{id}` | M | UpdateInput と必須 confirmation_token | 200 OrderWorkQueueResponse |
| POST `/store/orders/{id}/start` | M | 新設 StartRequest | 200 OrderResponse |
| POST `/store/orders/{id}/completion-preview` | M | 既存 CompletionInput。特殊サービスは保存済み快照を保持 | 200 Preview |
| POST `/store/orders/{id}/completion` | M | 既存 CompletionInput と必須 confirmation_token。拒否未処理なら 409 | 200 既存 OrderCompletionResponse |
| POST `/store/orders/{id}/correction-preview` | C | CorrectionInput に歴史版本選択を追加 | 200 Preview |
| POST `/store/orders/{id}/corrections` | C | CorrectionInput と必須 confirmation_token | 201 CorrectionResult |
| POST `/store/orders/{id}/cancellation` | M | 既存要求・応答。IN_SERVICE からも取消可能 | 204 |
| GET `/store/orders` | M | 既存検索・Page を維持 | 200 Page<OrderSummaryResponse>、要対応字段追加 |
| GET `/store/orders/work-queue` | M | 既存検索・CursorPage を維持、status に IN_SERVICE を追加 | 200 CursorPage<OrderWorkQueueResponse>、要対応字段追加 |
| GET `/store/orders/archive` | M | 既存検索・Page を維持 | 200 Page<OrderArchiveResponse>、要対応字段追加 |
| GET `/store/orders/{id}` | M | 既存 | 200 OrderResponse、特殊サービス詳細追加 |
| GET `/platform/orders` | P | 既存検索・Page を維持 | 200 Page<PlatformOrderResponse>、要対応字段追加 |
| PUT `/platform/me/service-conditions/{id}/consent?store_id={storeId}` | ROLE_CAST | 既存 OwnConsentRequest。拒否時に受注への作用を同一トランザクションで記録 | 200 既存 OwnServiceConditionSummary |

`start` はサービス開始という名詞の単一操作資源。本人意思 API は既存どおり認証主体から本人・有効在籍を解決し、代理拒否入口を設けない。本人 API の要求・応答型は変更しない。

## 要求型と置換規則

### 作成・申請確定

CreateInput / ConfirmationInput に `special_service_ids?: string[]` を追加する。省略/null/空配列は未選択。同一 ID の重複・null 要素は 400。非空の場合は担当 cast_id が必須で、その店舗の当該在籍における現在条件への受諾が必要。

### 通常編集と修復

UpdateInput に `special_service_ids?: string[]` を追加する。省略/null は既存項目保持、空配列は全除去、指定時は残す項目と新規採用項目の全量。同じ担当・同じ service_id を残した項目は旧快照を保持し、新規項目だけ現在条件と受諾を照合する。同じ項目の条件を採り直す操作は一度除去して保存した後に選択する。

担当変更時は特殊サービスを全除去する。変更要求の special_service_ids は省略/null/空配列のみを許し、非空は 400。試算で除去と費用・報酬の差を明示し、保存後に新担当の候補から選び直す。コース・延長・加算・割引は保持し、合計が負なら 400。担当変更だけを理由に割引を調整しない。

拒否項目を残した通常編集も許すが要対応は残る。除去・別項目への改選・担当変更が成功したときだけ該当の拒否記録を処置済みにする。本人の後日の再受諾だけでは処置済みにしない。

### 開始・完了

StartRequest = `{expected_version: integer >= 0, reason: string (1..500文字)}`。開始日時はサーバ時刻で確定し、操作者と理由を記録する。

CONFIRMED → IN_SERVICE を追加する。二度目の開始と終端からの開始は 400、古い版は 409。未処理拒否がある開始・完了は `409 details.special_services` で修復を案内する。拒否は状態を戻さない。完了は CONFIRMED / IN_SERVICE から可能とし、開始記録のない会計も許す。取消は両状態から可能で、既存の理由必須・二重取消拒否を維持する。

完了の場では特殊サービスを編集せず、必要な修復は通常編集で行って再試算する。完了済み・取消済みは本人拒否の作用対象に含めない。

### 完了後訂正

CorrectionInput に `special_service_revision_ids?: string[]` を追加する。省略/null は快照保持、空配列は全除去、指定時は特殊サービスの全量。同一 service_id の複数版本は 400。既存と同じ revision_id は採用根拠も保持し、新しく指定した版本は HISTORICAL_CORRECTION として採用する。

同店舗の SPECIAL_SERVICE の CREATED/UPDATED 版本だけを許す。現在の削除・価格・受諾状態を理由に拒否しない。担当等の既存凍結範囲は維持し、担当未設定で特殊サービスを追加する訂正は 400。reason・expected_version・confirmation_token は既存どおり必須。

### 明細の手入力経路

特殊サービスは既存 OPTION を置き換える SPECIAL_SERVICE 種別で生成する。OPTION を enum・フォーム・baseline から撤去し、直送は 400。SPECIAL_SERVICE の name/amount/remuneration 直送も 400。通常明細入力の置換対象から特殊サービスを除外し、専用 ID 選択からのみ生成する。BASE_COURSE・ポイント専有行の保護を維持する。

EXTENSION / SURCHARGE / DISCOUNT / CREDIT_SURCHARGE は [#932 の契約](0932-order-extension-surcharge-discount.md) に従う。MANUAL_ADJUST は撤去済みであり、加算は設定版本から選択する。これらを特殊サービス選択の代用として UI に案内しない。

## 応答型

### 候補と歴史版本

SpecialServiceCandidate の字段はすべて必須:

`{service_id: string, revision_id: string, revision_number: integer, terms_version: integer, name: string, charge_type: "PAID"|"FREE", price: integer, remuneration: integer, consent_event_id: string, consent_version: integer}`。

現在の有効在籍が現在条件を ACCEPTED とした項目のみ返す。候補条件と返却する意思イベント・意思版は一つの問い合わせで取得し、同じ断面を参照する。未受諾・拒否・再受諾待ち・設定削除は新規候補に含めない。公開状態は参照しない。search は名称部分一致、順序は name ASC, service_id ASC。別店舗・存在しない在籍は 404、有効資格のない在籍は 400。

SpecialServiceRevision は候補から consent_event_id / consent_version を除き、`occurred_at: datetime`、`service_deleted: boolean` を加えた型。対象受注と同店舗の特殊サービス版本を返す。順序と cursor は occurred_at DESC, revision_id DESC。DELETED 版本は除外し、削除された設定の過去の条件は返す。

### 採用項目と要対応

SpecialServiceSnapshot は候補の設定字段に以下を追加する:

- `adoption_basis: "ACCEPTED_TERMS"|"HISTORICAL_CORRECTION"`
- `adopted_at: datetime`
- `enrollment_id: string`
- `consent_event_id?: string`、`consent_version?: integer`。ACCEPTED_TERMS では必須、受諾根拠を伴わない歴史訂正では省略。
- `requires_attention: boolean`
- `current_consent_status?: "NOT_ACCEPTED"|"ACCEPTED"|"REJECTED"|"RECONFIRMATION_REQUIRED"`。未完了の内部詳細のみ。在籍が ENROLLED でなければ NOT_ACCEPTED とし、採用時の約定と受諾根拠は保持する。終端では省略し、過去の約定から現在の提供資格を誤認させない。

OrderResponse に `special_services: SpecialServiceSnapshot[]`（常に配列）、`requires_attention: boolean`、`unresolved_special_service_count: integer >= 0`、`started_at?: datetime` を追加する。一覧用 OrderSummaryResponse / OrderWorkQueueResponse / OrderArchiveResponse / PlatformOrderResponse には同じ要対応 boolean と件数、started_at を追加する。要対応件数は未処理の拒否イベント数ではなく、対象の特殊サービス ID の重複を除いた項目数とする。同一項目への再受諾・再拒否で増やさず、拒否と処置の履歴自体は各イベントを保持する。全受注 status の型は IN_SERVICE を含む。明細を既に返す応答では SPECIAL_SERVICE 行に `service_id: string`、`remuneration: integer` を追加する。

Preview に `special_services: SpecialServiceSnapshot[]`（保存前なので adopted_at のみ省略可能）、requires_attention と unresolved_special_service_count を追加する。fee_lines は特殊サービス価格・報酬を含む。時間・数量の字段は増やさない。無料は price = remuneration = 0。

CorrectionResult に `previous_special_services: SpecialServiceSnapshot[]`、`special_services: SpecialServiceSnapshot[]` を追加する。既存の correction_id と前後費用・コースは維持する。

本人・会員・公開の受注応答には受諾根拠・内部要対応・報酬を追加しない。既存応答が status を返す場合だけ IN_SERVICE の表示を追加する。

### 拒否・処置履歴

SpecialServiceEvent = `{id: string, occurred_at: datetime, actor_id: string, kind: "REJECTED"|"RESOLVED", consent_event_id: string, before: SpecialServiceSnapshot[], after: SpecialServiceSnapshot[], previous_total_fee: integer, total_fee: integer, resolution?: "REMOVED"|"RESELECTED"|"CAST_CHANGED", rejection_event_id?: string}`。

resolution / rejection_event_id は RESOLVED のとき必須、REJECTED では省略。同じ修復が複数の拒否を処置する場合は拒否ごとに記録し、同じトランザクションで保存する。順序と cursor は occurred_at DESC, id DESC。履歴は内部閲覧のみで、本人への応答に注文・顧客情報を追加しない。

## 再確認・競合の不変量

- 既存確認トークンに選択項目の版本・受諾イベント・担当資格・未処理拒否と試算結果を含める。フォーム変更時は破棄する。既存項目の条件改定による再受諾待ちは旧快照を変えず、その事実だけで旧約定を無効化しない。
- 新規採用で試算後に受諾・在籍・設定が変わった保存は 409、details.confirmation_token で再試算を要求する。最初から存在しない候補は 404、存在するが不適格な選択は 400。未処理拒否による開始・完了停止は details.special_services、古い受注版は details.expected_version。
- UI は前回試算を残し、項目・費用・報酬・担当・受諾状態の差を提示する。保存の自動再送はしない。拒否と再受諾待ちは別表示とし、一覧・詳細の要対応から編集へ到達できる。取得失敗を候補ゼロとして扱わない。
- サービス改定・削除・本人拒否・在籍変更で既に使う店舗行ロックを最初に取得する。保存・開始・完了・修復・取消も同じ順序で取り、対象受注をロックして版を照合する。新規作成・申請確定も含めて一貫した順序にする。
- 本人拒否の意思イベントと未完了受注の拒否記録を同期・同一トランザクションで保存する。非同期イベントの遅延で開始・完了が通る隙間を作らない。拒否を先に確定した場合は進行拒否、完了が先なら完了受注を変更しない。
- 詳細取得は集約・表示用 projection・明細・現在資格・要対応件数を REPEATABLE READ の一断面から組む。候補の資格検証・内容・総数と、歴史候補の版本・削除状態も各読み取りトランザクション内で同じ断面に揃える。要求間の更新は次回取得に反映する。
- 開始は版照合後に集約の状態・入力を検証し、未処理拒否を検証する。終端・開始済みからの開始は 400、開始可能な受注の未処理拒否は 409 とし、拒否した操作の開始記録・状態・版は保存しない。
- 負の合計・版競合・途中失敗では受注・明細・拒否/処置履歴・本人意思履歴・顧客・申請・ポイントの部分成功を残さない。

初回の不正な特殊サービス選択は 400、存在しない ID は 404 を維持する。保存時の提供資格エラーを確認競合にするのは、同じ操作・受注・店舗・操作者・入力の成功した試算を署名で確認できる場合に限る。保存の確定には、入力証明に加えて試算結果を含む確認値全体の一致を必要とする。

申請確定で指名を解除するときは特殊サービスの選択を即時に空にし、候補を操作できない表示にする。解除を戻しても旧選択を復元せず、表示中の選択値を試算・保存へそのまま送る。

開始の expected_version 競合では詳細を再取得し、未保存のフォーム入力と開始理由を保持して新しい版へ更新する。利用者へ再確認を案内し、開始を自動再送しない。

## baseline の保存設計

- t_orders に started_at（TIMESTAMPTZ、nullable）、started_by（BIGINT、nullable）、start_reason（VARCHAR(500)、nullable）を追加し、三字段の同時有無を CHECK で保証する。started_by は t_users への NO ACTION。status の CHECK に IN_SERVICE を追加する。
- t_order_special_services を追加する。id（VARCHAR PK）、store_id（BIGINT）、order_id / enrollment_id / service_id / revision_id（VARCHAR）、revision_number / terms_version（BIGINT）、name（VARCHAR(255)）、charge_type（VARCHAR）、price / remuneration（INTEGER）、adoption_basis（VARCHAR）、adopted_at（TIMESTAMPTZ）、consent_event_id（VARCHAR nullable）、consent_version（BIGINT nullable）。nullable とした列以外 NOT NULL。
- 一受注・一項目の UNIQUE(order_id, service_id)。同店舗 FK として (order_id, store_id)、(enrollment_id, store_id)、(service_id, store_id)、(revision_id, store_id)、(consent_event_id, store_id) を使用する。必要な参照先複合一意制約を baseline に定義する。すべて ON DELETE NO ACTION とし、参照先を削除して証跡を失わせない。
- price >= 0、0 <= remuneration <= price、FREE なら双方 0、受諾採用では意思根拠必須を CHECK で固定する。数量・時間列は持たない。担当と enrollment_id、版本と service_id、採用条件と生成明細の一致は集約とトランザクションで保証し、認証済み HTTP テストで固定する。
- t_order_fee_lines の service_id・revision_id・revision_number・adoption_basis・adopted_at と非 null remuneration はコース・加算と共有する。SPECIAL_SERVICE の採用根拠は ACCEPTED_TERMS または HISTORICAL_CORRECTION とし、一受注・一項目部分 UNIQUE を設ける。対応する応答の adoption_basis にも ACCEPTED_TERMS を含める。受諾証跡の正本は特殊サービス快照に置く。既存 BASE_COURSE とポイント専有の制約を維持し、OPTION を CHECK から除く。
- t_order_special_service_events に id、store_id、order_id、enrollment_id、service_id、consent_event_id、actor_id、occurred_at、kind、before_snapshot / after_snapshot（JSONB）、previous_total_fee / total_fee を NOT NULL で保存する。resolution と rejection_event_id は nullable で、RESOLVED との対応を CHECK。JSONB は各項目の採用快照と当時の要対応状態を保存する。
- 履歴の受注・在籍・設定・意思イベントには店舗付き NO ACTION FK、actor_id は t_users への NO ACTION FK、rejection_event_id は同店舗の拒否履歴への NO ACTION FK。一拒否の処置は UNIQUE(rejection_event_id)。同一受注・同一意思拒否の重複を部分 UNIQUE で防ぐ。
- 履歴読取用 INDEX(store_id, order_id, occurred_at DESC, id DESC)、拒否伝播用 INDEX(store_id, enrollment_id, service_id)、版本・意思イベント参照用索引を設ける。未処理拒否は REJECTED のうち RESOLVED の参照がないものから導出する。
- 既存 t_order_corrections に訂正前の特殊サービス快照 JSONB（NOT NULL、配列）を加える。通常除去後も拒否・処置・訂正履歴の快照は残る。受注単件削除の入口を追加しない。店舗物理削除はサービス履歴の既存 NO ACTION 方針を維持する。

## 承認後の実装・検証

認証済み HTTP + 実 PostgreSQL で受諾候補、拒否伝播、開始・完了阻断、修復、旧約定保持、歴史訂正、隔離・権限、競合と履歴の原子性を検証する。金額境界は集約テスト、再確認・権限・状態表示・店舗切替は UI 操作テスト、主要導線は日本語 Gherkin E2E に置く。

ADR 0013 / 0017 / 0018 / 0019 / 0027 と CONTEXT は実装に合わせて更新する。最終検証は task lint・task test・task build・task e2e を実行し exit code で判定、code-review を経て実装コミットを行う。実装ブランチは `codex/issue-933-special-services`。


## 検証結果（2026-09-15）

- Taskfile の lint・test・build・e2e はすべて exit code 0。前端 1418 テスト、バックエンド統合 705 テスト、E2E 44 シナリオが成功した。
- 並行 worktree との衝突を避け、test / e2e は Taskfile の一時コピーで STACK_ID を `0c3f-933` に固定した。検証コマンドとサービス定義は変更していない。
- Standards / Spec の二軸レビューを実施し、非同期 UI・入力検証・更新応答反映、資格競合・拒否履歴・受諾差異表示の指摘を修正した。再レビューで残存指摘なし。

レビュー修正後は拒否反映を公開同期契約へ移し、反映失敗の共同ロールバックを追加検証した。バックエンドの Taskfile lint・test・build は exit code 0、統合 706 件が成功した。

## 追加検証（2026-09-16）

初回の不正選択と試算後競合、候補取得直後の拒否コミット、停止・退店後の現在資格、開始版競合からの再取得と未保存入力保持を回帰テストで検証した。

- Taskfile lint / build、前端 test、バックエンド test はすべて exit code 0。前端 1425 件、バックエンド統合 715 件が成功し、単体カバレッジゲートも通過した。
- Taskfile e2e は exit code 0、45 シナリオ成功（8.9 分）。
- Standards / Spec の再レビューは両軸とも残存指摘なし。


## 読み取りと状態遷移の横断確認

- 詳細・候補・歴史候補は一要求内の断面を固定する。作業キュー・アーカイブの既存 REPEATABLE READ と、試算・保存・修復・開始・完了の店舗ロックも確認した。拒否・処置履歴は保存済みの不変快照を返す。
- 詳細組立中の拒否／修復、資格照会後の拒否、歴史版本照会後の削除を別 HTTP 要求で確定し、応答全体が更新前の断面を保ち、次の取得で更新を読めることを回帰対象とする。
- 開始の版照合・状態違反・未処理拒否の優先順と、拒否した開始の状態・版・開始記録のロールバックを確認する。完了・取消の集約テストは CONFIRMED と IN_SERVICE の両方を対象とする。
- 集約・アプリケーション・HTTP 契約・前端 API・画面文言・CONTEXT・関連 ADR の状態説明を照合した。取消の直列化と二度目の 400、訂正における歴史版本の省略規則と前後快照も現行実装に揃えた。

横断修正後の最終検証は Taskfile lint / build、前端 test、バックエンド test、e2e がすべて exit code 0。前端 1425 件、バックエンド統合 722 件（特殊サービス 20 件）、E2E 45 シナリオ（9.0 分）が成功し、バックエンド単体・カバレッジゲートも通過した。Standards / Spec の再レビューは両軸とも残存指摘なし。

再受諾後の再拒否による件数と、申請確定時の指名解除を追加検証した。店舗詳細・一覧・作業キュー・平台一覧・試算の件数を照合し、解除および解除撤回後の表示・試算・保存値を UI テストで固定した。Taskfile lint / test / build / e2e はすべて exit code 0（前端 1427 件、バックエンド統合 722 件、E2E 45 シナリオ）。Standards / Spec の再レビューは両軸とも残存指摘なし。
