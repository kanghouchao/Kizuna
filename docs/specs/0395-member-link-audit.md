# 顧客・会員の関連区間の共通監査

## 状態と目的

2026-10-08、承認済み基点は master `4a426636ba67db894bbe7a309dae62e228d464c7`。親#395はOPENを継続する。顧客と会員の関連を建立・変更・解除する既存2入口を、同じ取引の変更不可監査へ接続する契約は承認済み。シフト監査#1008はその後master `c1ff7775f89e4fcd6257c0d76d606438ff86db02`へ合流し、本作業も未commit変更を保全して競合なしで同期済み。続いて非公開添付#1004の合流後、最終検証の基点を `86be6e51c9eb59419e423e3fb40752b11489f4f7` に同期した。権限目録44件と添付検証補助を引き継ぐ。関連監査の実装上の依存はない。

現行の関連区間正本は理由と状態を保持するが、共通監査へは未接続である。通常担当者と緊急昇格担当者による変更を、安全な区間情報と既存正本への参照だけで追跡する。

## 利用者の要求

1. 店舗担当者として、初回の関連建立を区間IDと認証主体から追跡できる。
2. 店舗担当者として、関連先の変更で旧区間の解除と新区間の建立を両方辿れる。
3. 店舗担当者として、解除後も区間IDから理由正本を確認できる。
4. 監査参照者として、通常担当者と緊急昇格の利用を区別できる。
5. 利用者として、会員コード・理由原文・氏名・メールを共通監査へ複写されない。
6. 利用者として、拒否・競合・取引rollbackが成功変更として記録されない。
7. 店舗担当者として、監査の保存失敗で業務だけが確定しない。
8. 店舗担当者として、既存のHTTP契約・権限・顧客ロックと関連の唯一性を維持できる。

## 承認済み入口とHTTP契約

| 入口 | 既存API | 成功 |
| --- | --- | --- |
| CustomerMemberLinkService.link | POST /store/customers/{customerId}/member-link | 201、既存関連応答 |
| CustomerMemberLinkService.unlink | POST /store/customers/{customerId}/member-link/releases | 204 |

PERM_CUSTOMER_MANAGE、既存要求/応答、401/403/404/400/409の分類、顧客行の先行ロック、expectedLinkIdの一致確認、部分一意索引、既存flush順序を維持する。同じ会員への再関連は既存409であり、no-op成功に変更しない。解除済み/古い画面による競合も既存契約のまま。

## 記録契約

- 対象種別CUSTOMER_MEMBER_LINK、対象IDは既存区間ID。由来CUSTOMER/顧客IDを用いる。
- 建立はCUSTOMER_MEMBER_LINK_CREATEDを1件。関連先の変更は旧区間のCUSTOMER_MEMBER_LINK_RELEASEDと新区間のCREATEDを2件。明示解除はRELEASEDを1件。内部委譲と読取は追加入口として数えない。
- snapshot白名簿はcustomer_id、member_id、status、reason（成立依据enum）、version、linked_by、linked_at、released_by、released_atのみ。store_idとactorは既存sinkの文脈に従う。versionはflush後の実値を使い、旧区間のbeforeは変更前に確保する。
- member_code、operation_reason、release_reason、氏名、メール、hash、要求/entity全体は含めない。理由正本は区間IDから既存履歴を照会する。新しいoperation IDや第二監査表を導入しない。
- 新規建立のbeforeは空。解除ではACTIVEからRELEASEDへ遷移した区間の前後を記録する。関連先変更では旧区間の監査と新しい区間の監査が共に業務と同一取引で確定する。
- BusinessAudit/AuditWriterを再利用し、主体・店舗・緊急昇格IDは既存の検証済み文脈から解決する。拒否、古いexpectedLinkId、一意制約競合、外側rollback、監査失敗では成功履歴を残さない。
- 顧客統合のrepoint、会員退会、本人伝票claim、注文帰属など別の書き手には拡張しない。この2入口の完了を関連区間の全書込や#395全体の完了とは扱わない。

## 検証

承認済みの3境界を継続する。サービスの公開操作から実BusinessAudit/AuditWriterへ到達する試験で、建立・変更両側・解除の件数、前後値、由来と秘密除外を確認する。実PGでは実version、監査失敗による建立/変更/解除のrollback、外側rollback、同一顧客の古いexpectedLinkId競合と異なる顧客の同一会員競合を確認する。HTTPでは普通STAFF/緊急昇格、異店舗・無権限・既存409、既存関連履歴の表示を検証する。

既存CustomerMemberLinkServiceTest、CustomerMemberLinkControllerTest、CustomerAuditPostgresTest/CustomerContactAuditPostgresTest、customer-member-linkのE2Eを使える境界として優先する。DBアダプター以外に不要な新しいmock境界を導入しない。

最終検証はTask lint/test/build/e2eと双軸レビュー。重型E2Eは#1007の窓解放後に割当を受ける。draft PR→同一HEADのCI実job/step→Ready→Codex Completedと公式bot反応を確認する。所有者が手動合流する。

## 対象外

新しいAPI/画面/schema、権限授与、実送信、デプロイ、#1008への積み重ね、他worktreeの変更、保持年限や実運用scheduleの決定。
