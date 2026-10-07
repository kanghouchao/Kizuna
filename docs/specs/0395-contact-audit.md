# 顧客連絡先5入口の共通監査接続

## 範囲と基点

実装基点は認証失効修正#999を含む `9351275ea034fedd96d35f5968f2d335196cc6cd`。2026-10-07に5入口・安全な項目・同一取引と検証境界が承認された。`CustomerContactService` の人工操作5入口と、その操作に伴う同サービス内の状態変更に限定する。既存HTTPのDTO・権限・状態遷移・応答コード・ページング・スキーマは変更しない。

| 入口 | 既存HTTPと成功コード | 共通監査action |
| --- | --- | --- |
| `create` | POST `/store/customers/{customerId}/contacts`、201 | `CUSTOMER_CONTACT_CREATED` |
| `update` | PUT `/store/customers/{customerId}/contacts/{contactId}`、200 | `CUSTOMER_CONTACT_UPDATED` |
| `changePermission` | PUT `/store/customers/{customerId}/contacts/{contactId}/permissions/{purpose}`、200 | `CUSTOMER_CONTACT_PERMISSION_RECORDED` |
| `delete` | DELETE `/store/customers/{customerId}/contacts/{contactId}`、204 | `CUSTOMER_CONTACT_DELETED` |
| `prefer` | PUT `/store/customers/{customerId}/contact-preferences/{type}`、204 | `CUSTOMER_CONTACT_PREFERENCE_CHANGED` |

全入口の既存権限は `CUSTOMER_MANAGE`。親顧客を悲観ロックし、店舗filterで隔離する。統合済み顧客は409、存在しない/他店の顧客・連絡先は既存の404、権限拒否は403、入力/状態不正は400を維持する。`CustomerService.create` が初期連絡先をこの `create` に委譲する経路も同じ監査対象に含むが、顧客そのもののCRUD監査は本切片の完了範囲に含めない。

## 保存する安全な項目

共通sinkの主体・店舗・操作日時・結果に加え、targetは `CUSTOMER_CONTACT` と連絡先ID、sourceは `CUSTOMER_CONTACT_HISTORY` と今回保存する既存業務履歴IDにする。既存の履歴・許諾根拠の正本を維持し、新しい独自監査表や原文のコピーは作らない。

before/afterの状態は `version`、`customer_id`、`origin_customer_id`、`type`、`preferred`、`deleted`、`business_status`、`marketing_status` のみ。createのbeforeは空。afterの関連情報は `operation_id`、許諾操作の `purpose`、制約継承の `source_contact_id` に限定する。`redacted_fields_changed` は値を比較した結果の固定項目名 `value` のみを保持し、値そのものは保存しない。許諾根拠は履歴IDから参照し、根拠を記録した操作であることはactionとpurposeで表す。

電話番号・メール・LINE ID、正規化値、マスク値・digest・hash、許諾のsource/reason原文、顧客氏名・住所・備考、token・password・要求/entity全体は保存しない。緊急昇格IDは既存の `BusinessAudit` が検証済み認証から設定し、callerから受け取らない。

## 複数行と変更なしの扱い

- 編集・削除による共同制約の継承で実際に許諾状態が変わった各連絡先も `CUSTOMER_CONTACT_RESTRICTION_INHERITED` として記録する。元操作と同じ既存 `operation_id` を関連付け、継承元IDを残す。新しい同意として扱わない。
- 優先切替は旧優先の解除と新優先の指定を各対象のbefore/afterとして記録し、同じ `operation_id` で束ねる。解除→flush→指定という既存の部分一意索引維持手順を保つ。
- 同じ正規化値への編集、同じ優先指定、状態不変の制約継承には新たな共通成功監査を作らない。既存業務履歴の生成仕様自体は変えない。
- `changePermission` は状態が同じでも、新しいsource/reasonを伴う根拠記録が既存業務履歴として保存されるため、その新しい履歴IDを一度監査する。状態変更を偽装せずbefore/afterは同じ値のまま記録する。
- 書込みは同じ取引でflushした後の実際のversionと履歴IDを使う。処理中の副作用・共同制約の各beforeは変更前に固定する。成功監査の失敗で業務・既存履歴・関連行をすべてrollbackする。失敗/競合/拒否には成功監査を残さない。

## 他作業との境界

`transfer` と顧客統合は既存の統合監査を維持して対象外。`GuestContactImports`、`CustomerProvisioningService`、`CustomerMemberLinkService`、会員退会/ポイント/認証、招聘/添付、通知配信/口コミ、cast/在籍には触れない。既存 `record` helperを通るだけでtransferまで監査対象になる変更は避ける。

製品コードの予定変更は `CustomerContactService` と顧客連絡先専用のsnapshot/記録helperに限定する。既存ドメイン・controller・DTO・migration・権限目録・公共sinkは変更しない。テストはこのサービスの単体、snapshot、専用PostgreSQL、および独立した監査E2Eを追加する。E2E共通helperが必要なら担当に確認し最小限とする。

## 検証予定

5入口、初期連絡先委譲、同値編集、同じ優先指定、同じ状態への根拠追加、重複制約継承の変更あり/なし、優先切替両側を単体で確認する。実PostgreSQLでは業務/既存履歴/監査の同時commit、監査保存拒否時と外側取引失敗時の全rollback、複数行優先切替の部分一意制約、flush後のversionを検証する。

通常STAFFと緊急昇格による実HTTP操作から監査を照会し、主体・店舗・昇格ID・履歴由来・operation関連・前後値・秘密非混入を検証する。拒否/他店/統合済み・不正入力の成功監査が増えないこと、既存連絡先UI/顧客統合/通知シナリオの回帰を確認する。Standards/Specレビュー後、Task lint/test/build/e2eを終了コードで判定する。重いE2Eは共有窓の割当後だけ実行する。
