# 受注への設定コース適用 — API 契約

状態: **承認済み。**

対象: [#931](https://github.com/kanghouchao/Kizuna/issues/931)。業務規則の正本は [#384](https://github.com/kanghouchao/Kizuna/issues/384)。調査基点は `cebcd216`、実装基点は本人受諾を含む `4cf6a85a`。既存のサービス設定・歴史版本を再利用する。

## 共通規約

- JSON は snake_case。ID は文字列（既存の receptionist_id 等の数値 ID は現行型を維持）。金額は整数円、版は非負整数、日時は ISO 8601。
- 全店舗端点は認証、店舗コンソール資格、`X-Role: store`、`X-Store-ID` と店舗作用域の検証を要する。
- 以下の M は `ORDER_MANAGE`、C は `ORDER_MANAGE` と `ORDER_CORRECT` の両方。サービス設定の管理権限を受注操作に要求しない。
- 成功コードは表のとおり。共通失敗は 401（未認証）、403（資格・権限不足）、400（不正入力・状態遷移・負の合計）、404（対象なし・別店舗）、409（版・試算結果の競合）。
- エラー外殻は既存の `{error: string, details?: Record<string, string>}`。内部例外・SQL を返さない。
- Page は既存の Spring Page。新しい一覧は `page?: integer = 0`、`size?: integer = 20`（1..100）、不正範囲は 400。
- CursorPage は `{content: T[], next_cursor?: string}`。`cursor?: string`、`size?: integer = 20`（1..100）。不正 cursor は 400。末尾では next_cursor を省略。
- 表にある試算・保存・単件照会はページングなし。POST 試算は読み取り専用で、顧客・受注・申請・台帳・確認用レコードを作成しない。

## 端点一覧

| 操作 | パス | 権限 | 要求 | 成功応答 |
| --- | --- | --- | --- | --- |
| GET（新設） | `/store/orders/course-candidates` | M | `search?: string`、page、size | 200 Page<CourseCandidate> |
| GET（新設） | `/store/orders/{id}/course-revisions` | C | `search?: string`、cursor、size | 200 CursorPage<CourseRevision> |
| POST（新設） | `/store/orders/preview` | M | CreateInput | 200 Preview |
| POST（変更） | `/store/orders` | M | CreateInput + confirmation_token | 201 OrderResponse（id を含む） |
| POST（新設） | `/store/order-applications/{id}/confirmation-preview` | M | ConfirmationInput | 200 Preview |
| POST（変更） | `/store/order-applications/{id}/confirmation` | M | ConfirmationInput + confirmation_token | 201 OrderResponse |
| POST（新設） | `/store/orders/{id}/preview` | M | UpdateInput | 200 Preview |
| PUT（変更） | `/store/orders/{id}` | M | UpdateInput + confirmation_token | 200 OrderWorkQueueResponse |
| POST（置換） | `/store/orders/{id}/completion-preview` | M | CompletionInput | 200 Preview |
| POST（変更） | `/store/orders/{id}/completion` | M | CompletionInput + confirmation_token | 200 既存 OrderCompletionResponse |
| POST（新設） | `/store/orders/{id}/correction-preview` | C | CorrectionInput | 200 Preview |
| POST（変更） | `/store/orders/{id}/corrections` | C | CorrectionInput + confirmation_token | 201 CorrectionResult |

GET `/store/orders/{id}/completion-preview?total_fee=...` は撤去し、任意の総額だけを受け付ける試算経路を残さない。新設の `preview` と `course-candidates` は ID として予約する。

現在の POST `/platform/orders` は店舗実務の作成経路であり、店舗専用の選択・確認を迂回するため、本提案では撤去する。GET `/platform/orders` は `ORDER_SET_MANAGE` と授権店舗集合のまま維持する。通常 HQ 向けの試算・設定選択・店舗作成経路は追加しない。この撤去も今回の契約承認対象に含む。

## 要求型

既存 DTO のうち以下で変更を明記しない字段は、型・必須性・長さ制限・null の意味を維持する。正本は `backend/src/main/java/com/kizuna/order/api/dto/` の各 Request。互換用の旧字段は残さず、未知字段として 400 にする。

| 型 | 既存 DTO | 変更 |
| --- | --- | --- |
| CreateInput | OrderCreateRequest | course_name/course_minutes を撤去。`course_id: string` 必須。fee_lines は任意（省略時は他項目なし）。 |
| ConfirmationInput | OrderApplicationConfirmationRequest | course_name/course_minutes を撤去。`course_id: string` 必須。`fee_lines?: EditableFeeLine[]` を追加（省略時は他項目なし）。 |
| UpdateInput | OrderUpdateRequest | course_name/course_minutes を撤去。`course_id?: string`、`expected_version: integer` 必須を追加。course_id 省略/null は既存快照保持、指定時はそのコースの現在条件を明示採用。同じ ID の指定でも現在条件を採用する。 |
| CompletionInput | OrderCompletionRequest | course_name を撤去。expected_version・fee_lines 必須、use_points 任意（指定時 1 以上）を維持。コース変更は先に通常編集で行う。 |
| CorrectionInput | OrderCorrectionRequest | course_name/course_minutes を撤去。`course_revision_id?: string` を追加。省略/null は快照保持、指定時は歴史版本の条件を採用。expected_version、reason（1..500 文字）、fee_lines は必須。実績時刻・延長分数の省略/null は従来どおり値なしへの訂正。 |

`confirmation_token: string` は保存時必須、試算入力には存在しない。トークンは直前に表示した Preview の値を渡す。フォーム変更時には破棄して再試算する。

`EditableFeeLine = {kind: enum, name?: string (最大255文字), amount: integer}`。入力可能な種別は既存の EXTENSION / OPTION / SURCHARGE / DISCOUNT / MANUAL_ADJUST / CREDIT_SURCHARGE。金額の符号・名称要件は既存規則を維持する。BASE_COURSE と POINT_REDEMPTION の直送は 400。BASE_COURSE はコース条件から一行だけ生成し、ポイント専有行は既存機構で保持・生成する。

fee_lines の置換対象は入力可能な行だけ。通常編集の省略/null は既存の他項目を保持し、空配列は他項目のみ削除。完了・訂正では必須。コース変更だけで他項目を最新設定へ変えない。今回、コース以外の手入力経路を別のサービス適用へ置き換えない。

## 応答型

### コース選択

CourseCandidate の全字段は必須:

| 字段 | 型 | 意味 |
| --- | --- | --- |
| service_id | string | 設定 ID |
| revision_id | string | 採用する ServiceRevision の ID |
| revision_number | integer | 設定の版本番号 |
| name | string | コース名 |
| duration_minutes | integer > 0 | 所要時間 |
| price | integer >= 0 | 顧客価格 |
| remuneration | integer >= 0 | コース固定報酬。price 以下 |

候補は現在有効な COURSE のみ。search は名称の部分一致、順序は name ASC, service_id ASC。権限のないサービス種別や操作者情報を含めない。

CourseRevision は上記に `occurred_at: datetime` と `service_deleted: boolean` を追加した型。対象受注と同店舗の全コースの CREATED/UPDATED 版本を返す。削除済み設定も対象。DELETED は新しい提供条件ではないので候補から除く。順序は occurred_at DESC, revision_id DESC、cursor もこの組を使う。選択時は当時の条件を採り、現在の価格や削除状態へ置き換えない。

### 採用済みコース

CourseSnapshot は CourseCandidate の全字段に以下を加える:

- `adoption_basis: "CURRENT_SETTING" | "HISTORICAL_CORRECTION"`（必須）
- `adopted_at: datetime`（必須、保存時に確定）

`OrderResponse`、`OrderWorkQueueResponse`、`OrderArchiveResponse`、`OrderSummaryResponse`、`PlatformOrderResponse` に `course: CourseSnapshot` を必須で追加する。既存の course_name/course_minutes は course.name/course.duration_minutes へ統一して撤去する。明細の BASE_COURSE に `remuneration: integer` を追加する。他種別ではこの字段を省略し、未実装の他項目報酬を 0 と表示しない。本人・公開応答に報酬や採用根拠を追加しない。

### Preview

| 字段 | 型・必須性 | 意味 |
| --- | --- | --- |
| confirmation_token | string、必須 | 対象・入力・計算結果を結ぶ改変検知付き確認値 |
| course | CourseCandidate + adoption_basis、必須 | 採用予定の条件。保存前なので adopted_at は含めない |
| fee_lines | OrderFeeLineResponse[]、必須 | 保存予定の全明細。BASE_COURSE のみ remuneration を付加 |
| total_fee | integer >= 0、必須 | ポイント適用後の顧客費用 |
| points | PointsPreview、完了試算だけ必須、他では省略 | 会計時のポイント結果 |

PointsPreview: `member_linked: boolean`、`usage_unit: integer`、`use_points: integer >= 0`、`grant_points: integer >= 0` は必須。`point_balance: integer` と `member_code: string` は会員関連がある場合のみ返す。残高は参考表示であり、残高だけの変化で結果が不変なら再確認を要求しない。利用資格不足・残高不足は拒否する。

CorrectionResult は `correction_id: string`、`previous_total_fee: integer`、`total_fee: integer`、`previous_course: CourseSnapshot`、`course: CourseSnapshot`（全て必須）。今回、全種別の受注変更履歴一覧は新設しない。

## 再確認と同時操作

- 試算と保存は同じ計算処理を利用する。確認値はサーバ署名付きとし、操作者・店舗・操作・対象 ID・正規化した入力・採用版本・計算結果を結ぶ。別の入力や対象への使い回し、改変は受け付けない。
- 保存時は対象受注/申請と採用する現在設定を同一トランザクション内でロックし、設定改定・削除と直列化する。サービス側が既に使う設定行ロックに合わせる。履歴版本は不変で、設定削除後も参照可能。
- expected_version は完了・訂正で維持し、通常編集にも追加する。試算後に別の操作が受注を変えた場合、未変更快照を古い画面で上書きしない。
- 変更を指定しないコースは保存済み快照を読む。無関係な設定改定・削除を確認値へ含めない。未来営業日も操作時の設定を採る。
- 会員帰属・利用資格・付与結果も完了の再計算対象。結果に影響しない残高変動だけでは競合にしない。顧客解決・申請確定の資格も保存時に再検証し、試算時の顧客作成などは行わない。
- 再計算の差異は 409、`details.confirmation_token = "再試算して変更内容を確認してください"` を返す。古い受注版は `details.expected_version` で識別する。設定削除で選択不可になった場合も試算済み保存は 409、最初の試算で存在しない候補を指定した場合は 404。
- UI は旧試算を保持して再試算し、コース名・時間・価格・報酬・請求・ポイントの差異を並べる。自動再送しない。候補消失・過大割引等で試算できない場合は理由を表示し、再選択・修正後に確認する。
- 競合時は受注・申請・顧客・明細・訂正履歴・ポイントを一切部分保存しない。単純な読取→照合だけで済ませず、関連書込み経路も含めてロック境界を検証する。

## スキーマと不変量

- pre-launch baseline を直接更新。新しい移行経路は作らない。
- t_orders に course_service_id（VARCHAR、NOT NULL）、course_revision_number（BIGINT、NOT NULL）、course_revision_id（VARCHAR、NOT NULL）、course_price（INTEGER）、course_remuneration（INTEGER）、course_adoption_basis（VARCHAR）、course_adopted_at（TIMESTAMP WITH TIME ZONE）を追加。既存 course_name/course_minutes は快照の保存列として維持し NOT NULL とする。
- t_service_revisions の既存 `(id, store_id)` 一意制約を再利用し、受注の `(course_revision_id, store_id)` から複合 FK（ON DELETE NO ACTION）を張る。同店舗の版本だけを参照する。course_revision_id の参照索引を追加する。
- コース条件は価格 >= 0、報酬 >= 0、報酬 <= 価格、分数 > 0。一受注一 BASE_COURSE の部分一意索引を設ける。コース快照と BASE_COURSE の一致は集約が同時生成し、全保存経路のテストで固定する。
- 既存 t_order_corrections に訂正前 course_service_id・course_revision_number・course_revision_id・course_price・course_remuneration・course_adoption_basis・course_adopted_at を追加し、同じ型と店舗付き FK を使う。既存の訂正前名称・時間・全明細快照と合わせて過去を復元する。訂正理由・操作者・日時・訂正 ID は既存基盤を使う。
- サービス設定は既存の論理削除を維持する。設定改定・削除から受注快照や訂正履歴を書き換えない。サービス設定を持つ店舗の物理削除は既存の NO ACTION 制約を維持する。受注と版本の店舗付き参照を実 PostgreSQL で確認する。

## 実装後の検証

- 認証済み HTTP + 実 PostgreSQL: 作成と申請確定、店舗隔離、候補・歴史版本の権限、削除後の快照保持、コース変更・歴史訂正、expected_version、設定改定/削除との競合と原子性。
- 集約テスト: 一コース必須、価格/報酬/分数、過大割引、BASE_COURSE・ポイント専有行の入力拒否。
- UI 操作テスト: コース選択、試算の失効・再確認、候補削除、ロード/再試行/権限不足、店舗切替、本人画面への報酬漏洩防止。
- 日本語 Gherkin E2E: 店舗作成→完了、申請確定、削除済み版本への理由付き訂正。
- ADR 0018 / 0019 と CONTEXT を実装済み範囲へ更新。最終検証は task lint / task test / task build / task e2e、続いて code-review、コミット。成功は exit code で判定する。
