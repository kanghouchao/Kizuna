# 文書と実装の照合記録

## 基準と範囲

- 照合日: 2026-09-11。基準コミット: `4d20cf83761455d95821fd801d707fe244d5e1f1`。
- 正本は作業開始時のワークスペース。開始時の差分は `docs/api-guidelines.md`、`docs/issue-859-api-design.md`、`docs/issue-863-api-design.md` の削除のみ。この 3 件は復元していない。削除済み本文は同コミットの Git 版を参照した。
- 変更は文書のみ。初回整理完了時は未コミット・未 push（PR 作成に伴う追加検証は文末を参照）。API、業務コード、CI のスキップ条件は変更していない。
- 「一致」は記載した主要規則をソース・既存テストと静的に照合した意味で、全実行経路の動作保証ではない。「文書の陳腐化」「実装の規範逸脱」「歴史資料の差」「証拠不足」を区別する。

## 文書ごとの処置

同一説明の重複は正本へのリンクへ置換。保留は有効な内容を維持する処置であり、未確認の実装を肯定する意味ではない。

| 文書 | 原文の主張・整理対象 | 実装・照合根拠 | 差異の性質 | 処置 |
| --- | --- | --- | --- | --- |
| [README.md](../README.md) | 起動・構成・開発命令が重複 | [Taskfile.yml](../Taskfile.yml)、[開発 Taskfile](../infrastructure/development/Taskfile.yml)・compose | 重複と起動条件の不足 | 圧縮・APP_DOMAIN と build → up を明記 |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | 英文 PR 章を CI が検査、coverage の説明が重複 | [CI](../.github/workflows/lint-and-test.yml)、[PR template](../.github/pull_request_template.md)、[Jest](../frontend/jest.config.cjs) | 文書の陳腐化 | 圧縮・実際の章名、ゲート、coverage 閾値へ訂正 |
| [SECURITY.md](../SECURITY.md) | 版表、仮連絡先、偽 PGP、生成器の尾書き | 基準コミットの SECURITY.md、[build.gradle](../backend/build.gradle) と package.json | 文書の陳腐化・外部運用は証拠不足 | 圧縮・既存メールと方針を保持、支援約束を追加しない |
| [AGENTS.md](../AGENTS.md) | Cast の在籍操作・履歴は後続作業、API 文書参照 | [CastEnrollmentService.java](../backend/src/main/java/com/kizuna/cast/application/CastEnrollmentService.java)、[PlatformCastService.java](../backend/src/main/java/com/kizuna/cast/application/PlatformCastService.java) | 文書の陳腐化・重複 | 圧縮・条件付き読み込みへ整理、安全・提出制約を維持 |
| [CONTEXT.md](../CONTEXT.md) | Member / Guest の将来記述、StoreContext が me を取得、Cast 旧構造 | [Member.java](../backend/src/main/java/com/kizuna/member/domain/Member.java)、[GuestOrderApplicationService.java](../backend/src/main/java/com/kizuna/order/application/GuestOrderApplicationService.java)、[StoreContext.tsx](../frontend/src/entities/user/model/StoreContext.tsx)、[MeContext.tsx](../frontend/src/entities/user/model/MeContext.tsx) | 文書の陳腐化 | 圧縮・概念と不変条件を保持、実装・決定の参照を分離 |
| [backend/AGENTS.md](../backend/AGENTS.md) | API 正本の削除、DB 手順と認証説明の重複 | [SecurityConfig.java](../backend/src/main/java/com/kizuna/auth/infrastructure/SecurityConfig.java)、[EndpointAuthorizationDeclarationTests.java](../backend/src/test/java/com/kizuna/EndpointAuthorizationDeclarationTests.java)、削除前 API 文書 | 統合・既存逸脱あり | API contract を単一正本にする。既存逸脱は次節に残す |
| [backend/src/main/resources/db/AGENTS.md](../backend/src/main/resources/db/AGENTS.md) | 親文書に DB 手順の要約がある | [build.gradle](../backend/build.gradle)、[changelog](../backend/src/main/resources/db/changelog/) | 参照の陳腐化 | 保留・親の指示は本書へのポインタと明記 |
| [frontend/AGENTS.md](../frontend/AGENTS.md) | API 型名まで snake_case、me と店舗文脈が混在 | [StoreContext.tsx](../frontend/src/entities/user/model/StoreContext.tsx)、[MeContext.tsx](../frontend/src/entities/user/model/MeContext.tsx)、[API types](../frontend/src/entities/user/) | 文書の陳腐化・重複 | 圧縮・型は PascalCase、通信プロパティは snake_case。視覚規範は DESIGN へ |
| [frontend/DESIGN.md](../frontend/DESIGN.md) | 完了済み移行手順、token 値の重複、ログイン検証の移行予定 | [globals.css](../frontend/src/app/globals.css)、[button.tsx](../frontend/src/shared/ui/button.tsx)、[PlatformLoginForm.tsx](../frontend/src/features/platform-login/ui/PlatformLoginForm.tsx)、[通知](../frontend/src/shared/notify/) | 文書の陳腐化・重複 | 圧縮・視覚と相互作用の規範を保持。色比は既存測定記録と明記 |
| [infrastructure/AGENTS.md](../infrastructure/AGENTS.md) | 環境別 compose、非公開ポート、host 判定、E2E 分離 | [development](../infrastructure/development/docker-compose.yml)、[release](../infrastructure/release/docker-compose.yml)、[storeResolver.ts](../frontend/src/shared/lib/proxy/storeResolver.ts) | 主要設定と一致 | 保留。作業ツリー名の衝突可能性は次節 |
| [e2e/AGENTS.md](../e2e/AGENTS.md) | 直列実行、復元 sentinel、隔離環境 | [設定](../e2e/playwright.config.ts)、[compose](../e2e/docker-compose.e2e.yml)、[steps](../e2e/steps/) | 主要設定と一致 | 保留・安全な復元条件を維持 |
| [e2e/README.md](../e2e/README.md) | createBdd の API が Given / もし / ならば | [steps](../e2e/steps/)、[設定](../e2e/playwright.config.ts) | 文書の陳腐化 | 訂正・Given / When / Then と日本語シナリオを区別 |
| [CLAUDE.md](../CLAUDE.md) | 単行の @AGENTS.md ローダー | 同じディレクトリの AGENTS.md が存在 | 一致 | 保留・正文をコピーしない |
| [backend/CLAUDE.md](../backend/CLAUDE.md) | 単行の @AGENTS.md ローダー | 同じディレクトリの AGENTS.md が存在 | 一致 | 保留・正文をコピーしない |
| [backend/src/main/resources/db/CLAUDE.md](../backend/src/main/resources/db/CLAUDE.md) | 単行の @AGENTS.md ローダー | 同じディレクトリの AGENTS.md が存在 | 一致 | 保留・正文をコピーしない |
| [e2e/CLAUDE.md](../e2e/CLAUDE.md) | 単行の @AGENTS.md ローダー | 同じディレクトリの AGENTS.md が存在 | 一致 | 保留・正文をコピーしない |
| [frontend/CLAUDE.md](../frontend/CLAUDE.md) | 単行の @AGENTS.md ローダー | 同じディレクトリの AGENTS.md が存在 | 一致 | 保留・正文をコピーしない |
| [infrastructure/CLAUDE.md](../infrastructure/CLAUDE.md) | 単行の @AGENTS.md ローダー | 同じディレクトリの AGENTS.md が存在 | 一致 | 保留・正文をコピーしない |
| [.github/ISSUE_TEMPLATE/bug.md](../.github/ISSUE_TEMPLATE/bug.md) | 日本語の入力・検証項目 | テンプレート本文と [CI](../.github/workflows/lint-and-test.yml) | 一致 | 保留・不存在の英文セクション検査をガイド側から削除 |
| [.github/ISSUE_TEMPLATE/feature.md](../.github/ISSUE_TEMPLATE/feature.md) | 日本語の入力・検証項目 | テンプレート本文と [CI](../.github/workflows/lint-and-test.yml) | 一致 | 保留・不存在の英文セクション検査をガイド側から削除 |
| [.github/pull_request_template.md](../.github/pull_request_template.md) | 日本語の入力・検証項目 | テンプレート本文と [CI](../.github/workflows/lint-and-test.yml) | 一致 | 保留・不存在の英文セクション検査をガイド側から削除 |
| [docs/adr/0001-authentication-spring-security-standard-stack.md](adr/0001-authentication-spring-security-standard-stack.md) | 標準認証スタック | [SecurityConfig.java](../backend/src/main/java/com/kizuna/auth/infrastructure/SecurityConfig.java) | 一致 | 保留・見出しを日本語化 |
| [docs/adr/0002-store-scoped-entities-declare-filters-statically.md](adr/0002-store-scoped-entities-declare-filters-statically.md) | 対象を 8 エンティティと列挙 | [StoreIsolationTests.java](../backend/src/test/java/com/kizuna/StoreIsolationTests.java) | 文書の陳腐化 | 圧縮・全 StoreScopedEntity の宣言条件へ修正 |
| [docs/adr/0003-store-scoped-entities-do-not-denormalize-store-name.md](adr/0003-store-scoped-entities-do-not-denormalize-store-name.md) | 店舗名を行に重複保持しない | [OrderRepository.java](../backend/src/main/java/com/kizuna/order/domain/OrderRepository.java) | 一致 | 保留・見出しを日本語化 |
| [docs/adr/0004-frontend-shadcn-ui-on-radix.md](adr/0004-frontend-shadcn-ui-on-radix.md) | Radix ベースの UI 基盤 | [package.json](../frontend/package.json) | 後続決定により置換 | 背景を圧縮・0005 への取代関係を維持 |
| [docs/adr/0005-frontend-shadcn-ui-on-base-ui.md](adr/0005-frontend-shadcn-ui-on-base-ui.md) | Base UI と vendored primitives | [button.tsx](../frontend/src/shared/ui/button.tsx) | 一致 | 移行背景を圧縮・継承する視覚規範を維持 |
| [docs/adr/0006-point-ledger-is-platform-scoped.md](adr/0006-point-ledger-is-platform-scoped.md) | ポイント台帳はプラットフォーム所属 | [PointLedger.java](../backend/src/main/java/com/kizuna/point/domain/PointLedger.java) | 一致 | 保留・見出しを日本語化 |
| [docs/adr/0007-ledger-writes-carry-client-idempotency-keys.md](adr/0007-ledger-writes-carry-client-idempotency-keys.md) | クライアント冪等キーで台帳を書き込む | [PointLedgerService.java](../backend/src/main/java/com/kizuna/point/application/PointLedgerService.java) | 一致 | 保留・見出しを日本語化 |
| [docs/adr/0008-order-attribution-single-path-via-customer.md](adr/0008-order-attribution-single-path-via-customer.md) | 帰属は Customer 経由、完結後取消は未設計 | [OrderPointRollbackService.java](../backend/src/main/java/com/kizuna/order/application/OrderPointRollbackService.java) | 文書の陳腐化 | 取消の説明を 0023 へ統合 |
| [docs/adr/0009-attribution-is-immutable-fact-fixed-at-completion.md](adr/0009-attribution-is-immutable-fact-fixed-at-completion.md) | 帰属時の事実を固定、連絡先スナップショットは選択待ち | [Order.java](../backend/src/main/java/com/kizuna/order/domain/Order.java) | 文書の陳腐化 | contact_name / contact_phone_number を反映 |
| [docs/adr/0010-customer-merge-is-repoint-and-tombstone.md](adr/0010-customer-merge-is-repoint-and-tombstone.md) | 付替えと墓標による顧客統合 | [CustomerMergeService.java](../backend/src/main/java/com/kizuna/customer/application/CustomerMergeService.java) | 一致 | 保留・不変条件を維持 |
| [docs/adr/0011-receipt-token-reissue-revokes-the-previous-one.md](adr/0011-receipt-token-reissue-revokes-the-previous-one.md) | 再発行で前のレシートを失効 | [OrderReceiptToken.java](../backend/src/main/java/com/kizuna/order/domain/OrderReceiptToken.java) | 一致 | 背景を圧縮・失効条件を維持 |
| [docs/adr/0012-attribution-point-correction-addresses-the-attribution-record.md](adr/0012-attribution-point-correction-addresses-the-attribution-record.md) | 訂正は帰属レコードを指定 | [OrderAttributionCorrectionService.java](../backend/src/main/java/com/kizuna/order/application/OrderAttributionCorrectionService.java) | 一致 | 背景を圧縮・冪等性と上限を維持 |
| [docs/adr/0013-terminal-orders-are-frozen-and-never-deleted.md](adr/0013-terminal-orders-are-frozen-and-never-deleted.md) | 終端受注を凍結、完結後訂正は将来 | [OrderCorrectionService.java](../backend/src/main/java/com/kizuna/order/application/OrderCorrectionService.java) | 文書の陳腐化 | 通常更新禁止と専用訂正を区別、0019 / 0023 へ統合 |
| [docs/adr/0014-attendance-is-a-separate-aggregate-from-shift.md](adr/0014-attendance-is-a-separate-aggregate-from-shift.md) | 勤怠はシフトから独立 | [AttendanceService.java](../backend/src/main/java/com/kizuna/shift/application/AttendanceService.java) | 一致 | 保留・保存と訂正の決定を維持 |
| [docs/adr/0015-shift-publication-is-a-separate-axis-from-approval.md](adr/0015-shift-publication-is-a-separate-axis-from-approval.md) | 公開は承認と独立、Cast.ACTIVE を参照 | [ShiftService.java](../backend/src/main/java/com/kizuna/shift/application/ShiftService.java) | 文書の陳腐化 | 在籍 ENROLLED と 0026 の参照に修正 |
| [docs/adr/0016-row-locks-follow-the-foreign-key-cascade-direction.md](adr/0016-row-locks-follow-the-foreign-key-cascade-direction.md) | t_users の明示ロックは単一路のみ | [PlatformStaffAccountService.java](../backend/src/main/java/com/kizuna/user/application/PlatformStaffAccountService.java) | 文書の陳腐化・全経路の証拠不足 | 旧経路表を決定時点と明記、順序規範は維持 |
| [docs/adr/0017-order-applications-are-a-separate-record-from-orders.md](adr/0017-order-applications-are-a-separate-record-from-orders.md) | 申請を分離、Guest 入口は追加予定 | [GuestOrderApplicationService.java](../backend/src/main/java/com/kizuna/order/application/GuestOrderApplicationService.java) | 文書の陳腐化 | 実装済みの入口を記載、直接作成の HQ 記述を修正 |
| [docs/adr/0018-order-fees-are-lines-and-the-total-is-derived.md](adr/0018-order-fees-are-lines-and-the-total-is-derived.md) | 料金は明細の導出値、訂正は将来 | [OrderFeeLine.java](../backend/src/main/java/com/kizuna/order/domain/OrderFeeLine.java) | 文書の陳腐化 | 0019 / 0023 参照へ集約、償還ポイントの不変性を維持 |
| [docs/adr/0019-completed-orders-are-corrected-through-a-permissioned-gate.md](adr/0019-completed-orders-are-corrected-through-a-permissioned-gate.md) | 完結後訂正に専用権限と履歴 | [OrderCorrectionService.java](../backend/src/main/java/com/kizuna/order/application/OrderCorrectionService.java) | 一致 | 保留・見出しを日本語化 |
| [docs/adr/0020-staff-management-splits-into-three-layers.md](adr/0020-staff-management-splits-into-three-layers.md) | 管理者は STAFF_MANAGE を使用 | [PlatformStaffAccountService.java](../backend/src/main/java/com/kizuna/user/application/PlatformStaffAccountService.java) | 文書の陳腐化 | 0021 の ROLE_MANAGE / STORE_STAFF_MANAGE 境界へ更新 |
| [docs/adr/0021-hq-admin-retreats-from-store-daily-operations.md](adr/0021-hq-admin-retreats-from-store-daily-operations.md) | プラットフォームは横断閲覧、日常操作は店舗 | [PlatformOrderService.java](../backend/src/main/java/com/kizuna/order/application/PlatformOrderService.java) | 一致 | 保留・見出しを日本語化 |
| [docs/adr/0022-session-invalidation-by-credential-version.md](adr/0022-session-invalidation-by-credential-version.md) | credential version、旧再読ガードの撤去は要確認 | [AuthSessionService.java](../backend/src/main/java/com/kizuna/auth/application/AuthSessionService.java) | 文書の陳腐化 | commit 後の確定版反映を記載 |
| [docs/adr/0023-point-rollback-is-an-explicit-operation-that-reverses-allocations.md](adr/0023-point-rollback-is-an-explicit-operation-that-reverses-allocations.md) | ポイント取消は配賦を反転する専用操作 | [OrderPointRollbackService.java](../backend/src/main/java/com/kizuna/order/application/OrderPointRollbackService.java) | 一致 | 保留・見出しを日本語化 |
| [docs/adr/0024-emergency-elevation-is-an-entity-plus-short-lived-token.md](adr/0024-emergency-elevation-is-an-entity-plus-short-lived-token.md) | 緊急昇格を期限付き token と実体で分離 | [EmergencyElevationService.java](../backend/src/main/java/com/kizuna/auth/application/EmergencyElevationService.java) | 一致 | 背景を圧縮・安全制約を維持 |
| [docs/adr/0025-service-identity-lives-in-t-users-without-credentials.md](adr/0025-service-identity-lives-in-t-users-without-credentials.md) | 資格情報なしの SERVICE 利用者 | [ServiceIdentityService.java](../backend/src/main/java/com/kizuna/user/application/ServiceIdentityService.java) | 一致 | 背景を圧縮・管理機構の未実装保証はしない |
| [docs/adr/0026-cast-person-enrollment-and-public-profile.md](adr/0026-cast-person-enrollment-and-public-profile.md) | Cast 三層、在籍操作と履歴は後続作業 | [CastEnrollmentService.java](../backend/src/main/java/com/kizuna/cast/application/CastEnrollmentService.java) | 文書の陳腐化 | 実装済み操作・履歴・公開条件と横断閲覧を反映 |
| `docs/api-guidelines.md` | API 一般規範 / Cast 実装計画 | Git 基準版、[CastEnrollmentService.java](../backend/src/main/java/com/kizuna/cast/application/CastEnrollmentService.java)、[PlatformCastService.java](../backend/src/main/java/com/kizuna/cast/application/PlatformCastService.java) | 開始時から削除済み・計画の陳腐化 | 削除維持。一般規範は backend/AGENTS.md、Cast の不変条件は ADR 0026 へ統合 |
| `docs/issue-859-api-design.md` | API 一般規範 / Cast 実装計画 | Git 基準版、[CastEnrollmentService.java](../backend/src/main/java/com/kizuna/cast/application/CastEnrollmentService.java)、[PlatformCastService.java](../backend/src/main/java/com/kizuna/cast/application/PlatformCastService.java) | 開始時から削除済み・計画の陳腐化 | 削除維持。一般規範は backend/AGENTS.md、Cast の不変条件は ADR 0026 へ統合 |
| `docs/issue-863-api-design.md` | API 一般規範 / Cast 実装計画 | Git 基準版、[CastEnrollmentService.java](../backend/src/main/java/com/kizuna/cast/application/CastEnrollmentService.java)、[PlatformCastService.java](../backend/src/main/java/com/kizuna/cast/application/PlatformCastService.java) | 開始時から削除済み・計画の陳腐化 | 削除維持。一般規範は backend/AGENTS.md、Cast の不変条件は ADR 0026 へ統合 |
| [docs/legacy-business/actors-and-access.md](legacy-business/actors-and-access.md) | 旧 current-business の確認済み業務・未確定事項 | 文書に記載された 2026-07-17 の issue 本文・回答 | 歴史資料の差 | 移動・冒頭に歴史性を明記、未解決の要求根拠を保持。旧パスは削除 |
| [docs/legacy-business/data-model.md](legacy-business/data-model.md) | 旧 current-business の確認済み業務・未確定事項 | 文書に記載された 2026-07-17 の issue 本文・回答 | 歴史資料の差 | 移動・冒頭に歴史性を明記、未解決の要求根拠を保持。旧パスは削除 |
| [docs/legacy-business/workflows.md](legacy-business/workflows.md) | 旧 current-business の確認済み業務・未確定事項 | 文書に記載された 2026-07-17 の issue 本文・回答 | 歴史資料の差 | 移動・冒頭に歴史性を明記、未解決の要求根拠を保持。旧パスは削除 |
| [docs/README.md](README.md) | 文書の所在・差異・処置の追跡 | 本文の参照先 | 新規 | 追加 |
| [docs/documentation-audit.md](documentation-audit.md) | 文書の所在・差異・処置の追跡 | 本文の参照先 | 新規 | 追加 |

