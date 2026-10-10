# キャスト項目定義と値除去の共通監査

公開票: [#1027](https://github.com/kanghouchao/Kizuna/issues/1027)。親 #395。

## 背景 / 目的

親 #395 の残る接続のうち、店舗のキャスト項目定義の作成・編集・削除と、削除に伴う在籍内部値・公開プロフィール値の除去を同じ取引の共通監査へ接続する。2026-10-10 の継続実行指示に基づく独立切片。基点は master `b70da833fce13680da4fbbdd1d9a1626aab4a242`。#1009/#1010 は完了済み。

## スコープ

| 操作 | action / target | 安全な記録 |
| --- | --- | --- |
| 定義作成 | CAST_FIELD_DEFINITION_CREATED / CAST_FIELD_DEFINITION | before空、afterはexists/is_public/display_order/実version |
| 定義編集 | CAST_FIELD_DEFINITION_UPDATED / CAST_FIELD_DEFINITION | 同じ白名簿の前後値。labelの実変更は固定名redacted_fields_changed=labelのみ |
| 定義削除 | CAST_FIELD_DEFINITION_DELETED / CAST_FIELD_DEFINITION | 削除前白名簿、削除後はexists=falseのみ。消えた行のpostversionを作らない |
| 内部値除去 | CAST_INTERNAL_FIELD_REMOVED / CAST_ENROLLMENT | 実際に対象keyを持つ在籍のみ。exists/versionの前後、afterに既存snapshot_idと固定変更名internal_custom_fields |
| 公開値除去 | CAST_PROFILE_FIELD_REMOVED / CAST_PROFILE | 実際に対象keyを持つprofileのみ。exists/enrollment_id/versionの前後と固定変更名public_custom_fields |

除去イベントはsourceを削除したCAST_FIELD_DEFINITION/idとする。内部値のsnapshot_idは既存の旧値保存が作った行を参照する。件数制限のない影響先ID集合を一つの摘要へ詰めず、影響先ごとのイベントで参照する。内部helperは独立した利用者操作を二重計上しない。flush後に実versionを採取し、定義・値・snapshot・監査が全て確定するか全てrollbackする。

監査へ自定義key/label/値/hash・個人情報・要求/entity全体を渡さない。labelの比較はメモリ内だけで行う。同値編集は成功変更を追加しない。actorは認証主体から取得し、既存BusinessAudit/AuditWriterによる実actorと緊急昇格IDを使う。新しいHTTP要求欄・sink・権限は追加しない。

### 既存契約の維持

`/store/casts/fields` GET200（CAST_FIELD_DEF_VIEW）・POST201、`/{id}` PUT200・DELETE204（CAST_FIELD_DEF_MANAGE）を維持する。作成はkey/label/is_public、更新はlabel/display_order/is_public、応答は既存DTOのまま。keyと公開区分は作成後不変。同店舗同keyと20件超過は400、対象不在/異店は404、資格/権限拒否は403。既存例外分類の409/500を変更しない。店舗行ロック、在籍行ロック、storeFilterと20件上限を保つ。HTTP・schema・UI挙動は変更しない。

## 受け入れ基準

- [x] 定義3入口と実際に変わった関連在籍/profileを唯一の共通sinkへ接続し、定義IDと既存snapshotへ辿れる
- [x] 実version・存在性・公開区分・表示順だけを許可し、同値を除外、labelだけの真の変更を記録する
- [x] key/label/値/hash/秘密を監査へ複写せず、削除後versionや未変更行のイベントを作らない
- [x] 通常STAFF/緊急昇格の実主体、店隔離、権限拒否、重複key/20件上限、公開区分不変を保つ
- [x] 並行値書込・削除再作成で旧値が復活せず、監査失敗・外側rollbackで定義/値/snapshot/監査が原子的に戻る
- [ ] service→実sink、専用実PostgreSQL、実HTTPと既存UI回帰、Task lint/test/build/全量e2e、独立Spec/Standards、同一HEAD CI/Codexが成功する

## 検証方針

承認済みの3境界を使う。公開サービス→実BusinessAudit/AuditWriter（DBだけmock）で白名簿/no-op/参照を確認する。専用PostgreSQL18 + JDK25では本物の店・在籍ロックと取引を用い、実version/rollback/競合を観測する。条件付き実庫試験の標準CI skipと独立実測を明記する。実HTTPでは専用の通常STAFF・権限のない有効店舗STAFF・緊急昇格を使う。既存cast-custom-fields UIシナリオを全量E2Eで回帰する。

## 対象外（別 issue）

Cast在籍本体の削除/級聯、招待、広告/新客帰属、既存値の意味・公開境界の変更、過去監査の補填、実権限授与、デプロイ、親 #395 の閉鎖。他worktreeと主checkoutを変更しない。

## 参考

親 #395、完了済み #1010、既存在籍snapshotと店舗行ロック。Blocked by: なし。最終PRの合流とmaster同期は親タスクが担当する。

## 検証状況

公開サービス→実sinkの3件、Cast領域/ModularityTestsはexit 0。専用PostgreSQL18/JDK25の13件はskip/failure/error 0、実際のロック待機を伴う値書込先行・削除先行と並行20件上限、5種類の監査失敗・外側rollback・昇格主体・店隔離を確認した。専用PGは回収済み。

focused実HTTPは通常STAFF/緊急昇格の3入口と値除去、同値、権限/異店/重複key/公開区分/容量拒否、削除再作成を1場面で確認し、retry 0でexit 0。Task lint/test/buildは全てexit 0。Specレビュー0指摘、Standardsレビュー0硬性違反。全量Task E2Eも103件、retry 0、exit 0（12.9分）で成功し、専用スタックを回収した。同一HEAD CI/CodexとPR合流は未完了で、最新の状態は公開票/PRに記録する。

BDD生成と変更対象TypeScript検査はexit 0。全E2Eの追加TypeScript検査は基点の広告場面 `advertising-costs.steps.ts:659` のquery params型で失敗する。同ファイルは基点と同じblob `73ac5a8f13c8c352e730ac38a8974adab84fe4af` であり、本切片では広告域を変更しない。
