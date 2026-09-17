# 誤完了の無効化と関連する新受注 — API 契約

状態: **2026-09-17 承認済み。実装・検証完了。**

対象: [#937](https://github.com/kanghouchao/Kizuna/issues/937)。正本は #384、先行契約は [0935](0935-order-correction-history.md) と [0936](0936-point-redemption-offset.md)。調査基点は `fe7f3084b254b0112b3a3fb65616983a082a07b8`。

## 共通規約

以下は既存契約への差分。記載しない要求・応答・省略可否は現行 DTO を維持する。JSON は snake_case。ID は string、店舗・操作者 ID と受注版は int64、金額は int32 の整数円、日時はオフセット付き ISO 8601、営業日は YYYY-MM-DD。`?` の字段のみ省略可能で、値なしは省略する。

M は ORDER_MANAGE、C は ORDER_MANAGE と ORDER_CORRECT、P は ORDER_SET_MANAGE。店舗 API は認証・店舗コンソール資格・`X-Role: store`・`X-Store-ID` と店舗隔離を要求する。平台は授権店舗集合内の参照だけを提供する。

共通エラーは `{error: string, details?: Record<string,string>}`。401 は未認証、403 は権限・店舗資格不足、404 は不存在・作用域外、400 は入力・状態不正、409 は古い版・並行更新・確認条件変化。未知字段も拒否し、失敗では部分保存しない。

## 追加・変更する HTTP 端点

| メソッド・パス | 授権 | 要求の差分 | 成功と応答の差分 |
| --- | --- | --- | --- |
| POST `/store/orders/{id}/completion-invalidation` | C | `expected_version: int64`（0 以上）、`reason: string`（空白除去後 1〜500 文字）。両方必須 | 201、下記の ChangeRecord |
| POST `/store/orders/preview` | M | OrderCreateRequest に `replacement_for_order_id?: string` | 200、既存 OrderPreviewResponse |
| POST `/store/orders` | M | 同上。試算の confirmation_token は既存どおり保存時必須 | 201、OrderResponse に下記の状態・関連字段 |
| GET `/store/orders/{id}` | M | 変更なし | 200、OrderResponse に状態・関連字段 |
| GET `/store/orders` | M | 変更なし | 200、Page<OrderSummaryResponse> の各行に状態・関連字段 |
| GET `/store/orders/archive` | M | 変更なし | 200、Page<OrderArchiveResponse> の各行に状態・関連字段 |
| GET `/store/orders/work-queue` | M | 変更なし | 200、既存 CursorPage の各行に状態・関連字段 |
| GET `/platform/orders` | P | 変更なし | 200、Page<PlatformOrderResponse> の各行に状態・関連字段 |
| GET `/store/orders/{id}/corrections` | M | 変更なし | 200、CursorPage<ChangeRecord>。通常訂正と無効化を同じ履歴に含める |
| GET `/platform/orders/{id}/corrections` | P | 変更なし | 同上 |

OrderResponse / OrderWorkQueueResponse を返す既存の `PUT /store/orders/{id}`、`POST /store/orders/{id}/start`、`POST /store/order-applications/{id}/confirmation` も同じ状態・関連字段を返す。要求・授権・成功コードは変更しない。

### 状態・関連字段

- `completion_invalidated: boolean` は必須。無効化後も status は COMPLETED。
- `replacement_for_order_id?: string` は再提供の元受注 ID。通常受注では省略する。作成後は変更できない。
- 原コース・特殊サービス・明細の内容と採用額は保持する。無効化後のそれらは原記録であり、有効な請求・報酬として表示しない。既存の `total_fee`、`total_remuneration`、`accrued_remuneration` は該当 DTO に存在する場合、有効額として零を返す。原時間・原日時は維持する。

### ChangeRecord

0935 の CorrectionHistory（現行 OrderCorrectionResult）を拡張して使う。`correction_id: string` は訂正・無効化に共通の一意な変更 ID で、名前は維持する。

必須字段は `correction_id`、`order_id: string`、`store_id: int64`、`business_date: date string`、`completed_at: datetime string`、`corrected_at: datetime string`、`reason: string`、`before_version / after_version: int64`、`before / after: CorrectionSnapshot`。`corrected_by?: int64` は既存どおり操作者削除後のみ省略する。

追加字段は `change_type: "CORRECTION" | "COMPLETION_INVALIDATION"`（必須）。before / after の両快照にも `completion_invalidated: boolean`（必須）を追加する。他の快照字段は 0935 の型・省略可否を維持する。無効化の after は原項目を保持し、有効な費用・報酬合計は零になる。

通常訂正の `POST /store/orders/{id}/corrections`（C、201、既存 OrderCorrectionResponse）の wire 型は変更しない。無効化済み受注に対する同 POST と `POST /store/orders/{id}/correction-preview`（C）は 400 を返す。

### 無効化の失敗と確認

- 未完了・取消済み・二度目の無効化は 400。復活の経路は設けない。
- 提供項目と通常割引を有効額から外した後、保持するシステム専有明細の符号付き合計が負なら 400。`details.point_redemption` にポイント救済後の再実行を案内する日本語文言を返す。台帳を照会せず、受注明細の元利用と相殺から判定する。
- 古い版・訂正との競合は 409、`details.expected_version` で再取得・再確認を案内する。受注の排他制御と変更記録を一つのトランザクションに含める。
- 専用画面で原内容・原営業日・原完了日時、費用と報酬の前後、不可逆性を表示し、「全く提供していない」ことと理由を確認して送信する。表示時の版を送るため、別の試算 API や確認トークンは追加しない。

### 再提供の新受注

replacement_for_order_id は同店舗の無効化済み COMPLETED の受注だけを参照できる。不存在・作用域外は 404、未無効化は 400。試算と保存の双方で検証し、確認トークンに関連先も束縛する。確認後の関連先変更は 409。

元受注の原内容を自動採用せず、既存の新規作成画面でコース・担当・特殊サービスを選び、現在の本人受諾・試算・保存規則を適用する。元受注の会員帰属・ポイント・完了状態を新受注に継承しない。関連は一対多を許し、作成件数の新しい制限は追加しない。

### ページング

通常一覧は既存 Page を維持し、既存ソートは一意な ID で終わる全順序とする。作業キューは既存 CursorPage を維持する。変更履歴は 0935 の `after_version DESC, id DESC`、既定 size 20・上限 2000・受注と作用域に束縛した cursor を維持する。新しい無制限配列や一覧端点は作らない。単件操作にはページングなし。

## 保存・公開境界

- pre-launch baseline の t_orders に `completion_invalidated BOOLEAN NOT NULL DEFAULT FALSE` と `replacement_for_order_id VARCHAR(64) NULL` を追加する。ID 列の長さは既存受注 ID と一致させる。
- CHECK で無効化なら status = COMPLETED、有効 total_fee と accrued_remuneration は零、関連先は自己 ID 以外を要求する。原コース・明細・原営業日・原完了日時を削除・上書きしない。
- 関連先 FK は `(replacement_for_order_id, store_id)` から t_orders の `(id, store_id)` への複合 FK、onDelete NO ACTION。同店舗の原記録を保持し、関連を黙って切らない。索引は `(store_id, replacement_for_order_id)`。店舗削除の既存 CASCADE との整合も実 PostgreSQL で検証する。
- t_order_corrections に `change_type VARCHAR(32) NOT NULL` を追加し、許容値 CHECK と、order_id に対する COMPLETION_INVALIDATION の部分 UNIQUE 索引を設ける。前後 JSONB に無効化状態を保持する。既存の版 UNIQUE・履歴索引・店舗/受注 CASCADE・操作者 SET NULL は維持する。
- `order::result` の単件結果に `completionInvalidated: boolean`、`replacementForOrderId?: string` を追加する。既存 latestCorrection と履歴に無効化の同じ変更 ID・種別・前後快照を含め、項目の採用額を原記録、有効 accruedRemuneration を零として区別できるようにする。HTTP の追加端点は設けない。
- 無効化済み受注の伝票申領は既存の申領不能応答（404）で拒否し、帰属・ポイント・来店・昇格を生成しない。無効化は受注行 → 伝票行の順にロックし、申領と直列化する。
- 無効化はポイント台帳・付与・会員帰属・実返金・支払を読み書きしない。原利用 -3,000 と相殺 +3,000 を保持し、有効な提供費用零と合算して請求零を導出する。全額割引や任意調整は使わない。

## 画面・検証・完了条件

店舗詳細に専用無効化入口を置き、一覧・詳細・平台一覧・変更履歴に無効化を明示する。無効化後は通常訂正を提示せず、関連を固定した新規作成への入口を示す。ORDER_CORRECT 不足時は訂正担当者、ポイント処置に必要な POINT_ADJUST 不足時はポイント救済担当者を案内する。POINT_ADJUST 保持者は既存の専用救済に進み、完了後に再取得して無効化を再実行する。

既存 Base UI・フォーム・取得ライフサイクル・店舗切替を使用し、ロード・取得失敗・再試行・権限不足を区別する。店舗切替で旧店舗の内容を表示しない。

承認済みの集約・認証済み HTTP と実 PostgreSQL・利用者操作 UI・日本語 Gherkin E2E の境界で TDD を行う。10,000 / 利用 3,000 の救済前拒否と救済後零、二重無効化、通常訂正拒否、競合と部分成功なし、隔離、原記録保持、再提供での現在条件・受諾・試算、権限不足を検証する。

実装と同時に ADR 0019 と CONTEXT を更新する。最終検証は `task lint`・`task test`・`task build`・`task e2e` の exit code と Standards / Spec のコードレビュー。実装ブランチは `codex/issue-937-order-completion-invalidation`。

## 検証結果

2026-09-17 に `task lint`、`task test`、`task build`、`task e2e` がすべて exit code 0。E2E は 47 シナリオが成功し、ポイント救済から無効化、関連を固定した新受注の保存までを含む。追加した狭幅スクロールとキーボード操作も同じ Docker イメージで対象シナリオを再実行し exit code 0。Standards / Spec レビューは指摘を修正後、両軸とも残件なし。

管理コンソールは DESIGN.md の最小幅を維持する。390px では横スクロールで原記録の右端を参照でき、再提供リンクへのフォーカス移動と Enter で新規作成へ進める。確認ダイアログは Escape で閉じ、起点にフォーカスが戻る。

- [ライト表示](assets/0937/light.png)
- [ダーク表示](assets/0937/dark.png)
- [狭幅・操作位置](assets/0937/narrow.png)
- [狭幅・右端へスクロール](assets/0937/narrow-right.png)
