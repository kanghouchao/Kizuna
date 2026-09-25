# ゲスト申請の同意取得・取り込み — HTTP 契約案

状態: 承認済み（2026-09-25）。対象: #962、増補仕様 #955。先行する #961 は完了済み。

## 共通規則

既存の申請・確定・顧客関連を拡張する。JSON キーは snake_case、ID は string、操作者 ID は integer (int64)、日時はオフセット付き ISO 8601 string。店舗は既存の `X-Role: store` と `X-Store-ID` で解決する。公開画面では訪問ドメインから解決し、要求本文に店舗 ID を受けない。

同意は申請時の全連絡先（電話・メール・LINE）を対象とする。氏名と一種類以上の有効な連絡先を必須とし、原文と正規化値を保存する。業務許可は今回の申請・そこから生成される受注だけに限定する。販促は別のチェックボックスで、既定は未選択。

## 端点

| メソッド・パス | 要求・変更 | 成功 | 授権 |
| --- | --- | --- | --- |
| GET /store/order-applications/public/contact-consent | 本文なし。現在の同意文面を取得 | 200、下記文面応答 | 匿名、店舗文脈必須 |
| POST /store/order-applications/public | 既存要求に `contact_consent` を追加 | 201、既存の `{id: string}` のみ | 匿名、既存の流量制限 |
| GET /store/order-applications/{id} | 新設。既存の店舗申請項目と同意詳細を取得 | 200、下記詳細応答 | ORDER_MANAGE |
| POST /store/order-applications/{id}/confirmation-preview | 既存要求に `contact_imports` を追加 | 200、既存の試算応答に取り込み結果を追加 | ORDER_MANAGE、条件付き CUSTOMER_MANAGE |
| POST /store/order-applications/{id}/confirmation | 試算と同じ要求 | 201、既存の受注詳細（id を含む） | 同上 |

公開 GET の `public` は既存の予約済みリテラル。匿名ハンドラを明示し、文面 GET と申請 POST は壊れた Bearer にも依存しない。CSRF 除外は書き込みの実メソッド・パスだけに適用する。申請・確定以外の会員 API は変更しない。

## 文面と取得証拠

文面応答は必須の `version: string`、`business_text: string`、`marketing_text: string`。サーバーが文面の正本を持つ。

- 業務: 「入力した連絡先を、この予約申請と成立した予約に関する確認・変更などの連絡に使用することを許可します。」
- 販促: 「入力した連絡先を、この店舗のキャンペーンやサービスの案内に使用することを許可します（任意）。」

公開 POST の `contact_consent` は必須・null 不可。各フィールドも必須・null 不可。

| 項目 | 型・条件 |
| --- | --- |
| version | string、取得した現行文面の版と一致 |
| business_allowed | boolean、true 必須 |
| marketing_allowed | boolean、未選択時 false |

版不一致は 409。UI は入力を保持し、文面を再取得してチェックを解除し、再同意を求める。任意の文面や取得時刻をクライアントから受けず、受理時にサーバーが申請 ID・原文連絡先・正規化値・文面の版と全文・両選択値・取得日時を同じトランザクションで保存する。以後の文面変更や台帳更新でこの証拠を変更しない。

## 店舗申請詳細

既存の `OrderApplicationResponse` の項目を含む専用詳細 DTO に、次を追加する。一覧 DTO には追加しない。

- `contact_consent`: ゲストでは必須の証拠オブジェクト、会員では省略。`version: string`、`business_text: string`、`marketing_text: string`、`business_allowed: boolean`、`marketing_allowed: boolean`、`acquired_at: string`。申請 ID と原文連絡先は既存の `id` と `contact_snapshot` によって対応する。
- `business_contact_permissions`: 必須の配列、最大 3 件。各要素は `type: PHONE | EMAIL | LINE`、`value: string`（申請の正規化値）、`status: UNKNOWN | ALLOWED | DENIED`、`decision: ALLOWED | STORE_DENIED | NOT_ALLOWED`。会員申請では空配列。確認前後とも現在の全店拒否判定を行う。
- `contact_imports`: 必須の配列、最大 3 件。未確認・未取り込みでは空配列。確定後は下記の結果に `contact_id: string`、`recorded_by: integer | null`、`recorded_at: string` を加えた確定記録。実行者削除後も証拠を維持する。

公開応答は受付 ID のみ。会員・公開 DTO には顧客の内部評価・注意事項・統合監査情報、拒否した顧客の情報のキーを追加しない。

## 顧客・連絡先の明示選択

ゲスト確定では既存の `customer_selection` を必須とする。

- EXISTING: `{mode: "EXISTING", customer_id: string}`。
- NEW: `{mode: "NEW", new_customer: {name: string}}`。名前は空白不可、最大 255 文字。CUSTOMER_MANAGE を要する。
- NONE: `{mode: "NONE"}`。顧客を生成しない。

異なる mode の項目の混在を拒否する。電話一致による自動選択・作成は行わない。認証済み会員申請の顧客整備は既存どおりで、会員申請には `customer_selection` と `contact_imports` を指定できない。