## 重点項目の追加照合

| 対象 | 現在の根拠 | 結論と反映先 |
| --- | --- | --- |
| ログイン後の入口・cookie | [completePlatformLogin](../frontend/src/features/platform-login/model/completePlatformLogin.ts)、[proxy](../frontend/src/proxy.ts)、[routeGuard](../frontend/src/shared/lib/proxy/routeGuard.ts) | console と user_type による遷移。host/cookie による表示選択を API の授権と混同しない。README とフロント指示へ反映 |
| 店舗作用域 | [StoreIdInterceptor](../backend/src/main/java/com/kizuna/shared/storescope/StoreIdInterceptor.java)、[StoreIsolationTests](../backend/src/test/java/com/kizuna/StoreIsolationTests.java) | ヘッダ文脈と Hibernate filter は別責務。直接 ID 読み込みの applyToLoadByKey を含む隔離規則を維持 |
| 全端末失効と再開 | [CredentialVersionService](../backend/src/main/java/com/kizuna/auth/infrastructure/CredentialVersionService.java)、[AuthSessionServiceTest](../backend/src/test/java/com/kizuna/auth/application/AuthSessionServiceTest.java) | パスワード変更・再設定・停止で版を更新。ロール変更は次回ログイン反映、再開で旧セッションを復活させない。CONTEXT / ADR 0022 に反映 |
| Cast 三層・履歴 | [CastEnrollmentService](../backend/src/main/java/com/kizuna/cast/application/CastEnrollmentService.java)、[PlatformCastService](../backend/src/main/java/com/kizuna/cast/application/PlatformCastService.java) | suspend / resume / withdraw と履歴照会は実装済み。店舗 cast_id は在籍 ID、本人の横断閲覧は別経路。CONTEXT / ADR 0026 に反映 |
| 公開プロフィール・シフト | [CastProfileRepository](../backend/src/main/java/com/kizuna/cast/domain/CastProfileRepository.java)、[ConfirmedShiftLookupService](../backend/src/main/java/com/kizuna/shift/application/ConfirmedShiftLookupService.java) | 公開プロフィールは PUBLISHED ∧ ENROLLED、公開シフトの追加条件と内部受注の確定シフト条件を分離。ADR 0015 / 0026 に反映 |
| 受注・申請・ポイント | [Order](../backend/src/main/java/com/kizuna/order/domain/Order.java)、[OrderApplication](../backend/src/main/java/com/kizuna/order/domain/OrderApplication.java)、[OrderPointRollbackService](../backend/src/main/java/com/kizuna/order/application/OrderPointRollbackService.java) | CONFIRMED 出生の受注と PENDING の申請は別。完結後は専用訂正・ポイント取消。将来計画の記述を ADR 0013 / 0017 / 0018 から除去 |
| 顧客統合・来店履歴・勤怠 | [CustomerMergeServiceTest](../backend/src/test/java/com/kizuna/customer/application/CustomerMergeServiceTest.java)、[MemberReceiptClaimServiceTest](../backend/src/test/java/com/kizuna/order/application/MemberReceiptClaimServiceTest.java)、[AttendanceServiceTest](../backend/src/test/java/com/kizuna/shift/application/AttendanceServiceTest.java) | 両側会員リンク時の統合拒否、墓標、帰属記録、勤怠の取消・訂正という規範を保持。単なるレコード結合や物理削除に言い換えない |
| 旧業務と現在の差 | [旧 workflows](legacy-business/workflows.md) は受注の金額・人数を未実装と記載するが、現在の Order は pax / totalFee / feeLines を持つ | 歴史資料の差。2026-07-17 の記述を改変して現在の仕様にせず、歴史ラベルと現在の正本への導線で分離。未解決の配車等を実装済みにしない |

