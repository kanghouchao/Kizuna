# 受注完了の固定報酬・ポイント確定 — API 契約案

状態: **承認済み・実装検証済み（2026-09-16）。**

対象: [#934](https://github.com/kanghouchao/Kizuna/issues/934)。業務仕様は [#384](https://github.com/kanghouchao/Kizuna/issues/384)。調査基点は `dad7ddbaccaeb75a359d0226b34c94e99386af06`。依存 #932・#933 は CLOSED。既存の料金快照・試算署名・会員帰属・ポイント台帳を利用する。

## 共通規約

- 本文は既存契約への差分。記載しない要求・応答字段は現行 DTO および契約 0931〜0933 の型・必須性を維持する。新規の HTTP パスは設けない。
- JSON は snake_case。金額・ポイント利用数は非負の int32、残高は int64、ID は既存型、日時はオフセット付き ISO 8601。任意字段は null を返さず省略する。
- M は ORDER_MANAGE、C は ORDER_MANAGE と ORDER_CORRECT、P は ORDER_SET_MANAGE。店舗 API は認証、店舗コンソール資格、`X-Role: store`、`X-Store-ID` と店舗作用域を必要とする。平台は授権店舗集合の参照に限定する。
- 主要失敗は 401 未認証、403 権限・店舗資格不足、404 対象なし・作用域外、400 不正入力・不正遷移・初回試算の利用不能、409 版競合・確認済み結果の変化・保存時の残高不足。共通形式は `{error: string, details?: Record<string,string>}`。
- `details.expected_version` は再取得、`details.confirmation_token` は再試算・再確認、`details.use_points` は利用点数の見直しを案内する。自動減額・自動再送しない。

## 変更対象の HTTP 端点

| メソッド・パス | 授権 | 要求 | 成功応答・差分 |
| --- | --- | --- | --- |
| POST `/store/orders/preview` | M | 既存 OrderCreateRequest。confirmation_token は指定不可 | 200 OrderPreviewResponse。points を必ず返す |
| POST `/store/orders` | M | 既存 OrderCreateRequest、confirmation_token: string 必須 | 201 OrderResponse。会員条件・付与結果を再照合 |
| POST `/store/order-applications/{id}/confirmation-preview` | M | 既存 OrderApplicationConfirmationRequest。confirmation_token は指定不可 | 200 OrderPreviewResponse。points を必ず返す |
| POST `/store/order-applications/{id}/confirmation` | M | 同 Request、confirmation_token: string 必須 | 201 OrderResponse。会員条件・付与結果を再照合 |
| POST `/store/orders/{id}/preview` | M | 既存 OrderUpdateRequest。expected_version: int64 必須、confirmation_token は指定不可 | 200 OrderPreviewResponse。points を必ず返す |
| PUT `/store/orders/{id}` | M | 同 Request、confirmation_token: string 必須 | 200 OrderWorkQueueResponse。会員条件・付与結果を再照合 |
| POST `/store/orders/{id}/completion-preview` | M | 下記 CompletionInput、confirmation_token は指定不可 | 200 OrderPreviewResponse |
| POST `/store/orders/{id}/completion` | M | CompletionInput と confirmation_token: string 必須 | 200 既存 OrderCompletionResponse `{receipt_token?: string}` |
| GET `/store/orders/{id}` | M | id: string | 200 OrderResponse に報酬・完了字段を追加 |
| GET `/store/orders/work-queue` | M | 既存検索・cursor・size | 200 CursorPage<OrderWorkQueueResponse> に報酬字段を追加 |
| GET `/store/orders/archive` | M | 既存検索・page・size | 200 Page<OrderArchiveResponse> に報酬・完了字段を追加 |
| GET `/platform/orders` | P | 既存 page・size・sort | 200 Page<PlatformOrderResponse> に報酬・完了字段を追加 |
| POST `/store/orders/{id}/correction-preview` | C | 既存 OrderCorrectionRequest | 200 OrderPreviewResponse。points は既存どおり省略。下記金額字段を追加 |

単件・試算・保存にはページングなし。既存 Page/CursorPage の要求、上限、応答外殻と一意なソートを維持し、新しい履歴一覧は追加しない。OrderResponse を返す既存 start も同じ追加字段を返す。

## 要求・応答字段

### CompletionInput

- `expected_version: int64` 必須。
- `fee_lines: OrderFeeLineRequest[]` 必須。空配列の可否は既存のコース保持規則に従う。既存行は line_id で保持し、POINT_REDEMPTION の追加・変更・削除は受け付けない。
- `use_points?: int32`。利用するときだけ 1 以上を指定。省略は 0。0 の直送は 400。作成・通常更新・申請確定には利用点数入力を追加せず、試算は利用 0 とする。
- `confirmation_token` は試算で指定不可、完了で必須。生の内訳合計・報酬合計・完了日時は要求で受け取らない。

### OrderPreviewResponse

既存の course、fee_lines、total_fee、total_duration_minutes、total_remuneration、special_services、要対応字段、confirmation_token を維持する。

- `point_basis_amount: int32` 必須。通常割引後・ポイント控除前の金額。通常付与基準かつ金額上の利用上限。
- `points` は作成・申請確定・通常更新・完了試算で必須。訂正試算では省略し、ポイント台帳を参照しない。
- points の既存 `member_linked: boolean`、`usage_unit: int32`、`use_points: int32`、`grant_points: int32` は必須。`point_balance?: int64`、`member_code?: string` は会員関連がある場合だけ返す。
- `points.redemption_eligible: boolean` を必須追加。既存の会員・台帳規則で利用可能かを示す。残高と金額上限は別途検証する。非会員では false、grant_points は 0。
- 署名は入力・作用域・実行者・会員帰属先・利用資格・今回の付与結果を照合する。point_balance 自体は照合対象外とし、十分な残高への変化だけでは拒否しない。

### 競合の分類

- `409 details.use_points` は確認後の利用資格・残高・利用単位など、ポイント利用の是正を要する失敗に限る。付与計算の上限超過は説明付きの 400 を維持し、再計算可能な付与結果の変化は `confirmation_token` で再確認する。
- 顧客行の NOWAIT 取得競合は `409 details.customer_lock` で返す。画面はサーバの案内を示し、受注を再取得せずに会計内訳・利用ポイントを保持して再試算できる。

### 受注の参照型

OrderResponse、OrderWorkQueueResponse、OrderArchiveResponse、PlatformOrderResponse に以下を返す。

| 字段 | 型・省略 | 意味 |
| --- | --- | --- |
| total_remuneration | int32、必須 | 現在の採用項目の固定報酬合計。未完了では予定報酬 |
| accrued_remuneration | int32、必須 | 完了で発生した支払対象。未完了・取消は 0 |
| completed_at | ISO 8601 string、未完了・取消で省略 | 受注自身の完了操作日時。会員帰属日時と独立 |

既存 business_date を原営業日として表示する。完了操作で引き直さない。予定報酬・発生済み報酬を状態に応じて区別し、「支払済み」と表示しない。店舗詳細・平台一覧の既存個人情報境界は維持する。

## 永続化・公開モジュール境界

- pre-launch baseline の t_orders に `completed_at TIMESTAMP WITH TIME ZONE NULL`、`accrued_remuneration INTEGER NOT NULL DEFAULT 0` を追加する。COMPLETED は completed_at 必須、他状態は NULL かつ accrued_remuneration = 0、報酬は非負という CHECK を置く。
- 完了時に項目別報酬合計を accrued_remuneration へ保持する。割引・ポイント・回収・返金で減らさない。既存の提供内容訂正は項目合計と発生済み報酬を同時更新し、completed_at と business_date を保持する。
- 新規テーブル・FK・一覧検索用索引は不要。既存受注・店舗・担当在籍・快照の FK と削除方針を保持する。給与のための二重台帳は作らない。
- order の NamedInterface から単件結果を読み取れる窓口を公開する。結果は orderId、storeId、castEnrollmentId（未指名は空）、businessDate、completedAt、項目別快照、accruedRemuneration、version。未完了・取消は結果なし。個人情報を含めず、JPA entity は外へ渡さない。
- 結果は現在の単件参照であり、給与仕訳・実支払や訂正イベントの配信を意味しない。独立した訂正識別子・誤完了無効化は後続票の領分。

## 原子性と検証

- 通常保存でも試算時と同じ会員解決を行い、顧客・関連変更との競合を直列化する。電話番号は既存の店舗顧客解決だけに使い、会員同定の証明には使わない。
- 完了の受注版、特殊サービス拒否、会員条件、利用単位、現在残高、付与結果を同一トランザクションで再検証する。料金・発生済み報酬・完了日時・台帳・帰属・履歴は全成功または全取消とする。
- 認証済み HTTP と実 PostgreSQL で金額例 16,000/10,500、担当変更後 14,000/9,000、ポイント利用後請求 13,000・基準 16,000、翌日完了の原営業日維持を検証する。
- 集約テストで金額境界、HTTP 統合テストで残高・会員条件・設定・受注版の競合と部分成功なしを固定する。UI は試算差異の再確認、取得失敗・再試行、権限不足と報酬表記を利用者操作で確認する。主要導線は日本語 Gherkin E2E を追加する。
- 承認後は TDD、ADR/CONTEXT 整合、Taskfile の lint・test・build・e2e、Standards/Spec のコードレビューを行ってコミットする。現作業位置は detached HEAD のため、実装開始時に `codex/issue-934-completion-remuneration` を作成する。


## 検証結果

- `task lint`、`task test`、`task build`、`task e2e` はすべて終了コード 0。
- PostgreSQL 統合テスト 730 件、E2E 45 件が成功。電話照合と顧客統合の並行実行、会員関連変更、残高不足、残高だけの変化、同時完了、公開単件結果の店舗分離を含む。
- Standards / Spec の二軸レビューを実施し、指摘を修正後に再レビュー。残存指摘なし。
