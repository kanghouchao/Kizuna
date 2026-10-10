# サービス設定と本人の意思変更の共通監査

公開票 [#1031](https://github.com/kanghouchao/Kizuna/issues/1031)、親 #395。基点 `6f2704e12804b42914798c0c69b85f489a02984f`。2026-10-10の継続指示により、既定の業務意味・HTTP・権限を変えず4書込入口を接続する。

## 背景 / 目的

ServiceSettingsServiceの作成・編集・論理削除とOwnServiceConditionService.decideは既存の版本／意思履歴を保存するが、共通BusinessAuditへ未接続。唯一のBusinessAudit/AuditWriterへ同じ取引で追記し、通常STAFF・本人CAST・緊急昇格の実主体を追跡する。

## スコープと記録契約

- 設定: SERVICE_CREATED / SERVICE_UPDATED / SERVICE_DELETED、target SERVICE/id、source SERVICE_REVISION/今回の既存版本id。白名簿はexists、deleted、version（flush後の実JPA版）、revision_number、terms_version。作成beforeは空。論理削除後も行は存在するためexists=true、deleted=trueと実versionを記録する。
- 編集: 実変更した固定名のみredacted_fields_changedへ安定順で記録する（name、duration_minutes、charge_type、price、remuneration）。値は記録しない。同値編集は既存版本も監査も増やさない。
- 本人選択: SERVICE_CONSENT_CHANGED、target SERVICE_CONSENT/実consent id、source SERVICE_CONSENT_EVENT/今回の既存event id。白名簿はexists、version、revision_number、terms_version、decision、service_id、enrollment_id、service_revision_id。初回beforeは空。同じ意思・同じ条件版は既存仕様どおり無変更で監査を増やさない。条件改定後の再確認は実変更として記録する。
- 名称・自由文・健康／性嗜好を含むサービス内容・価格／時間／報酬の値・独自値・hash・要求/entity全体は共通監査に複写しない。変更名は固定列挙だけを使い、既存正本を参照する。
- 店舗行→service行のロックとstoreFilter、本人の現行在籍照合、expectedVersion/termsVersion/consentVersion照合を維持する。保存後flushで実version・既存履歴IDを得る。拒否の注文履歴副作用を含め、業務・版本・意思・注文履歴・監査が全て確定するか全てrollbackする。内部helperを別の利用者操作として二重計上しない。

## HTTP契約の維持

`/store/services` POST201、`/{id}` PUT200、DELETE204（query expected_version）、GET一覧／詳細／revisions 200はSERVICE_MANAGE。作成はkind/name/duration_minutes/charge_type/price/remuneration、更新はkind以外＋expected_version、応答DTOは変更しない。

`PUT /platform/me/service-conditions/{id}/consent?store_id=...` はROLE_CAST、terms_version/consent_version/decisionを受け既存summaryを200で返す。GET一覧200。同値は200、古い版409、無効入力／削除済み設定編集400、不在／異店／本人在籍なし404、資格／権限拒否403、未認証401の既存契約を保つ。新しいendpoint・権限・schema・UIは作らない。

## 受け入れ基準

- [x] 設定3入口と本人選択を唯一の共通sinkへ接続し、実対象と既存版本／意思イベントへ辿れる
- [x] 最小白名簿と実version、固定変更名だけを記録し、内容・自由文・hashを複写しない
- [x] no-opと拒否は成功監査を増やさず、論理削除後も以前の監査を保持する
- [x] 実主体／緊急昇格、店舗隔離、本人在籍・権限・各版照合と既定の業務効果を維持する
- [x] 実PGで競合、監査失敗・内外取引rollbackと拒否の注文副作用の原子性を確認する
- [ ] サービス→実sink・実PG・HTTP、Task lint/test/build/全量E2E（型検査含む）、独立reviewと同HEAD CI/Codexが成功する

## 検証境界

公開サービス→実BusinessAudit/AuditWriter（DBのみmock）から始める。独立使い捨てPostgreSQL18/JDK25で実行する条件付き試験は通常CIのskipと区別する。実HTTPは専用STAFF・CAST・拒否主体・昇格を使い、既存service-settings/service-consent UIを全量E2Eで回帰する。DB・HTTPの拒否／rollback・同値・実ロック待機を検証する。

## 対象外

同意の意味／採用条件／本人拒否の業務効果の変更、過去監査補填、新しい機微情報収集、他設定・招待・物理削除、実環境の授権・デプロイ、親 #395 の閉鎖。合流とmaster同期は親担当。

## 独立PostgreSQL検証の起動

専用の使い捨てPostgreSQL18へ`KIZUNA_SERVICE_AUDIT_TEST_JDBC_URL`を指定し、JDK25で`backend/gradlew -p backend test --tests '*ServiceAuditPostgresTest' --rerun-tasks`を実行する。試験用schemaはcreate-dropで生成されるため、開発／運用DBを指定しない。未指定時の12件skipは独立実測の成功と区別する。

## ローカル検証結果

公開サービス→実BusinessAudit/AuditWriter3件、独立実PG12件（skip/failure/error 0）、Task lint/test/buildはexit 0。HTTP fixture/helper修正後のTask E2E型検査もexit 0。focused実HTTPは1件・retry 0、全量Task E2Eは104件・retry 0でexit 0。前置の全E2E型検査と既存service-settings/service-consent UIも成功した。Specレビュー0、Standards最終0（共通HTTP helperの指摘は修正・再確認済み）。

専用PGとE2Eスタックは回収済み。同一HEAD CI/CodexとPR合流は未完了で、最新状態は公開票／PRに記録する。親 #395 はOPENを維持する。
