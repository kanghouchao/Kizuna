# 顧客CRUD 3入口の共通監査接続

## 状態と基点

2026-10-07、master `662f5506058e7352cdead3501b72fb21285ed221` を確認。3入口・許可項目・原子性と既存3検証境界は承認済み。親 #395 はOPEN、連絡先 #1000 / PR #1001 は合流済み。顧客サービスと公共sinkについて43540abaから合流masterまでの差分はない。

## 入口ホワイトリスト

| 入口                   | 現行HTTP                     | 成功                 | action           |
| ---------------------- | ---------------------------- | -------------------- | ---------------- |
| CustomerService.create | POST /store/customers        | 201 CustomerResponse | CUSTOMER_CREATED |
| CustomerService.update | PUT /store/customers/{id}    | 200 CustomerResponse | CUSTOMER_UPDATED |
| CustomerService.delete | DELETE /store/customers/{id} | 204・本文なし        | CUSTOMER_DELETED |

3入口はCUSTOMER_MANAGE、既存StoreScopedと単店コンテキストを維持する。401認証拒否、403権限拒否、404不存在/他店舗、400検証不正、409統合済み/削除保護/競合の既存写像を維持。新HTTP、DTO、権限、ページング、schema変更はない。createには初期contacts（既存max100）が任意で、name必須。updateのname/address/building_name/landmark/classification/has_pet/usage_areas/ng_type/ng_contentは従来どおりnullで変更しない。成功DTOの私的項目も現行契約のままであり、監査へ全体を転記しない。

## 公共sinkの許可項目

主体・店舗・action・SUCCESS・時刻は既存BusinessAuditへ委譲。targetはCUSTOMERと顧客ID。独自の業務履歴は存在しないためsource_type/source_idはnullとし、履歴IDを捏造しない。昇格IDは認証済みセッションから既存sinkが関連付ける。

- create：beforeは空。afterはexists=trueとflush後の実version。redacted_fields_changedには実際に非nullで保存した属性の固定名のみを載せる。
- update：before/afterはexists=trueと実version。変更判定はpatch適用前後の実値比較。実変更のある固定名だけをafter.redacted_fields_changedへ安定順に載せる。nullパッチ/同値updateは新たな成功監査を作らない。
- delete：beforeはexists=trueと削除前version。afterはexists=false。存在しない削除後versionを作らず、物理削除後もtarget IDで監査を保持する。

固定属性名は name/address/building_name/landmark/classification/has_pet/usage_areas/ng_type/ng_content の9つ。値・マスク・hash/digest・住所・NG内容・顧客名・分類文字列・要求/entity全体は記録しない。has_petを含む顧客属性値は本切片では一律転記しない。merged_into_idはこの3入口の変更対象ではなく、統合の既存監査に委ねる。

## 原子性と初期連絡先

createは初期連絡先の作成まで成功した後に顧客の成功監査を記録する。既存連絡先監査は従来どおり各連絡先の履歴IDを持ち、customer_idとtarget顧客IDで関連を辿る。顧客1件の監査と連絡先N件の監査を混同・重複しない。顧客監査拒否、連絡先監査拒否、外側取引失敗のいずれも顧客/連絡先/既存履歴/全公共監査を同時rollbackする。

updateは保存flush後の実versionを記録する。既存楽観ロック・統合済み拒否・nullパッチの意味は変えない。deleteは現在の顧客行ロックと連絡先履歴/統合/受注参照の削除保護を維持し、削除flushが成功した後に監査する。audit失敗で削除もrollbackする。拒否・競合に成功監査を残さない。

## 検証境界

既存のサービス→実BusinessAudit/AuditWriterの単体、実Spring取引/Hibernate/PostgreSQL、実HTTP/E2Eから監査API照会の3境界を使う。新しい製品インターフェイスを試験のために増やさない。

単体：3入口、9属性の秘匿変更名、部分update/no-op、物理削除前snapshot、通常主体・昇格、拒否時無記録。
実PG：create/update/deleteと監査の同時commit、各audit失敗rollback、初期連絡先・既存履歴と複数監査の一括rollback、version0→1とno-op不変、DB削除保護。削除後も監査照会が可能。
実HTTP：通常STAFF/昇格の3入口、初期連絡先併用、権限403/他店404/統合済み409/連絡先履歴・受注参照の削除409、秘密非混入、読み/同値updateで監査不増。既存contact auditの件数はcontact action/targetで限定し、顧客監査の追加を誤って重複と判定しない。

Standards/Specレビュー、task lint/test/build/e2e、同一HEADの実CIjobsとCodex Completed+fresh公式bot👍+未解決0を完了する。重E2Eは既存の共有窓調整に従い、現在は起動しない。

## 変更予定と排他境界

製品変更はCustomerServiceと顧客専用snapshot helperだけ。テストはCustomerServiceTest、新しい顧客監査unit/PG/E2E、既存ContactAuditPostgresTestのconstructor接続と必要な件数限定。既存E2E helper拡張は最小限。#395ソース内coverage matrixは次PRで連絡先合流済みに更新する。

CustomerProvisioningService、CustomerMemberLinkService、統合transfer、ポイント/帰属/伝票、認証/退会、添付/招聘、口コミ/通知、cast/在籍には入らない。Store/service設定は後続切片。既存削除制約やupdateのロック方式を監査接続のついでに変更しない。

## 承認済み決定

承認済みの範囲は、この3入口だけを1つの縦切片として扱うこと、9属性値を公共sinkへ転記せず変更名に限定すること、exists/versionのbefore/after形、既存3検証境界の再利用。新規依存先なし（基盤・注文・連絡先はすべて合流済み）。grillingの判断枝は確定し、既存票テンプレートで独立子票を公開してTDDで実装する。
