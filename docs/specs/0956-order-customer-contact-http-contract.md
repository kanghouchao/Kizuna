# 受注の顧客選択と受付時連絡先 — HTTP 契約

状態: 承認済み（2026-09-20）。対象: #956。

## 境界

顧客選択と受付時連絡先を独立した入力にする。電話の一致による顧客選択・作成を廃止する。顧客台帳の固定連絡先欄、連絡可否、同意取り込みは本票で変更しない。旧受注入力の互換層・データ移行は設けない。

以下は既存契約からの変更分であり、日付・コース・指名・明細・試算トークンなどの既存項目と検証は継続する。

## 入力型

### 顧客選択 `customer_selection`

オブジェクト型。店舗の作成とゲスト申請の確認では必須。受注編集では省略・null は変更なし。

| キー | 型・必須性 | 意味 |
| --- | --- | --- |
| `mode` | 必須文字列: `EXISTING` / `NEW` / `NONE` | 担当者による明示選択 |
| `customer_id` | 文字列。EXISTING の場合だけ必須 | 同店の未統合顧客 ID |
| `new_customer` | オブジェクト。NEW の場合だけ必須 | `{ "name": string }`。名前は空白不可・最大255文字 |

別モードの項目の併記は400。NONEは顧客参照なし。NEWは連絡先なしの顧客を受注保存と同一トランザクションで作成する。受付時連絡先から台帳への暗黙転記はしない。

認証済み会員申請の確認では `customer_selection` は省略・null のみ許可する。顧客は認証済み本人と現在の会員関連から解決・作成し、担当者による差し替えは400。会員申請由来の受注編集でも顧客差し替えを拒否し、同じ顧客の据え置きだけ許可する。

### 受付時連絡先 `contact_snapshot`

オブジェクト型。顧客の有無と独立する。各キーは省略・null 可。

| キー | 型・上限 | 意味 |
| --- | --- | --- |
| `name` | 文字列、255文字 | 受付時に名乗った名前 |
| `phone_number` | 文字列、50文字 | 今回の電話番号 |
| `email` | 文字列、254文字、有効なメール形式 | 今回のメール |
| `line_id` | 文字列、255文字 | 今回のLINE ID |

電話・メール・LINE は各1件を同時に保存できる。顧客連絡先IDは受け取らない。電話は日本番号として検証し +81 形式へ正規化、メールは前後空白とドメイン部の大小文字だけを正規化、LINEは前後空白だけ除去する。申請では受理した原入力も保持し、正規化や確認によって原入力を上書きしない。

- 直接作成: 省略・null・空オブジェクトは連絡先なし。
- 編集: 省略・null は変更なし。オブジェクトを送ると全体を置換し、空オブジェクトは全項目を消去する。顧客付き受注でも編集可能。既存の終端状態編集禁止と `expected_version` を適用する。
- 申請確認: 省略・null は申請からコピー。オブジェクトは確定後の受注の写しだけを置換し、申請原文は不変。空オブジェクトは受注の連絡先なし。
- 公開ゲスト申請: オブジェクトと `name` は必須。電話・メール・LINE のうち1件以上を必須とし、電話だけを必須にはしない。既存の折返し先を求める境界を維持する。直接作成の受注にはこの必須条件を課さない。

## 端点・応答

