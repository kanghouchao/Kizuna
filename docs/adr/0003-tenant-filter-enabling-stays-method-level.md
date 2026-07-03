# テナントフィルタの有効化は @TenantScoped メソッド単位のまま据え置く

架構深化バッチ PR-B の Step 2（`docs/architecture-deepening-plan.md`）として、Hibernate `tenantFilter` の有効化を 24〜25 箇所の `@TenantScoped` メソッドアノテーションから、リクエスト単位の単一シームへ引き上げる案を検証した。想定案は `TenantIdInterceptor`（`HandlerInterceptor#preHandle`）でリクエスト最早期にフィルタを有効化すること。

**却下**：`spring.jpa.open-in-view: false`（`application.yml`）により、`preHandle` の時点ではリクエストスレッドに Hibernate Session がまだ存在しない（Session は `@Transactional` 境界に入って初めて JpaTransactionManager が生成する）。`entityManager.unwrap(Session.class)` をこの時点で呼んでも、後続の実トランザクションとは無関係な使い捨てインスタンスにしかフィルタを設定できず、意図した効果を持たない。

代替として「トランザクション開始（`afterBegin`）をグローバルに捕捉する `TransactionExecutionListener`（Spring Framework 6.1+）を登録し、`tenantContext.isTenant()` の間は毎トランザクションでフィルタを有効化する」案も検討したが、以下の理由で見送る：

- 本バッチ実装済みの [[C2 Step 1]]（`TenantIsolationTests` を tenant_id 列基準へ拡面）が、#216 の実際の事故クラス（エンティティに `@Filter` を宣言し忘れる）を機械的に閉じている。Step 2 が防ぐのは別クラスの事故（サービスメソッドに `@TenantScoped` を付け忘れる）で、実インシデントの前例がない。
- `@TenantScoped` は 1 行の明示的マーカーであり、CentralMenuServiceImpl/StoreMenuServiceImpl 統合（C4）のような実体のある重複ロジックではない。除去してもテスト対象・バグの局所性は増えず、暗黙のグローバル挙動に置き換わるだけで可読性が下がる（`grep @TenantScoped` でテナント境界を横断する箇所を一覧できる現状の利点を失う）。
- 実装コストと検証コストが非対称に高い：Spring Boot 側に前例のない新機構の導入、ネストしたトランザクション（`REQUIRES_NEW` 等）での再有効化の検証、既存 24+ 箇所の単体テストとは別種の統合テストが必要になる。

## Consequences

- `@TenantScoped` + `TenantFilterEnable`（AOP）を唯一の有効化経路として維持する。新しいテナントスコープの service メソッドを追加する開発者は、引き続き自分でこのアノテーションを付ける責任を負う。
- 機械的な安全網は引き続き `TenantIsolationTests`（エンティティに `@Filter` があるか）のみ。「@TenantScoped の付け忘れ」を機械検出する仕組みは今回導入しない——将来この種の事故が実際に起きた場合に再検討する。
- PR-B は Step 1 のみを届け、Step 2 は本 ADR をもって終了とする。
