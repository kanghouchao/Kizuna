# 顧客の複数連絡先と変更履歴 HTTP 契約案

状態: 承認済み。対象: #957。基準: `681c70e8`（#956 実装済み）。
ユーザーの承認に基づいて前後端を実装する。用途別の連絡可否、全種類の重複候補拡張、高度な統合プレビューは後続票で扱う。

## 共通契約

- JSON は snake_case。顧客・連絡先・履歴の ID は文字列、操作者 ID は整数、日時は ISO 8601 の UTC 文字列。必須項目は null 不可。
- 店舗 API は認証、`X-Role: store` と `X-Store-ID`、当該店舗へのアクセス権を要する。連絡先とその履歴の読み書きは `CUSTOMER_MANAGE` を要する。
- 主な失敗は 400（不正入力・不正カーソル）、401（未認証）、403（権限不足）、404（不存在・他店舗・指定顧客に属さない連絡先）、409（競合・履歴付き顧客の削除拒否）。既存の `{error: string, details?: Record<string, string>}` を使い、理由は日本語で返す。
- 一覧・履歴のページは `{content: T[], next_cursor?: string}`。cursor は任意の不透明文字列、size は任意の整数（既定 20、既存 CursorPage と同じく 1〜2000 に制限）。続きがなければ next_cursor は省略する。
- 連絡先の変更と履歴保存は同一トランザクション。削除・統合との競合は顧客単位で直列化し、部分更新を残さない。先に顧客削除が完了した操作は 404、先に履歴作成が完了した顧客削除は理由付き 409 とする。

## 型

`ContactType`: `PHONE | EMAIL | LINE`。

`ContactInput`: `type: ContactType`、`value: string`（ともに必須）。value は前後空白除去後に空文字不可、入力上限 320 文字。電話は日本番号として検証して +81 形式へ統一し、海外番号と内線付き入力を拒否する。メールは形式を検証し、前後空白とドメイン部の小文字化だけを行う。LINE ID は前後空白のみを除去し、大文字小文字を保持する。同値行の追加は許す。

`ContactResponse`: `id: string`、`customer_id: string`、`origin_customer_id: string`、`type: ContactType`、`value: string`（正規化後）、`preferred: boolean`、`created_at: string`、`updated_at: string`（すべて必須）。origin_customer_id は最初に登録した顧客で、統合後も変えない。

`ContactSummary`: `id: string`、`type: ContactType`、`value: string`（すべて必須）。

`ContactState`: `customer_id: string`、`type: ContactType`、`value: string`、`preferred: boolean`、`deleted: boolean`（すべて必須）。

`ContactHistoryResponse`: `id: string`、`contact_id: string`、`origin_customer_id: string`、`action: CREATE | UPDATE | DELETE | PREFERENCE | TRANSFER`、`actor_id: integer`、`occurred_at: string`（すべて必須）、`before?: ContactState`、`after: ContactState`。CREATE の before のみ省略し、削除は after.deleted=true として残す。優先の切替では解除行と指定行の両方を記録する。統合による所属変更も前後の顧客 ID を残す。

## 連絡先の端点

| メソッド・パス | 要求 | 成功 |
| --- | --- | --- |
| GET `/store/customers/{customerId}/contacts` | cursor、size（任意） | 200、CursorPage&lt;ContactResponse&gt; |
| POST `/store/customers/{customerId}/contacts` | ContactInput | 201、ContactResponse |
| PUT `/store/customers/{customerId}/contacts/{contactId}` | ContactInput（全項目必須） | 200、ContactResponse |
| DELETE `/store/customers/{customerId}/contacts/{contactId}` | 本文なし | 204、本文なし |
| PUT `/store/customers/{customerId}/contact-preferences/{type}` | `{contact_id: string \| null}`（キー必須） | 204、本文なし |
| GET `/store/customers/{customerId}/contact-history` | cursor、size（任意） | 200、CursorPage&lt;ContactHistoryResponse&gt; |

連絡先一覧は有効行のみを id 昇順で返す。履歴は occurred_at DESC, id DESC。各カーソルは同じ順序のキーで比較する。統合後の存続顧客では移動した連絡先の過去履歴も参照できる。

追加時は preferred=false。優先端点は PHONE / EMAIL / LINE ごとに既存指定を解除して指定行へ切り替え、null はその種類の指定を解除する。指定行が違う種類なら 400。種類ごとに最大一件とし、優先未指定でも別行を自動選択しない。削除時は優先指定も解除する。優先行の種類変更では指定を維持するが、変更先種類に別の優先行があれば 409（先に明示解除を求める）。同一内容への更新は成功し、実変更がなければ履歴を増やさない。