## 実装の規範逸脱と残る確認事項

| 原文の規範・主張 | 実装の証拠 | 性質 | 処置 |
| --- | --- | --- | --- |
| 匿名入口の免除は HTTP method + path | [SecurityConfig.java](../backend/src/main/java/com/kizuna/auth/infrastructure/SecurityConfig.java) の CSRF_IGNORED_MATCHERS と [PlatformBearerTokenResolver.java](../backend/src/main/java/com/kizuna/auth/infrastructure/PlatformBearerTokenResolver.java) の BEARER_EXEMPT_MATCHERS に path のみの要素がある | 実装の規範逸脱 | 規範を維持。兄弟 method と optional authentication を調べた別のコード修正が必要。現状の脆弱性成立までは本監査では断定しない |
| DTO は Request / Response | [StoreVO](../backend/src/main/java/com/kizuna/store/api/dto/StoreVO.java)、[Token](../backend/src/main/java/com/kizuna/auth/api/dto/Token.java)、[StoreCreateDTO](../backend/src/main/java/com/kizuna/store/api/dto/StoreCreateDTO.java) | 実装の規範逸脱 | 既知の例外として維持。命名だけの修正を混ぜず、新規 API で模倣しない |
| 削除前 API 文書が OrderResponse.receiptToken を反例として挙げる | [OrderResponse](../backend/src/main/java/com/kizuna/order/api/dto/OrderResponse.java) に当該項目はなく、[OrderCompletionResponse](../backend/src/main/java/com/kizuna/order/api/dto/OrderCompletionResponse.java) と [OrderReceiptTokenResponse](../backend/src/main/java/com/kizuna/order/api/dto/OrderReceiptTokenResponse.java) に分離済み | 文書の陳腐化 | 旧反例は移植しない。秘密値を一覧 DTO の型から除外する規範を維持 |
| 複数 worktree の E2E は衝突しない | [Taskfile](../Taskfile.yml) の名前は worktree の basename 由来 | 証拠の限界 | 同じ basename の別 checkout まで一意とは保証しない。下記の専用指示に条件を明記 |
| コントラスト表は現在の画面全体の適合を保証する | [globals.css](../frontend/src/app/globals.css) と DESIGN の既存測定表 | 証拠不足 | 既存測定値を残す。画面実測・全テーマの再計算は未実施。ブラウザ原生 outline の制限を保持 |
| セキュリティ窓口と対応方針 | 基準版の既存メールと明示された期限 | 外部運用の証拠不足 | メールの到達性、private reporting 設定、SLA 遵守は未確認。仮 PGP などを削除し、約束を増やさない |
| ロック順序の全書込経路が安全 | ADR 0016 の過去の経路表と現在の各サービス | 証拠不足 | 順序規範を維持。過去のロック数を現在の保証から外す。全競合の再実行検証は行っていない |

