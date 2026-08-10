# 会員ポイント台帳は platform 帰属とし、発生店舗は originating_store_id で帰属記録する

Status: Accepted

## Context

会員ポイント台帳（`t_point_entries` / `t_point_usage_allocations`）はグループ共通の会員残高の唯一の正本であり、
A 店で獲得したポイントを B 店で利用できることが仕様の中核にある。同時に、各仕訳は発生店舗を監査根拠として
記録しなければならない（将来の店舗間精算の基盤）。つまり台帳行は「店舗への参照を持つが、店舗に帰属しない」
初めての業務テーブルである。

一方、本リポジトリの店舗行レベル分離は ADR 0002 の通り機械的に強制されている：`StoreIsolationTests` は
`@Column(name = "store_id")` を持つ全 `@Entity` に対し、`StoreScopedEntity` の継承と `storeFilter` /
`storeSetFilter` 両宣言を豁免なしで要求する。台帳行をこの機構に載せると、店舗文脈での読みが他店舗発生の
仕訳を濾過し、残高計算そのものが壊れる — 分離機構の fail-closed が、店舗横断であることが正しさの条件で
あるデータに対しては「安全側」ではなく「壊す側」に働く。

## Decision

台帳エンティティは `BaseEntity`（platform 層）とし、`StoreScopedEntity` を継承しない。発生店舗の列は
`store_id` ではなく **`originating_store_id`** と命名する。これは店舗**帰属**（scope）ではなく発生源の
**帰属記録**（attribution）であるという意味論の宣言であり、同時に `StoreIsolationTests` の走査基準
（`store_id` 列名）に対して意図を機械可読にする — この列名を選ぶ限り、台帳が店舗分離機構へ引き込まれる
ことはなく、逆に将来誰かが `store_id` と改名すれば単体テストが即座に落ちて本 ADR との衝突を知らせる。

FK は `ON DELETE SET NULL` とする。店舗削除で当該店舗の受注（`t_orders`）は CASCADE で消えるが、会員の
残高と仕訳履歴はグループ資産として生き残らなければならない（`order_id` 参照も同様に SET NULL）。

## Consequences

台帳には Hibernate フィルタによる行レベル分離が存在しない。店舗コンソールからの読み書き口
（完了処理・残高照会・手動調整）は application 層の権限と「自店舗の顧客に ACTIVE な紐づけがあること」の
検証で守る — 会員コード直指定の全会員照会口を店舗側に作らないことが、この設計の運用上の前提である。

会員側（ポータル）への残高読み口は本 ADR の範囲外であり、`MemberFacingLedgerLeakIT` が導入まで
（ポータル表示のチケットが意図的に改定するまで）残高項目の会員側漏出を拒否リストで塞いでいる。

`store_id` 列名を持つ通常の店舗帰属エンティティは、従来通り例外なく ADR 0002 の対象である。本 ADR は
「店舗を跨ぐことが正しさの条件であるデータ」にのみ適用される例外であり、二例目を作る際は同じ判定
（分離が正しさを壊すか）を経ること。
