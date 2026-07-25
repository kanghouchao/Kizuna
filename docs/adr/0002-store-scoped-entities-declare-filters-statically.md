# StoreScopedEntity は storeFilter・storeSetFilter を静的に全量宣言する

Status: Accepted

## Context

集合作用域（`StoreScope` / `storeSetFilter` / `@StoreSetScoped`）は、`@StoreSetScoped` を付けたサービスメソッド実行時に
Hibernate の二次フィルタ `storeSetFilter` を有効化して読みを授権店舗集合に濾過する。フィルタは対象 `@Entity` が自身のクラスに
`@Filter(name="storeSetFilter", condition="store_id in (:storeIds)")` を宣言していない限り**静かに no-op** となり、
SPECIFIC_STORES ユーザーへ全店舗の行を返す fail-open になる。

単店版（`storeFilter` / `@StoreScoped`）にはこの不変量を機械検証する `StoreIsolationTests` が既にあるが、判定基準は
「`store_id` 列を持つ全 `@Entity`」という実体自身の構造から静的に導出できる集合だった。対して `storeSetFilter` が要る集合は
「`@StoreSetScoped` メソッドが実際に読むエンティティ」であり、これは実体の構造からは導出できず、コード上のどのサービスメソッドが
どのエンティティを読むかという到達可能性（データフロー）に依存する。

到達可能性を機械的に閉じる案として、ArchUnit・コンパイル時チェッカ（Error Prone 等）・Hibernate の
`@FilterDef(autoEnabled=true)` によるランタイム反転・`@StoreSetScoped` メソッドと対象エンティティの手書き登録台帳（反射で
使用箇所数と照合）を検討した。ArchUnit とコンパイル時チェッカは、対象メソッドが Spring Data リポジトリ + JPQL 文字列
（`PlatformOrderView` 投影）経由でエンティティに到達するため、バイトコード解析ではその経路を追えず不成立。ランタイム反転は
Hibernate コミュニティに確立された先例がなく、作用域機構全体の再設計になり本件のスコープを超える。登録台帳は「新規登録の
更新漏れ」自体を機械的には検知できず（登録済みエンティティの宣言漏れしか拾えない）、維持コストも残る。

## Decision

到達可能性の判定を諦め、`StoreScopedEntity` を継承する全エンティティ（現在 8 個: `Order` `Cast` `CastFieldDefinition`
`CastInvitation` `Customer` `Shift` `ShiftRequest` `StoreProfile`）に、現に `@StoreSetScoped` から読まれているかに関わらず
`@Filter(name="storeSetFilter", condition="store_id in (:storeIds)")` を静的に宣言させる。`StoreIsolationTests` に
`storeFilter` 版と同型の機械検証を追加し、以後どのエンティティも宣言漏れがあれば単体テストで fail-loud にする。

未使用の Hibernate `@Filter` は明示的に有効化されない限りランタイムコストを持たない。現時点で唯一の `@StoreSetScoped`
使用箇所である `PlatformOrderService.list()` が呼ぶ `OrderRepository.findPlatformViews` は `Order` 単体を select する
JPQL（`PLATFORM_VIEW_SELECT`）で、`Cast`/`Customer` 等への join を意図的に張っていない（既存コメント「店舗（表示名）の
join は張らない」）。よって現時点では、他 7 エンティティへの宣言追加は実行時挙動を一切変えない。

## Consequences

`storeSetFilter` を宣言していても、そのエンティティを実際に読む `@StoreSetScoped` メソッドが存在しない限り宣言は
休眠（dormant）状態のままになる。ソースだけを見ると「このエンティティは既に集合作用域の平台横断一覧に対応済み」と
誤読されうるため、その旨は `StoreIsolationTests` の新規テストメソッドの Javadoc に一箇所だけ明記し、各エンティティ側には
コメントを重複させない。将来 `@StoreSetScoped` を新しい集約へ拡張する側は、対象エンティティへの `@Filter` 追加を
意識する必要がなくなり、`@StoreSetScoped` を付けて濾過を有効化する作業だけに集中できる。

Hibernate の session フィルタは有効化した呼び出し元に関わらず、その Session 内で読まれる**全エンティティ**に及ぶ —
将来 `@StoreSetScoped` メソッドが `Cast`/`Customer` 等への join や遅延ロードを新たに持ち込んだ場合、それらのエンティティは
（本 ADR により既に `@Filter` 宣言済みのため）追加の意識なく自動的に濾過対象へ入る。狙って有効化した対象以外にも及ぶという
意味で影響範囲は静的に読み切れないが、方向は常に「安全側」（濾過が想定より広くかかる）であり fail-open の再発ではない。

本設計が捉えられない残存ケースが一つある：`@StoreSetScoped` が `StoreScopedEntity` を継承しない型（素の DTO や
`StoreScopedEntity` 系統外のエンティティ）を読む状況を将来作った場合、`StoreIsolationTests` の走査対象外のため
このガードは無力（フィルタは静かに no-op のまま）。現行 8 エンティティは全て `StoreScopedEntity` 継承であり
`StoreIsolationTests` の別テストが「`store_id` 列を持つ実体は例外なく `StoreScopedEntity` を継承する」ことを
既に固定しているため、今のところ実害はない。