| メソッド・パス | 要求・変更 | 成功 |
| --- | --- | --- |
| `POST /store/orders` | 上記選択と写し。旧 `customer_id`, `customer_name`, `phone_number`, `phone_number2`, `classification`, `landmark`, `has_pet`, `ng_type`, `ng_content` を入力から撤去。`address`, `building_name` は派遣先だけに使用 | 201、IDを含む受注詳細 |
| `POST /store/orders/preview` | 作成と同じ入力。顧客を作成しない | 200、既存の試算応答 |
| `PUT /store/orders/{id}` | 選択と写しを追加。旧 `contact_name`, `contact_phone_number` を撤去 | 200、受注詳細 |
| `POST /store/orders/{id}/preview` | 編集と同じ入力。更新・顧客作成なし | 200、既存の試算応答 |
| `POST /store/order-applications/{id}/confirmation` | 上記選択と写し。旧 `customer_id`, `new_customer` を撤去 | 201、生成した受注詳細 |
| `POST /store/order-applications/{id}/confirmation-preview` | 確認と同じ入力。更新・顧客作成なし | 200、既存の試算応答 |
| `POST /store/order-applications/public` | 旧 `contact_name`, `contact_phone_number` を `contact_snapshot` へ置換 | 201、`{ "id": string }` のみ |
| `GET /store/orders/{id}` | 旧連絡先2項目を `contact_snapshot` へ置換 | 200、受注詳細 |
| `GET /store/order-applications` | 旧連絡先2項目を `contact_snapshot` へ置換。申請原入力を表示 | 200、既存のCursorPage |
| `GET /store/orders/customer-candidates` | 新設。`search?: string`, `cursor?: string`, `size?: integer`（既定20、範囲1〜100） | 200、CursorPage |

受注詳細は `customer_id: string | null` と `customer_name: string | null` を維持する。`contact_snapshot` は常にオブジェクトで返し、値なしの各キーはnull。顧客台帳の更新・連絡先削除・顧客統合では写しを更新しない。受注一覧には新しい連絡先PIIを追加しない。

顧客候補は同店の未統合顧客を既存の氏名・固定電話欄で検索し、ID昇順の全順序で辿る。項目型は `{ id: string, name: string | null, phone_number: string | null }`。CursorPageは既存の `{ content: T[], next_cursor?: string }` 形式を使用し、続きがなければ `next_cursor` を省略する。候補検索は自動選択を行わず、台帳の内部評価・注意事項・統合監査情報を返さない。複数連絡先の横断検索は後続票の範囲。

予約受付箱の既存 `statuses`, `cursor`, `size` とページング上限は維持する。会員の `/platform/me/order-applications` の入力・応答は変更しない。会員申請の確定では名乗りを写しへ保持し、台帳やログイン情報から連絡先を暗黙補完しない。

## 権限と失敗

- 公開申請以外の上記端点は `ORDER_MANAGE` と店舗へのアクセス権を必須とする。店側の `X-Role: store` / `X-Store-ID` を維持する。
- 手動の NEW は追加で `CUSTOMER_MANAGE` を必須とし、試算時と保存時の両方で検証する。UIも同じ制約に従う。EXISTING/NONEと写しの保存は `ORDER_MANAGE` で可能。
- 会員本人の申請に基づく既存の顧客自動整備は手動NEWと区別し、追加の `CUSTOMER_MANAGE` を求めない。既存の並行確定時の収束・失敗時のロールバックを維持する。
- 公開申請は匿名アクセスと既存のCSRF/Bearer例外、店舗解決、流量制限を維持する。
- 400: 形式・モード不整合、不正電話/メール、終端状態編集、会員の顧客差し替え、処理済み申請の再確認などの既存状態違反。
- 401: 認証失敗。403: 権限不足・店舗アクセス拒否。404: 不在・店舗作用域外・統合済みの選択先。
- 409: 古い `expected_version`、失効した試算、会員関連の並行競合が再試行後も収束しない場合などの既存競合。
- 429: 公開申請の流量制限。エラーは既存の `{ "error": string, "details"?: object }` と日本語文言。

## 承認後の検証

HTTP統合テストと実PostgreSQLで3モード、電話一致による自動関連なし、顧客あり/なしの全種類の写し、台帳変更・統合後の不変性、ゲスト原文保持、会員の並行確定・ロールバック、店舗隔離と権限を検証する。顧客の物理削除が既存の受注参照制約で拒否される場合は、その拒否と写し保持を確認する。

ページテストと日本語E2Eで明示選択・入力保持・失敗時の回復を確認する。変更画面は実ブラウザで狭幅・長い値・両テーマ・キーボード操作を確認する。Taskfileのlint・test・build・e2e、Standards/Specの独立レビュー後にコミットする。
