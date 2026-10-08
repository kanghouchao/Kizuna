# キャスト在籍・公開プロフィールの6操作の共通監査

公開票: [#1010](https://github.com/kanghouchao/Kizuna/issues/1010)。技術契約承認済み、実装後の実PG・実HTTP検証は未完了。

## 背景 / 目的

親 #395 の業務監査接続のうち、キャスト在籍・公開プロフィールの作成、編集、公開切替、停止、再開、退店を同一取引の共通監査へ接続する。2実体の変更を実際のversionと安全な参照で辿り、任意入力や個人情報を監査へ複写しない。

2026-10-08、6つの非削除入口の技術契約は承認済み。基点はmaster `86be6e51c9eb59419e423e3fb40752b11489f4f7`。#1009から独立し、削除・級聯副作用は別契約として残す。

## スコープ

| 入口 | 操作名 | 対象・版本 |
| --- | --- | --- |
| CastService.create | CAST_ENROLLMENT_CREATED | 在籍ID。新在籍とprofileの実versionをそれぞれ保持、beforeは空 |
| CastService.update | CAST_ENROLLMENT_UPDATED | 在籍ID。enrollment_versionとprofile_versionを区別し、flush後の実値を保持 |
| CastService.changePublication | CAST_PROFILE_PUBLICATION_CHANGED | profile ID、sourceは在籍ID。profileだけの変更を記録 |
| CastEnrollmentService.suspend | CAST_ENROLLMENT_SUSPENDED | 在籍ID、既存状態履歴IDをsourceにする |
| CastEnrollmentService.resume | CAST_ENROLLMENT_RESUMED | 同上 |
| CastEnrollmentService.withdraw | CAST_ENROLLMENT_WITHDRAWN | 同上。ended_atを保持し、profileやShiftの未変更状態をイベントにしない |

既存のCAST_MANAGE、HTTPメソッド・成功/失敗・要求/応答、状態遷移、ロック順序を維持する。create201、update200、publication PATCH200、3状態操作POST200。新しい権限・schema・UI・期待version要求を追加しない。

在籍の許可値は内部ID、cast_id、status、ended_at、実version。profileは内部ID、enrollment_id、publication_status、実version。作成・編集はenrollment_version/profile_version/profile_idを明示する。名前・写真URL・紹介・年齢/身体情報・任意フィールド等はメモリ上で比較し、共通監査へは固定のredacted_fields_changedだけを送る。カスタム項目はinternal_custom_fields/public_custom_fieldsという固定名にまとめ、任意key・label・値・hashを複写しない。

業務的な同値入力で監査を増やさず、隠した入力だけの実変更は取り逃がさない。内部recordCreation/replaceInternalFieldsを新しい操作として重複計上しない。既存の状態履歴・内部フィールドsnapshotは正本のまま保ち、BusinessAudit/AuditWriterの主体・店・昇格情報を使う。

## 利用者の要求

1. 店舗担当者として、新しい在籍と公開profileを一つの作成操作として辿れる。
2. 店舗担当者として、内部情報と公開情報の別々の版本を正確に確認できる。
3. 店舗担当者として、公開切替と在籍状態の変更を区別できる。
4. 監査担当者として、停止・再開・退店を既存状態履歴へ辿れる。
5. 利用者として、名前・連絡先・任意入力・秘密を監査へ再保存されない。
6. 監査担当者として、同値操作と実変更を区別できる。
7. 利用者として、監査保存の失敗時は業務・履歴もまとめて元へ戻る。
8. 管理者として、通常担当者と緊急昇格を区別し、拒否・競合・rollbackが成功履歴にならない。

## 受け入れ基準

- [ ] 6入口を同一取引の既存監査sinkへ接続し、内部委譲を重複計上しない
- [ ] 2実体の実version、対象/由来ID、状態・時刻を最小白名簿で保持する
- [ ] 同値は監査なし、隠した入力だけの変更は固定項目名で記録する
- [ ] 個人情報・任意key/値・hash・token・添付内容・要求/entity全体を含めない
- [ ] 監査失敗・外側rollback・状態拒否・異店舗・権限拒否・並行競合で成功監査を残さない
- [ ] 通常STAFF/緊急昇格の実HTTP、実PGの原子性/版本、Task lint/test/build/e2e、双軸レビューと同一HEAD CI/Codexを確認する

## 検証方針

既存の公開サービス→実BusinessAudit/AuditWriter（DBのみmock）、実PostgreSQLの取引境界、実HTTPという3境界を継続する。CastService/CastEnrollmentService/Controllerの既存試験、cast-enrollment・cast-management-ui・cast-custom-fieldsのE2Eを回帰対象にする。普通試験は独立作業で進め、重型E2Eは親タスクから割当を受ける。#1009のE2E窓が来たら先に完了させる。

## 対象外（別 issue）

Cast削除と全級聯副作用、招待発行/受諾、カスタム項目定義CRUD、平台本人情報、Shift/申請の新しい変更、API/権限/業務挙動変更、実権限授与、デプロイ、#1009への積み重ね。親#395全体の完了宣言。

## 依存 / 参考

Blocked by: 実装の依存票なし。重型E2Eのみ親の共有窓割当待ち。#1009とは別ブランチ・別worktree。
親 #395。キャスト三層と在籍履歴の既存実装を維持する。所有者が手動でPRを合流する。
