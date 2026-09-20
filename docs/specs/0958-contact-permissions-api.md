# 用途別連絡可否の HTTP 契約

状態: 承認済み（2026-09-20）。対象: #958。業務仕様は [顧客台帳仕様](0385-customer-contacts.md)。

## 共通規則

- 店舗 API は認証と `CUSTOMER_MANAGE` を必須とし、`X-Role: store` と `X-Store-ID` で選択した授権店舗に限定する。他店舗の顧客・連絡先・履歴は返さない。
- JSON キーは snake_case。ID は string、操作者 ID は integer (int64)、日時はオフセット付き ISO 8601 string。
- 用途は `BUSINESS`（業務）と `MARKETING`（販促）。状態は `UNKNOWN`（未確認）、`ALLOWED`（許可）、`DENIED`（拒否）。
- 共通失敗は 400（入力・遷移・cursor 不正）、401（未認証）、403（権限・店舗アクセス拒否）、404（対象なし・他店舗・削除済み連絡先）、409（統合済み顧客への変更・操作競合）。
- エラーは既存の `{error: string, details?: Record<string, string>}`。利用者向け日本語で理由を示す。
- 会員・公開 API は追加・変更しない。ゲスト同意取り込み、今回だけの許可、実送信は本票の対象外。

## 連絡可否の明示変更

`PUT /store/customers/{customerId}/contacts/{contactId}/permissions/{purpose}`

必須の要求フィールド（null・省略不可）:

| フィールド | 型 | 検証 |
| --- | --- | --- |
| status | UNKNOWN / ALLOWED / DENIED | 用途一つの状態 |
| source | string | 前後空白除去後 1–200 文字。取得元 |
| reason | string | 前後空白除去後 1–2000 文字。判断根拠 |

200 で更新後の `ContactResponse` を返す。もう一つの用途と他行の明示状態は変更しない。同値行がある場合の共同制約は全有効行から再計算する。

制約を緩めるには根拠付きの `ALLOWED` への変更を要する。`DENIED` → `UNKNOWN` は 400 とし、削除や未確認への変更で拒否を解除できないようにする。同一状態の再記録も出所・根拠を持つ明示操作として履歴を残す。

## 既存の連絡先 API の応答拡張

対象は次の三端点。要求の型は既存どおり。

| メソッド・パス | 成功 | 要求 |
| --- | --- | --- |
| GET /store/customers/{customerId}/contacts | 200 CursorPage<ContactResponse> | cursor?: string、size?: integer（既定 20、1–2000 に制限） |
| POST /store/customers/{customerId}/contacts | 201 ContactResponse（id を含む） | type: PHONE / EMAIL / LINE、value: string（必須、空白不可、最大 320 文字） |
| PUT /store/customers/{customerId}/contacts/{contactId} | 200 ContactResponse | POST と同じ必須フィールド |

`ContactResponse` の既存フィールド `id`、`customer_id`、`origin_customer_id`、`type`、`value`、`preferred`、`created_at`、`updated_at` を維持し、次の必須フィールドを追加する。

| フィールド | 型 | 意味 |
| --- | --- | --- |
| business_status | 状態 enum | 当該行の業務状態 |
| marketing_status | 状態 enum | 当該行の販促状態 |
| effective_business_status | 状態 enum | 同一顧客・同種類・同正規化値の全有効行による共同制約 |
| effective_marketing_status | 状態 enum | 同上、販促用途 |

共同制約は DENIED > UNKNOWN > ALLOWED。ページ外の行も対象にする。能動的な連絡が可能なのは effective 状態が ALLOWED の場合だけ。新規行は両用途 UNKNOWN。明示状態と共同制約を UI で区別して表示する。

種類・正規化値の変更では、旧集合の共同制約を残存行へ引き継いでから変更行の両用途を UNKNOWN にする。同値の表示調整と優先指定では状態を維持する。既存の優先指定競合は 409。

## 削除・優先指定

- `DELETE /store/customers/{customerId}/contacts/{contactId}`: 要求 body なし、成功 204。旧集合の残存行への制約継承と履歴を同一トランザクションで確定する。残存行がなければ継承先を作らない。
- `PUT /store/customers/{customerId}/contact-preferences/{type}`: 必須 `contact_id: string | null`、成功 204。既存の要求・応答を維持し、可否には影響しない。
- `DELETE /store/customers/{customerId}`: 要求 body なし、成功 204。連絡可否履歴がある場合も理由付き 409 で拒否する。既存の連絡先履歴・受注・統合による削除拒否を維持する。

## 履歴

`GET /store/customers/{customerId}/contact-history` を拡張する。成功 200。

- 要求: `cursor?: string`、`size?: integer`（既定 20、1–2000 に制限）。
- 応答: `{content: ContactHistoryResponse[], next_cursor?: string}`。末尾では next_cursor を省略。総件数なし。
- 順序: `occurred_at DESC, id DESC`。cursor は同じ日時・ID 組を使う。一覧の連絡先は既存の `id ASC`。
- 既存の `id`、`contact_id`、`origin_customer_id`、`action`、`actor_id`、`occurred_at`、`before`、`after` を維持する。before は作成時のみ省略、after は必須。
- before/after の既存の `customer_id: string`、`type: PHONE | EMAIL | LINE`、`value: string`、`preferred: boolean`、`deleted: boolean` に、必須の `business_status` と `marketing_status` を追加する。
- action は既存の CREATE / UPDATE / DELETE / PREFERENCE / TRANSFER に、`PERMISSION_CHANGE`（明示変更）と `RESTRICTION_INHERITANCE`（制約継承）を追加する。値変更の初期化は UPDATE の前後状態で示す。
- `purpose?: BUSINESS | MARKETING`、`source?: string`、`reason?: string` は PERMISSION_CHANGE の場合必須、その他では省略。
- `source_contact_id?: string` は RESTRICTION_INHERITANCE の場合必須で除去元を表す。この行の contact_id が継承先。before/after に両用途の前後状態を保持する。その他では省略する。
- `operation_id: string` を全履歴に付与し、一回の削除・値変更とその制約継承を同じ ID で追跡できるようにする。継承は新しい同意として表示しない。

## 原子性と検証

顧客単位の既存ロックに参加し、状態・制約継承・履歴を一括で確定する。失敗時は全体をロールバックする。店舗隔離、権限、重複集合がページをまたぐ場合、並行操作、途中失敗、履歴付き削除拒否を HTTP と実 PostgreSQL で検証する。

UI は既存の顧客連絡先欄で両用途の状態・共同制約、出所・根拠付き変更、変更と継承の履歴を扱う。ページテスト、日本語 E2E、狭幅・長い値・両テーマ・キーボード・失敗時の再試行を検証する。
