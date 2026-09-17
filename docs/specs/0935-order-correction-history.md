# 歴史版本による完了後訂正・変更履歴 — API 契約案

状態: **本文は承認済み（2026-09-16）。追加契約も承認済み（2026-09-17）。**

対象: [#935](https://github.com/kanghouchao/Kizuna/issues/935)。業務仕様は #384、先行契約は 0931〜0934、設計判断は ADR 0019。調査基点は `206aeaca8f17043bd3a240d6dbd7f97e0d91bed5`。

## 共通規約

- 本文は現行契約への差分。記載しない要求・応答の型と省略可否は現行 DTO を維持する。JSON は snake_case、任意字段は値がなければ省略する。
- ID は string（store_id・corrected_by は int64）、受注版は int64、金額・分数は int32、日時はオフセット付き ISO 8601、営業日は YYYY-MM-DD、実績時刻は既存の LocalTime 形式。
- M = ORDER_MANAGE、C = ORDER_MANAGE と ORDER_CORRECT、P = ORDER_SET_MANAGE。店舗 API は店舗コンソール資格と `X-Role: store`・`X-Store-ID`、店舗作用域を要求する。平台 API は授権店舗集合内の参照のみ。SERVICE_MANAGE は要求せず、設定変更能力は与えない。
- 共通エラーは `{error: string, details?: Record<string,string>}`。401 未認証、403 権限・店舗資格不足、404 対象なし・作用域外、400 入力・状態・カーソル不正、409 受注版・確認内容・並行更新の競合。失敗時に受注・履歴・報酬の一部だけを保存しない。

## 追加・変更する HTTP 端点

| メソッド・パス | 授権 | 要求 | 成功 |
| --- | --- | --- | --- |
| GET `/store/orders/{id}/corrections` | M | id: string 必須、cursor?: string、size?: int（既定 20） | 200 CursorPage<CorrectionHistory> |
| GET `/platform/orders/{id}/corrections` | P | 同上 | 200 CursorPage<CorrectionHistory> |
| POST `/store/orders/{id}/corrections` | C | 現行 OrderCorrectionRequest（下記） | 201 現行 OrderCorrectionResponse に下記字段を追加 |

履歴は未訂正なら空の content。受注が存在すれば未完了・取消済みでも読み取りは可能。履歴 GET は通常 409 を返さない。POST は COMPLETED のみを許し、それ以外は 400。

### 履歴のページング

外殻は `{content: CorrectionHistory[], next_cursor?: string}`。size は既存 CursorPage と同じく 1〜2000 に収める。`after_version DESC, id DESC` の全順序で、同じ組をカーソル比較に使う。受注版は保存時に確定し、同じ受注では訂正ごとに単調増加する。時刻の同値・時計補正でも訂正順が逆転しない。

カーソルは受注と作用域に結び付け、不正・別受注のカーソルは 400。新規訂正は先頭の再取得で表示し、既に開始した続きの取得では既読行の重複や未読の既存行の欠落を生まない。総件数・任意ソートは持たない。

### CorrectionHistory

次の字段は `?` の付いたものだけが省略可能。

| 字段 | 型 | 意味 |
| --- | --- | --- |
| correction_id | string | 不変・一意な訂正 ID |
| order_id / store_id | string / int64 | 対象受注・店舗 |
| business_date | date string | 原営業日 |
| completed_at | datetime string | 原完了日時 |
| corrected_at | datetime string | 訂正操作日時 |
| corrected_by? | int64 | 操作者 ID。アカウント削除後は省略 |
| reason | string | 訂正理由 |
| before_version / after_version | int64 | 保存前後の受注版 |
| before / after | CorrectionSnapshot | 当該訂正の前後の提供事実・金額 |

操作者メール等のアカウント情報や顧客連絡先は含めない。画面では ID を表示し、削除後は「削除済み利用者」と示す。

### CorrectionSnapshot

- `actual_arrival_time?: string`、`actual_end_time?: string`。
- `course: OrderCourse` 必須。現行の service_id・revision_id・revision_number・name・duration_minutes・price・remuneration・adoption_basis・adopted_at はすべて必須。
- `fee_lines: OrderFeeLineResponse[]` 必須。システム専有行も含む全明細。line_id・kind・name・amount・remuneration・system_owned は必須。duration_minutes は時間を持つ行だけ、service_id・revision_id・revision_number・adoption_basis・adopted_at は設定採用行だけ。amount は現行応答と同じ表示上の非負値（減項は kind で判別）。
- `special_services: SpecialServiceSnapshot[]` 必須。各要素の service_id・revision_id・revision_number・terms_version・name・charge_type・price・remuneration・adoption_basis・adopted_at・enrollment_id は必須。consent_event_id?: string・consent_version?: int64 は採用時に受諾根拠を持つ場合のみ。現在の受諾・要対応状態は混ぜない。
- `total_fee: int32`、`total_duration_minutes: int32`、`total_remuneration: int32`、`accrued_remuneration: int32` は必須・非負。報酬は支払済み額を意味しない。

### 訂正 POST の差分

現行の correction_id と previous_* / 現値の応答を維持し、`order_id: string`、`store_id: int64`、`business_date: date string`、`completed_at: datetime string`、`corrected_at: datetime string`、`corrected_by: int64`、`reason: string`、`before_version: int64`、`after_version: int64`、`previous_accrued_remuneration: int32`、`accrued_remuneration: int32` を必須追加する。

要求の expected_version: int64、reason: string（非空白・最大 500 文字）、fee_lines: OrderFeeLineRequest[] は必須。course_revision_id?: string の省略は維持、special_service_revision_ids?: string[] の省略は維持・空配列は全除去。actual_arrival_time / actual_end_time の省略は値なし。confirmation_token: string は保存時必須、既存 correction-preview では指定不可。fee_lines は編集可能行の全量で、未変更行は line_id で保持する。

未知字段、任意単価による設定条件の上書き、MANUAL_ADJUST、システム専有行の直送・変更・除去を拒否する。予定・人数・担当・受付・備考・伝言は要求型に追加しない。通常編集による終端内容変更を認めない。

## 維持する既存端点

`POST /store/orders/{id}/correction-preview`（C、200 OrderPreviewResponse）、`GET /store/orders/{id}/course-revisions`・`surcharge-revisions`・`special-service-revisions`（C、200 既存 CursorPage）は wire 変更なし。歴史版本は同店舗の実際の採用条件を使い、削除・改定・現在の不受諾で拒否しない。既存の search/cursor/size と型を維持する。

試算・保存はポイント台帳を読み書きしない。ポイント利用行を保持し、負の請求は 400。帰属・実入出金・完了日時・原営業日は動かさない。409 の details.expected_version は再取得、details.confirmation_token は再試算・再確認を案内し、自動再送しない。

## 永続化と単件結果

- 既存 t_order_corrections を拡張し、訂正機構は再実装しない。baseline に before_version / after_version（BIGINT NOT NULL）、business_date（DATE NOT NULL）、completed_at（TIMESTAMP WITH TIME ZONE NOT NULL）、before_snapshot / after_snapshot（JSONB NOT NULL）を定義する。独立した前後快照に置き換える既存の前値専用列・重複 JSON・コース FK/索引は撤去し、互換読みや移行は追加しない。
- reason、corrected_at、corrected_by と既存 ID・店舗・受注の関連は維持する。store/order の削除は CASCADE、操作者は SET NULL。快照内の設定・在籍・受諾 ID は歴史的な値として保持し、設定削除で消失させない。
- CHECK は before_version >= 0、after_version > before_version。受注・after_version に UNIQUE、履歴索引は `(store_id, order_id, after_version DESC, id DESC)`。前後快照と受注更新は同じロック・トランザクションで確定する。
- `order::result` の単件結果に `latestCorrectionId?: string` と `latestCorrection?: 訂正変更記録` を追加する。未訂正は両方なし。変更記録は上記の同じ不変 ID・原日時・前後版・快照・理由・操作者・訂正日時を持つ。最新一件だけを全履歴と誤認させない。
- 同境界に受注単位の上限付きカーソル履歴参照を公開し、中間の訂正も同じ ID で回収できるようにする。HTTP は追加せず、店舗隔離と一貫した読み取り断面を維持する。給与・精算側は correction_id で重複を除外できるが、給与仕訳・配信・実支払自体は実装しない。

## 画面と検証

既存の店舗訂正ページから歴史選択・試算・理由付き保存を完結させる。店舗詳細と平台受注一覧から履歴を開き、原営業日・完了日時・訂正日時、費用・報酬・項目の前後を表示する。読取専用の担当者にも履歴を提供し、訂正操作は C のみに表示する。Base UI、既存フォーム、useCursorList、店舗切替を使い、読込中・失敗・再試行・権限不足・空履歴を区別する。

認証済み HTTP と実 PostgreSQL で店舗・集合隔離、歴史選択、競合、敗者の履歴なし、凍結維持、台帳非依存、同時刻の履歴順序とページングを検証する。集約で金額境界、UI で再確認・権限・表示境界、少数の日本語 Gherkin E2E で主要導線を固定する。

承認後に実装・ADR 0019/CONTEXT の更新を行い、task lint・task test・task build・task e2e と Standards/Spec レビューを実施してコミットする。作業ブランチは `codex/issue-935-order-correction-history`。

## 追加承認済みの補足（2026-09-17）

既存の平台一覧応答には請求総額がなく、未訂正の受注は履歴も空のため、受け入れ基準の跨店費用照会を満たせない。`GET /platform/orders`（ORDER_SET_MANAGE、既存 Page・授権店舗集合、200）の PlatformOrderResponse に `total_fee: int32`（必須・非負、ポイント控除後の現在請求額）だけを追加する。要求・ページング・成功／失敗コード・他字段は不変。現在額を表示し、変更根拠は承認済みの履歴 API で示す。

## 実装検証（2026-09-16）

- `task lint`、`task test`、`task build`、`task e2e` はすべて退出コード 0。前端 1,440 テスト、実 PostgreSQL の統合 734 テスト、E2E 45 シナリオを通過。
- 店舗履歴のライト・ダーク・390px と平台履歴を画像で確認し、Escape 後のフォーカス復帰も E2E で確認。
- Standards レビューの指摘は修正済み。Spec レビューで挙がった平台請求総額の追加契約は、2026-09-17 に承認を受けた。

## 追加契約反映後の検証（2026-09-17）

- `total_fee` を必須・非負の現在請求額として実装し、未訂正時と訂正後のポイント控除額を実 HTTP で検証。前端はコース料金と請求総額を分けて表示する。
- `task lint`、`task test`、`task build`、`task e2e` はすべて退出コード 0。前端 1,440 テスト・統合 734 テスト・E2E 45 シナリオ成功。
- Standards / Spec の追加レビューは残存指摘なし。[平台一覧](assets/0935/platform-list.png)、[訂正結果](assets/0935/correction-result.png)、[店舗履歴](assets/0935/store-history-light.png)、[ダーク表示](assets/0935/store-history-dark.png)、[390px 表示](assets/0935/store-history-narrow.png)、[平台履歴](assets/0935/platform-history.png)を確認。