削除は論理削除で履歴を保持する。削除済み連絡先の読取・変更・再削除は 404。書込み対象に統合済み顧客 ID を指定した場合は 409 として存続顧客の再取得を求める。読取りは既存の旧顧客 ID の一跳解決に揃える。

## 既存顧客 API の変更

- POST `/store/customers` は既存の顧客資料に `contacts?: ContactInput[]` を追加する（省略または空配列で連絡先なし、null 不可、初回一括登録は最大 100 件）。顧客と初回連絡先と履歴を原子的に作成する。全件優先未指定で始める。成功は 201、CustomerResponse。
- PUT `/store/customers/{id}` は顧客資料だけを更新する。連絡先の一括置換は提供せず、独立した連絡先端点を使用する。成功は 200、CustomerResponse。
- 作成・更新・詳細・一覧の型から旧 `phone_number`、`phone_number2`、`line_id` を撤去する。旧入力キーを受け付ける互換処理は設けない。
- CustomerResponse と CustomerSummaryResponse は `preferred_contacts: ContactSummary[]` を必須で返す。最大三件、未指定の種類は含めない。全連絡先はページ付きの独立端点から読む。その他の既存資料項目・省略規則は維持する。
- GET `/store/customers` の Page、search、classification を維持する。検索は旧固定欄に代えて有効な連絡先を参照し、削除済み行を対象外とする。名前検索も維持する。
- DELETE `/store/customers/{id}` は既存の 204 を維持する。連絡先履歴が一件でもあれば、有効行が零でも理由付き 409。受注参照・統合関与による既存の削除制約も維持する。

## 統合・重複候補

- GET `/store/customers/merge-comparison?ids=...` は既存の二件比較を維持し、旧固定連絡先欄を `preferred_contacts: ContactSummary[]` に置き換える。全連絡先の確認は各顧客のページ付き連絡先端点を使う。
- GET `/store/customers/duplicates` は今回、優先 PHONE 同士の候補検出とする。グループの一致値を `matched_value: string`、種類を `matched_type: PHONE` とし、既存の total と customers と CursorPage を維持する。非優先・全種類の候補拡張は後続票。
- 連絡先情報を返す duplicates と merge-comparison は `CUSTOMER_MANAGE` と `CUSTOMER_MERGE` の両権限を要求する。
- POST `/store/customers/{customerId}/merges` は既存の `{merged_customer_id: string}` と 200 応答を維持し、両権限を要求する。両顧客に同じ種類の優先指定がある場合は、同値でも理由付き 409。画面で対象を示し、人が優先指定を解除して再実行する。統合内で勝手に選ばない。
- 統合は有効・削除済みの全連絡先を ID・origin_customer_id・履歴ごと保持し、同値行も残す。片側だけの優先指定は維持する。既存の会員関連・受注付替えと一体で確定する。
- GET `/store/customers/{customerId}/merges` の統合履歴閲覧は既存の `CUSTOMER_MERGE` と応答・ページングを維持する。詳細な資料選択・統合監査は後続票で拡張する。

## 受注・申請と公開範囲

#956 で導入済みの customer_selection と contact_snapshot を維持する。新規顧客選択は名前だけで連絡先なしの顧客を作り、受付時の写しを台帳へ自動登録しない。明示的な台帳連絡先の管理は CUSTOMER_MANAGE を持つ店舗画面から行う。認証済み会員の顧客整備、公開・会員申請の HTTP 契約を変更せず、内部資料や履歴を公開 DTO に加えない。

受注の `/store/orders/customer-candidates` は既存の応答形を維持し、phone_number を新正本の優先 PHONE から投影する。CUSTOMER_MANAGE がなければ電話は null とし、連絡先の検索にも使わない。ORDER_MANAGE による顧客の名前と ID の選択は維持する。

## 検証境界

HTTP 統合テストと実 PostgreSQL で CRUD、正規化、権限・店舗隔離、優先切替、履歴・削除競合、統合の ID と履歴保持、原子性を確認する。ページテストと日本語 E2E で作成・編集・優先指定・履歴・削除拒否を確認する。実ブラウザで狭幅、長い値、両テーマ、キーボード、失敗後の回復を確認し、Taskfile の lint・test・build・e2e の終了コードを記録する。
