# 文書索引

現在の概念と不変条件は [領域用語](../CONTEXT.md)、開発・検証手順は [貢献ガイド](../CONTRIBUTING.md)、API の規範は [バックエンド指示の API contract](../backend/AGENTS.md#api-contract)、画面の規範は [デザインシステム](../frontend/DESIGN.md)を正本とする。起動は [README](../README.md)、AI の読み分けと安全制約は [AGENTS.md](../AGENTS.md)を参照する。

文書と実装の照合結果、既知の規範との差、各文書の処置は [文書監査](documentation-audit.md)に記録する。規範との不一致は実装済みの保証ではない。

## アーキテクチャ決定

決定時の背景は歴史、決定は有効な規範として読む。後続 ADR による変更は相互参照を優先する。

| 番号 | 決定 | 状態・関連 |
| --- | --- | --- |
| 0001 | [認証は Spring Security 標準スタックを採用する](adr/0001-authentication-spring-security-standard-stack.md) | 採用 |
| 0002 | [StoreScopedEntity は storeFilter・storeSetFilter を静的に全量宣言する](adr/0002-store-scoped-entities-declare-filters-statically.md) | 採用 |
| 0003 | [店舗作用域集約は店舗の表示名を非正規化保存しない](adr/0003-store-scoped-entities-do-not-denormalize-store-name.md) | 採用 |
| 0004 | [フロントエンドの UI 基盤は Radix ベースの shadcn/ui を vendoring する](adr/0004-frontend-shadcn-ui-on-radix.md) | 0005 により UI 基盤を置換。視覚規範の継承は 0005 を参照 |
| 0005 | [フロントエンドの UI 基盤を Radix から Base UI へ移す](adr/0005-frontend-shadcn-ui-on-base-ui.md) | 採用 |
| 0006 | [会員ポイント台帳は platform 帰属とし、発生店舗は originating_store_id で帰属記録する](adr/0006-point-ledger-is-platform-scoped.md) | 採用 |
| 0007 | [台帳への人手書き込みはクライアント生成の冪等キーで再送を機械的に遮断する](adr/0007-ledger-writes-carry-client-idempotency-keys.md) | 採用 |
| 0008 | [受注の帰属は顧客経路に一本化し、会員可視性は受注単位の帰属記録で与える](adr/0008-order-attribution-single-path-via-customer.md) | 帰属の取消は 0023 |
| 0009 | [帰属は完了時に確定する受注単位の不変事実として記録する](adr/0009-attribution-is-immutable-fact-fixed-at-completion.md) | 訂正・失効は 0012 / 0023 |
| 0010 | [顧客統合は付替えと墓標で表し、行を削除しない](adr/0010-customer-merge-is-repoint-and-tombstone.md) | 採用 |
| 0011 | [伝票トークンは受注ごとに 1 本だけ生かし、再発行が前の 1 本を失効させる](adr/0011-receipt-token-reissue-revokes-the-previous-one.md) | 採用 |
| 0012 | [誤帰属の台帳訂正は帰属記録を宛先に取り、無効化とは別段の人手操作とする](adr/0012-attribution-point-correction-addresses-the-attribution-record.md) | 採用 |
| 0013 | [終端状態の受注は汎用更新せず、単件削除しない](adr/0013-terminal-orders-are-frozen-and-never-deleted.md) | 専用訂正は 0019、ポイント取消は 0023 |
| 0014 | [当日実績はシフトと別の集約であり、実績に参照されるシフトは削除できない](adr/0014-attendance-is-a-separate-aggregate-from-shift.md) | 採用 |
| 0015 | [シフトの公開可否は承認と別の軸であり、店外露出だけを絞る](adr/0015-shift-publication-is-a-separate-axis-from-approval.md) | 採用 |
| 0016 | [行ロックは外部キーの連鎖の向きに押さえる](adr/0016-row-locks-follow-the-foreign-key-cascade-direction.md) | 経路表は決定時の調査。Cast は 0026 を参照 |
| 0017 | [予約申請は受注と別の記録であり、すべての受注は確定で出生する](adr/0017-order-applications-are-a-separate-record-from-orders.md) | 採用 |
| 0018 | [受注金額は明細行で持ち、合計は行から導出し、写しへは回写しない](adr/0018-order-fees-are-lines-and-the-total-is-derived.md) | 採用 |
| 0019 | [完了した受注は権限付きの門でだけ訂正でき、訂正前の姿が痕として残る](adr/0019-completed-orders-are-corrected-through-a-permissioned-gate.md) | 採用 |
| 0020 | [スタッフ管理は三層に分かれ、店長は店長を作れない](adr/0020-staff-management-splits-into-three-layers.md) | 管理責務を 0021 で更新 |
| 0021 | [HQ 管理者はアカウント生命周期・ロール定義・統治層の授与を担い、店舗の日常からは撤退する](adr/0021-hq-admin-retreats-from-store-daily-operations.md) | 採用 |
| 0022 | [セッション失効は資格情報の版の照合で判定する](adr/0022-session-invalidation-by-credential-version.md) | 採用 |
| 0023 | [ポイントの巻き戻しは受注を宛先とする明示操作で、利用は引き当ての逆転で元のロットへ戻す](adr/0023-point-rollback-is-an-explicit-operation-that-reverses-allocations.md) | 採用 |
| 0024 | [緊急昇格は独立した実体と短命な昇格トークンで表し、時限のロール授与では表さない](adr/0024-emergency-elevation-is-an-entity-plus-short-lived-token.md) | 採用 |
| 0025 | [サービスID は資格情報を持たない PlatformUser として `t_users` に同居させる](adr/0025-service-identity-lives-in-t-users-without-credentials.md) | 採用 |
| 0026 | [キャストの正本を本人・店舗在籍・公開プロフィールに分離する](adr/0026-cast-person-enrollment-and-public-profile.md) | 採用 |

## 旧システム調査資料

2026-07-17 時点の issue 本文・回答を保存した資料。Kizuna の実装仕様ではない。元の「確認済み」は回答で確認した旧業務、「未確定」「確認待ち」は未解決の要求を表す。「現行」は調査当時の旧システムを指す。未解決事項を実装済みとして読み替えない。

- [利用者とアクセス範囲](legacy-business/actors-and-access.md): 出典 [#364](https://github.com/kanghouchao/Kizuna/issues/364)。機能権限、担当店舗、精算範囲。
- [データモデル](legacy-business/data-model.md): 出典は文書冒頭を参照。旧データの粒度、対応候補、未確定事項。
- [業務フロー](legacy-business/workflows.md): 出典は文書冒頭を参照。旧受付・精算・在籍などの業務条件。

## 生成されたモジュール資料

[backend/docs/modulith](../backend/docs/modulith/) は [ModularityTests](../backend/src/test/java/com/kizuna/ModularityTests.java) が生成する図と AsciiDoc の保存物。**現在のコードと不一致があるスナップショット**であり、現在の API・サービス一覧の正本にはしない。例えば Cast の資料には `CastEnrollmentService` が未反映である。本整理では手で生成結果を書き換えず保留し、現在の構成はソースを参照する。再生成は JDK 25 で所定のテストを実行して行う。