## 生成文書の処置

以下は ModularityTests の生成物としてすべて保留する。手書きで列挙を直すと次回生成で失われるため、今回の圧縮対象から分ける。`module-cast.adoc` のサービス一覧には現在存在する CastEnrollmentService / PlatformCastService がなく、生成物が現行コードと同期している保証はない。索引に警告を置き、ソースを現在の正本とした。全モジュール図の依存差分と再生成は未実施。

| 生成文書 | 処置・証拠 |
| --- | --- |
| [all-docs.adoc](../backend/docs/modulith/all-docs.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [components.puml](../backend/docs/modulith/components.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-auth.adoc](../backend/docs/modulith/module-auth.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-auth.puml](../backend/docs/modulith/module-auth.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-cast.adoc](../backend/docs/modulith/module-cast.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-cast.puml](../backend/docs/modulith/module-cast.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-customer.adoc](../backend/docs/modulith/module-customer.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-customer.puml](../backend/docs/modulith/module-customer.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-member.adoc](../backend/docs/modulith/module-member.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-member.puml](../backend/docs/modulith/module-member.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-menu.adoc](../backend/docs/modulith/module-menu.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-menu.puml](../backend/docs/modulith/module-menu.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-notification.adoc](../backend/docs/modulith/module-notification.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-notification.puml](../backend/docs/modulith/module-notification.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-order.adoc](../backend/docs/modulith/module-order.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-order.puml](../backend/docs/modulith/module-order.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-point.adoc](../backend/docs/modulith/module-point.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-point.puml](../backend/docs/modulith/module-point.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-settings.adoc](../backend/docs/modulith/module-settings.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-settings.puml](../backend/docs/modulith/module-settings.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-shared.adoc](../backend/docs/modulith/module-shared.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-shared.puml](../backend/docs/modulith/module-shared.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-shift.adoc](../backend/docs/modulith/module-shift.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-shift.puml](../backend/docs/modulith/module-shift.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-storage.adoc](../backend/docs/modulith/module-storage.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-storage.puml](../backend/docs/modulith/module-storage.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-store.adoc](../backend/docs/modulith/module-store.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-store.puml](../backend/docs/modulith/module-store.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-storeprofile.adoc](../backend/docs/modulith/module-storeprofile.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-storeprofile.puml](../backend/docs/modulith/module-storeprofile.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-user.adoc](../backend/docs/modulith/module-user.adoc) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |
| [module-user.puml](../backend/docs/modulith/module-user.puml) | 保留・ModularityTests のスナップショット。現在の完全性は未確認 |

## 初回整理時の検証と規模

- 一時スクリプトによる Markdown のローカルリンク・章アンカー・CLAUDE ローダー検査: 52 ファイル、exit 0。
- 変更したフロント文書の Prettier 検査: exit 0。
- `git diff --check`: exit 0。
- `task lint-repo`: Docker の actionlint、exit 0。
- 全 build / unit / integration / E2E は未実施。文書のみ・未コミットであり、実装証拠の既存テストは静的照合に使用した。コミット・PR の前には CONTRIBUTING と PR テンプレートの全検証を別途実施する。backend / frontend 配下の文書も CI の対象パスになることは変更していない。

集計は作業開始時に存在した Markdown（50 ファイル）を基準とする。開始時から削除済みの 3 ファイルは分母に含めない。生成 AsciiDoc / PlantUML は 32 ファイルのまま。本文量は UTF-8 を Unicode 文字として数え、Markdown 構文を含む。表の空白圧縮だけで成果を大きく見せないため、空白除外値も示す。本監査自体の追加分は循環集計を避けて本文量から除外する。


| 指標 | 整理前 | 整理後 |
| --- | --- | --- |
| Markdown ファイル | 50 | 52（索引・監査を各 1 件追加。旧資料 3 件は移動） |
| 生成文書を含むファイル | 82 | 84 |
| Markdown 行数（本監査を除く） | 4,005 | 2,955 |
| Markdown 文字数（本監査を除く） | 311,551 | 175,024（43.8% 減） |
| 空白除外文字数（本監査を除く） | 262,484 | 155,107（40.9% 減） |

主な圧縮は DESIGN の移行手順・重複 token 説明と巨大な表の整形、README と CONTRIBUTING の重複命令、SECURITY の仮情報、根指示の重複説明。backend/AGENTS.md は削除済み API 規範の受け皿として増加した。安全制約や未解決業務の根拠を削って削減率を上げていない。

## PR 作成前の追加検証

2026-09-11、`codex/docs-cleanup-audit` で PR 作成前の検証を実行した。基準コミットと origin/master は上記と一致。全コマンドを所定の Taskfile と Docker 経由で実行し、終了コードで判定した。

| 検証 | 結果 |
| --- | --- |
| `task lint`（Repo / frontend / backend） | exit 0 |
| `task test`（frontend・backend 単体 / coverage / backend 統合） | exit 0 |
| `task build`（backend / frontend 本番イメージ） | exit 0 |
| `task e2e`（Chromium 全 40 シナリオ） | exit 0、40 passed（8.5 分） |
| ローカルの文書差分レビュー | 規範の保持、計画との対応、参照先、文書だけの差分であることを確認 |
| ローカルリンク・章アンカー・CLAUDE ローダー | 52 Markdown ファイル、exit 0 |
| `git diff --check` | exit 0 |

以上は初回整理時に未実施だった実行検証の追記である。生成モジュール資料は再生成しておらず、既存スナップショットの注意は引き続き有効。全画面の視覚確認・コントラストの再測定は行っていない。
