# 店舗作用域集約は店舗の表示名を非正規化保存しない

Status: Accepted

## Context

`Order.storeName`（#452 で削除）は「店舗の表示名」を `Order` 行に非正規化保存した自由文字列で、正規の帰属先である `store_id` と重複していた。`Shift`/`Cast` は同種の要求を `st.name as storeName` という `Store` への JOIN 投影で満たしており、`Order` だけが非正規化された例外だった。

## Decision

店舗作用域エンティティ（`StoreScopedEntity` 継承）は、店舗名など `store_id` から導出可能な情報を自身の列として重複保存しない。表示が必要になった時点で `Store` への JOIN 投影（`Shift`/`Cast` の既存パターン）で都度導出する。`Order` の店舗横断一覧（`PLATFORM_VIEW_SELECT`）は #452 の時点でこの表示要求自体が存在しない（フロントエンド未消費）ため、JOIN を追加せず `store_id` のみを返す——ADR 0002 が前提とする「Cast/Customer 等への join を意図的に張っていない」という記述と矛盾しない。