ゲストの `contact_imports` は必須・null 不可の配列、最大 3 件。空配列を「取り込まない」という明示選択に使う。同種類の重複・null 要素は拒否する。NONE では空配列だけを許可する。

| 要素の項目 | 型・省略条件 |
| --- | --- |
| type | 必須、PHONE / EMAIL / LINE。申請原文に存在する種類 |
| contact_id | string、省略・null は新規行。指定時は選択顧客の同種類・同正規化値の有効行 |
| import_marketing_consent | 必須 boolean。販促同意を今回取り込む明示選択 |

新規顧客では contact_id を指定できない。取り込み元の値は申請の保存済み原文から解決し、別の値を本文で受けない。新規行は両用途 UNKNOWN、優先指定なしで作成する。既存の行を電話一致で自動選択しない。非空の取り込み配列には CUSTOMER_MANAGE を要し、試算と確定の両方で検証する。

`import_marketing_consent=true` は保存済み `marketing_allowed=true` の場合だけ許可する。未選択の申請に true を指定すれば 400。false は既存の販促状態を一切変更せず、拒否とも扱わない。取り込みでは顧客台帳の業務許可を変更しない。

既存拒否を解除しないため、選択顧客の同種類・同正規化値の有効行に販促 DENIED があれば販促変更をスキップし、拒否を保持する。別顧客の業務拒否は下記の全店判定で適用する。拒否がない場合のみ選択対象行へ販促 ALLOWED を記録し、同値の他行を自動更新しない。同値行の UNKNOWN による共同制約も既存規則どおり維持する。

試算応答の `contact_imports` は必須の最大 3 件の配列（未取り込みは空）。各要素は `type`、`value: string`、`marketing_result: NOT_REQUESTED | ALLOWED | DENIED_PRESERVED`。確定時には最新状態で再検証し、結果の変更も既存の確認トークンの検証対象に含めて 409・再試算とする。試算は顧客・連絡先・履歴を作らない。

## 受注への写し・原子性

既存の `contact_snapshot` は省略可能。省略時は申請から受注の写しを作り、指定時は店舗が補正した写しを保存する。申請証拠は変更しない。今回の業務許可を移すのは申請と種類・正規化値が一致する受注の宛先だけで、追加・変更された宛先は UNKNOWN。台帳取り込みの有無・顧客の有無に依存しない。

受注の詳細・許可履歴は #961 の契約を利用し、出所・根拠には申請 ID と同意文面・取得日時への追跡を保持する。申請確認前後とも全未統合顧客の同値有効行を調べ、業務拒否が一件でもあれば STORE_DENIED とする。証拠保存自体は拒否しない。送信側は #961 の公開境界から毎回最新状態を判定する。

顧客・連絡先作成、販促許可、申請の取り込み記録、顧客の既存可否履歴、受注・今回の許可履歴、申請確定を一つのトランザクションで行う。販促履歴には `application_id: string`、同意全文・取得日時、担当者・記録日時、変更前後を保持する。既存 `GET /store/customers/{customerId}/contact-history` の応答に `application_id?: string` を追加し、ゲスト同意取り込みによる PERMISSION_CHANGE のみ必須とする。既存の source / reason と前後状態も維持する。途中失敗時は全体を戻す。

## 失敗・ページング

- 400: 同意欠落・業務未許可、型・値・組み合わせ不正、種類重複、申請と取り込み先の不一致、既存規則上の不正な遷移。
- 401: 保護端点での未認証。403: 店舗アクセス・ORDER_MANAGE 不足、必要な CUSTOMER_MANAGE 不足。
- 404: 申請・顧客・指定連絡先の不存在またはスコープ外。
- 409: 文面の版不一致、試算後の条件変更、台帳ロック等の競合。入力を保持して再取得・再試算を案内する。
- 429: 公開申請の既存流量制限。

エラー形式は既存の `{error: string, details?: Record<string, string>}`。新規端点は単一資源でページングなし。申請一覧は既存 CursorPage（size 既定 20、1〜2000 に制限）を維持する。顧客連絡先・履歴も既存の上限付き CursorPage と順序を維持し、全件取得用の別 API は作らない。

## 承認後の検証

HTTP 統合テストと実 PostgreSQL を主境界にし、公開同意・権限・店舗隔離・未取り込み・顧客未設定・既存拒否・宛先変更・途中失敗の原子性と内部情報キーの不在を検証する。ページテストと日本語 E2E を追加し、実ブラウザで狭幅・長い値・両テーマ・キーボード・失敗からの回復を確認する。最終検証は Taskfile の lint・test・build・e2e。code-review の Standards / Spec 両軸レビュー後にコミットする。

## 送信側の参照

確認前の申請には `order::contact` の `OrderApplicationBusinessContact.decide(applicationId, type)` を使う。授権済み店舗文脈で保存済み宛先と同意を読み、全店拒否を毎回照会して宛先と判定を返す。受注成立後は `OrderBusinessContact.decide(orderId, type)` を使う。画面の判定を配信権として再利用せず、送信・再送直前に再判定する。実送信は #394 の範囲。
