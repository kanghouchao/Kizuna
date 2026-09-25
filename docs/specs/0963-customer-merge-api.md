# 顧客統合の資料選択・再確認・監査履歴 — HTTP 契約案

状態: 承認済み（2026-09-25）。対象: [#963](https://github.com/kanghouchao/Kizuna/issues/963)。
本書を実装と検証の契約とする。

## 共通条件

- JSON は snake_case、ID は特記がなければ string、日時はオフセット付き ISO 8601 string。
- 全端点で認証と店舗アクセス権を必須とし、既存の `X-Role: store` と `X-Store-ID` を使う。他店舗の対象は 404。
- 比較・プレビュー・実行は `CUSTOMER_MANAGE` と `CUSTOMER_MERGE` の両方が必須。編集値の省略でも免除しない。統合履歴の一覧・詳細は `CUSTOMER_MERGE` が必須。
- エラーは `{ error: string, details?: Record<string, string> }`。未認証 401、権限不足 403、入力不正 400、対象なし・店舗外 404、状態競合 409。予期しない失敗は内部情報を含まない 500。
- 応答の可空値は既存の Jackson 設定に従い省略する。要求での省略と null の区別は各型に記載する。

## 端点

| メソッド・パス | 要求 | 成功 |
| --- | --- | --- |
| `GET /store/customers/merge-comparison` | `ids: string[]`、異なる顧客をちょうど 2 件 | 200、既存 `CustomerMergeComparisonResponse[2]` |
| `POST /store/customers/{customerId}/merge-preview` | `PreviewRequest` | 200 `MergePreview`。業務データの書き込みなし |
| `POST /store/customers/{customerId}/merges` | `MergeRequest` | 200 `MergeResult` |
| `GET /store/customers/{customerId}/merges` | `cursor?: string`, `size?: integer` | 200 `CursorPage<MergeSummary>` |
| `GET /store/customers/{customerId}/merges/{mergeId}` | 本文なし | 200 `MergeAudit` |

POST の `customerId` は存続顧客。履歴の `customerId` は当該統合に存続側または被統合側として関与した顧客であり、墓標も指定できる。無関係の顧客と `mergeId` の組は 404。比較の既存端点は二行選択の入口として維持し、新しいプレビューの代用にはしない。

## 要求

### PreviewRequest

- `merged_customer_id: string` 必須、空白不可、最大 64 文字。
- `profile?: Profile`。省略または null は存続側の全資料を採用。指定する場合は全フィールドを含む完全な確定資料とし、フィールド単位の省略は 400。
- `preferred_contacts?: PreferredContacts`。省略または null の場合、種類ごとに既存の優先指定が 0〜1 件ならその指定を採用する。2 件なら未解決として返す。

`Profile` の全フィールド: `name`, `address`, `building_name`, `landmark`, `classification`, `usage_areas`, `ng_type`, `ng_content` は `string | null`、`has_pet` は `boolean | null`。null は空欄への明示変更で、未変更を意味しない。文字数は既存 DB 列の上限を超えられず、超過は 400。双方の値を自動連結・補完しない。

`PreferredContacts` は `phone`, `email`, `line` の 3 キーすべてが必須で、各値は `string | null`。string はその種類の、双方いずれかに属する有効な連絡先 ID。null は「指定なし」。削除済み・別種類・対象外 ID は 400。同値でも ID を区別する。

### MergeRequest

- `merged_customer_id: string` 必須。
- `preview_token: string` 必須、空白不可。直前に確認したプレビューのトークン。
- `profile?: Profile`。プレビューと同じ省略・null 規則。
- `preferred_contacts: PreferredContacts` 必須。全種類について一件または指定なしを明示する。
- `warnings_acknowledged: boolean` 必須、true のみ許可。注意事項が空でも最終確認を要する。
- `operation_reason: string` 必須、前後空白除去後 1〜500 文字。

トークンは店舗、二顧客と方向、双方の資料・連絡先・関連区間・受注集合と状態、最終会員と現在残高、確定資料・優先指定に結び付く、クライアントが改変できない不透明値とする。トークン形式不正は 400。対象・確定値・関連情報の不一致は 409 とし、書き込まず再プレビューを求める。実装方式は既存の再確認機構を優先し、クライアント申告の版だけを信用しない。

## プレビュー応答

`MergePreview` の必須フィールド:

- `surviving: CustomerSnapshot`, `merged: CustomerSnapshot`。
- `profile: Profile`。実際に確定する候補資料。
- `preferred_contacts: PreferredContacts`。未解決種類の値は null だが、下記の衝突配列により明示的な「指定なし」と区別する。
- `preference_conflicts: ("PHONE" | "EMAIL" | "LINE")[]`。未解決種類のみ。空配列になるまで実行できない。
- `member_linked: boolean`。統合後に ACTIVE 関連が存在するか。
- `unfinished_order_count: integer`。双方の CONFIRMED・IN_SERVICE 受注の合計。
- `moved_order_count`, `moved_contact_count`, `moved_link_count`: integer。被統合側から移す件数。

任意フィールド:

- `final_member_code: string`, `point_balance: integer`。関連があるときのみ返す。残高はプラットフォーム全体の現在値で、64 bit 整数。未関連を零と扱わない。
- `preview_token: string`。優先指定の衝突がすべて解決したプレビューだけに含める。

`CustomerSnapshot` は `id: string`, `profile: Profile`, `contacts: ContactSnapshot[]`, `member_links: LinkSnapshot[]` を持つ。配列はこの二顧客に属する現在の行集合の快照で、履歴一覧を埋め込まない。

`ContactSnapshot` の必須項目は `id`, `customer_id`, `origin_customer_id`: string、`type`: PHONE / EMAIL / LINE、`value`: string、`preferred`, `deleted`: boolean、`business_status`, `marketing_status`, `effective_business_status`, `effective_marketing_status`: UNKNOWN / ALLOWED / DENIED。`created_at`, `updated_at`: string も必須。削除済み行も移動・監査対象とし、優先選択と有効な共同制約からは除外する。原資料の実効状態は統合前の各顧客単位で計算する。

`LinkSnapshot` は既存 `CustomerMemberLinkHistoryResponse` の必須・任意項目に `customer_id: string` を加えた型。ACTIVE と RELEASED の区間を含み、ID・理由・成立／解除日時を保持する。

双方 ACTIVE はプレビュー・実行とも 409。「先に理由付きで関連を解除する」案内を返し、統合内で会員を選択させない。一方のみ ACTIVE なら統合方向にかかわらず維持する。

## 実行結果と履歴

`MergeResult` の全項目は必須: `merge_id`, `surviving_customer_id`: string、`moved_order_count`, `moved_contact_count`, `moved_link_count`: integer。

`MergeSummary` は既存履歴項目を維持し、必須の `operation_reason: string`, `moved_contact_count: integer` を加える。既存項目は `id`, `counterpart_customer_id`: string、`direction`: SURVIVING / MERGED、`merged_at`: string、`moved_order_count`, `moved_link_count`: integer が必須、`counterpart_customer_name`, `merged_by_name`: string は任意。重い快照と移動 ID は一覧には含めない。

`MergeAudit` の必須項目:

- `id`, `surviving_customer_id`, `merged_customer_id`: string。
- `before_surviving`, `before_merged`, `after_surviving`: CustomerSnapshot。
- `moved_order_ids`, `moved_contact_ids`, `moved_link_ids`: string[]。この一回の操作で実際に移動した集合。
- `operation_reason`, `merged_at`: string。
- `merged_by: integer`。実行時に確定した操作者 ID を保持する。

`merged_by_name?: string` は実行時の表示名。監査は記録時点の資料で、後続編集・連鎖統合によって書き換えない。連絡先の詳細な同意根拠は既存の連絡先履歴を ID で追跡でき、統合監査には別の同意として複製しない。

履歴一覧は既存 `CursorPage`（`content` 必須、`next_cursor` は次頁があるときのみ）。既定 size=20、1〜2000 に丸める。`merged_at DESC, id DESC` と同じ組をカーソルに使う。不正カーソルは 400、履歴なしは空ページ。

## 原子性・画面・検証境界

顧客のロック下で関連情報とトークンを検証し、確定資料の適用、優先指定、全連絡先・全状態の受注・全関連区間の付替え、墓標の連鎖圧平、監査保存を一つのトランザクションで確定する。並行更新を検証から書き込みまでの間に取り逃さない。途中失敗で部分更新を残さない。同一 ID は 400、墓標指定・再実行・双方 ACTIVE・古いプレビューは 409。

同値の連絡先を削除せず、ID と由来を保持する。統合後の同値有効行は用途別に拒否 > 未確認 > 許可を適用し、その後の削除・値変更でも既存の制約継承を働かせる。受注の受付時の写し、確定済み帰属、ポイント台帳・残高は書き換えない。

二行選択から比較・資料選択・編集・最終確認へ進み、存続側を初期値とする。注意事項の明示確認、優先衝突の解決、理由入力を要求する。最終会員・現在残高・未完了受注件数と、完了時の有効関連で帰属すること、過去受注への自動帰属・残高変更・undo がないことを説明する。409 後は入力を保持して再取得・再確認する。会員・公開 DTO には内部評価、注意事項、統合監査のキーを追加しない。

HTTP と実 PostgreSQL を主境界に、片側 ACTIVE の両方向、双方 ACTIVE、資料・連絡先・関連・受注・残高の変更後の再確認、途中失敗、連鎖統合、権限の全組合せ、他店舗、内部情報非公開を検証する。UI の選択・確認・回復をページテスト、主要操作を日本語 E2E で確認する。狭幅・長い値・両テーマ・キーボード・失敗回復を実ブラウザで確認し、最後に Taskfile の lint・test・build・e2e を実行する。
