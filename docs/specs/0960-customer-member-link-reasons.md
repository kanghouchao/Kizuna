# 会員関連の操作理由と区間履歴 — HTTP 契約

状態: 承認済み（2026-09-24）。対象: [#960](https://github.com/kanghouchao/Kizuna/issues/960)。

## 共通条件

- 基底パスは `/store/customers/{customerId}/member-link`。`customerId` は文字列。
- 全端点で認証、店舗へのアクセス権、`CUSTOMER_MANAGE` を必須とする。店舗指定は既存の `X-Role: store` と `X-Store-ID` を使用する。
- 他店舗の顧客は 404。統合済み顧客への書き込みは 409 とし、存続顧客へ暗黙に転送しない。
- エラーは既存の `{ error: string, details?: Record<string, string> }`。未認証 401、権限不足 403、入力不正 400、顧客・会員コードの対象なし 404、状態競合 409。
- JSON は snake_case。日時はオフセット付き ISO 8601 文字列。応答の可空値は既存の Jackson 設定に従い省略する。

## 端点

| メソッド・相対パス | 用途 | 要求 | 成功 |
| --- | --- | --- | --- |
| `GET /` | 現在の ACTIVE 関連 | 本文なし | 200 `CurrentLink`。関連なしは既存どおり 404 |
| `POST /` | 会員コードによる成立・変更・再関連 | `LinkRequest` | 201 `CurrentLink`（新しい区間 ID を含む） |
| `POST /releases` | 現在の関連の理由付き解除 | `ReleaseRequest` | 204、本文なし |
| `GET /history` | ACTIVE と RELEASED の区間史 | `cursor?: string`, `size?: integer` | 200 `CursorPage<LinkHistory>` |

表の `/` は基底パスそのものを表し、末尾スラッシュは要求しない。理由を持たない既存の `DELETE` は削除し、互換端点を設けない。

### LinkRequest

- `member_code: string` 必須。数字 12 桁。
- `expected_link_id?: string`。変更時は確認済み ACTIVE 区間の ID を必須とする。初回成立・解除後の再関連は省略し、「ACTIVE が存在しない」ことを要求する。明示的な null も未関連の期待として扱う。空文字列は 400。
- `operation_reason?: string`。変更時は必須。初回成立・再関連では任意。指定時は前後空白除去後 1〜500 文字で、空白のみ・上限超過は 400。

期待した区間 ID と現在の状態が一致しない場合は 409。現在と同じ会員への変更、同店の別顧客で ACTIVE の会員への関連も 409。顧客ロックと既存の一意制約で並行操作を制御する。

変更は旧区間の解除と新区間の成立を同一トランザクション・同一操作日時で確定する。入力理由を旧区間の `release_reason` と新区間の `operation_reason` に保持する。新規成立の機構上の根拠はサーバーが `MEMBER_CODE` に固定し、クライアントは指定できない。

### ReleaseRequest

- `expected_link_id: string` 必須、空文字列不可。
- `operation_reason: string` 必須。前後空白除去後 1〜500 文字。

ACTIVE なし、既に解除済み、または現在の ID と不一致の場合は 409。解除対象の取り違えを防ぎ、再取得・再確認を求める。区間を物理削除せず、理由・操作者・日時を保存する。

### CurrentLink

- `id: string` 必須。関連区間の ID。
- `linked: boolean` 必須、常に true。
- `member_code: string` 必須。
- `linked_at: string` 必須。

### LinkHistory

- 必須: `id: string`, `member_code: string`, `status: "ACTIVE" | "RELEASED"`, `reason: "MEMBER_CODE" | "MEMBER_REQUEST" | "MIGRATION"`, `linked_at: string`。
- 任意: `operation_reason: string`（成立・変更時の入力理由）、`release_reason: string`（解除理由）、`linked_by: integer` / `released_by: integer`（操作者 ID）、`linked_by_name: string` / `released_by_name: string`（操作者表示名）、`released_at: string`。
- `reason` は既存の成立機構を表し、人の入力理由とは別項目とする。初回成立・会員申請由来など入力理由のない区間では `operation_reason` を省略する。
- ACTIVE では解除情報を省略する。操作者が削除された場合も履歴行を保持し、ID・名前は欠落可能とする。UI は「不明」を表示する。
- 対象会員は区間に保存された会員コードで確認する。会員・公開 DTO にこれらの監査項目を追加しない。

### ページング

既存の `CursorPage` を使用する。`content: LinkHistory[]` は必須、`next_cursor?: string` は次ページがある場合のみ返す。`size` は既定 20、1〜2000 に丸める。順序は `linked_at DESC, id DESC` で、カーソルも同じ組を使う。不正カーソルは 400。

## UI と検証

店舗の会員関連区画で成立・変更・再関連を現況に応じて明示し、変更と解除では理由入力と対象確認を必須にする。履歴は関連 ID、会員コード、状態、成立機構、入力理由、操作者、日時を表示する。409 後は入力を保持して現況を再取得し、再確認してから再送する。無権限時は照会・操作を行わず、読取失敗時は書き込みを止めて再試行を提供する。

既存 HTTP 統合テストと実 PostgreSQL で理由必須、並行競合、変更の原子性、区間保持、権限・店舗隔離を検証する。受注帰属・ポイント台帳の不変、双方 ACTIVE の統合拒否、会員申請による顧客整備も回帰確認する。UI 固有の挙動はページテスト、主要操作は日本語 E2E、狭幅・長い値・両テーマ・キーボード・失敗回復は実ブラウザで確認する。
