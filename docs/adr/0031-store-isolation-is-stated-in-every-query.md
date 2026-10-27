# 店舗分離は各クエリが store_id を明示する — Hibernate filter と要求文脈の ThreadLocal を撤去する

Status: Accepted（実装は #1048、PR #1055 / #1061 / #1068）

## 決定時の背景

店舗文脈はテナント期の設計を引き継ぎ、ThreadLocal（`StoreContext`）に暗黙に置かれ、`@StoreScoped` の切面が Hibernate の `storeFilter` へ流し込んでいた。店舗文脈が無いと filter は何もせず、読みが全店舗の行を返す（fail-open）。[ADR 0002](0002-store-scoped-entities-declare-filters-statically.md) の静的全量宣言は filter の宣言漏れを閉じたが、有効化の漏れ — 文脈が確立されないまま走る読み — は閉じられなかった。

「側」の撤去以降、店舗が要るかどうかは端点ごとに決まるのに、店舗文脈の確立は URL 前綴 `/store/**` に掛けたインターセプタに依存していた。前綴に頼る限り、店舗を要する端点がその外に置かれた時点で filter は黙って外れる。

## 決定

- 現在店舗を要する handler は `@CurrentStore Long storeId` を宣言し、引数解決器が `X-Store-ID` を検証する（無い・不正 → 400、本人の店舗集合外 → 403、実在しない → 400）。宣言の無い handler はヘッダを無視する。
- 店舗級の service は店舗 id を第一引数で受け、各クエリは根と、結合・副問い合わせする店舗級実体のすべてに `store_id` の条件を持つ。店舗級実体の repository は `StoreScopedRepository` を継承し、店舗条件の無い `findById` / `findAll` を公開しない。
- 店舗集合を跨ぐ読み（横断一覧・集計）は本人の `StoreScope` を引数で受け、SPECIFIC_STORES なら `store_id in (:storeIds)`、ALL_STORES なら店舗条件を付けない。
- 書き込みは店舗 id を明示的に設定する。`StoreScopeStampListener` は値を補わず、null を拒むだけにする。
- `StoreContext`・`@StoreScoped` / `@StoreSetScoped` と両切面・`StoreScopeExecutor`・`storeFilter` / `storeSetFilter` を撤去する。

## 帰結

- 店舗条件の欠落を止める静的検査は無い。正しさはレビューと e2e が担保する。`StoreIsolationTests` が見るのは、`store_id` 列を持つ実体が `StoreScopedEntity` を継承していることだけである。
- 条件を書き忘れれば他店舗の行が見えうる点は filter 期と同じだが、文脈の有無という実行時の状態には依存しなくなり、正しさはクエリの字面だけで判定できる。
- 意図して店舗を跨ぐクエリは実際の所有境界（トークンの digest、user id、member id）で鍵付けし、その境界を repository の宣言に一行で記す。
