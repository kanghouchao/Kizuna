# 保存時の整合性違反変換を共有する

対象: [#877](https://github.com/kanghouchao/Kizuna/issues/877)。状態: 実装・検証完了。
採用点の調査基準: `0b6e30e5f3d1f1e5a25f9190e35a96ee770676ec`。

## 目的と範囲

application 層で繰り返される保存時の整合性違反の捕捉を共有する。保存方式、flush 時機、制約から業務例外への対応表は呼出側が指定する。共有側へ JPA repository の依存を持ち込まない。

対象は手書きの `saveAndFlush` と例外変換の組合せ 8 箇所、および既存の `IntegrityMappedSaves` 呼出 5 箇所。削除・一括更新・通常の `save`・トランザクション外の再試行と競合変換は対象外とする。

元票の「手書き 13 箇所がすべて saveAndFlush」という記述は調査基準のコードと一致しない。手書きの `catch` から `IntegrityViolations.translate` を呼ぶ箇所は 14 個で、内訳は即時保存 8、通常保存 1、削除後 flush 4、一括更新 1。このうち即時保存だけを今回採用する。既存 helper の 5 呼出を加えた今回の移行総数は 13 であり、元票の 13 箇所とは別の集合である。

## 共有インターフェースと責務

既存の `com.kizuna.shared.exception.IntegrityViolations` に次のメソッドを追加する。

```java
public static <T> T translateOnFailure(
    Supplier<T> operation,
    Map<DbConstraint, Supplier<RuntimeException>> table);
```

呼出例:

```java
return IntegrityViolations.translateOnFailure(
    () -> repository.saveAndFlush(entity),
    table);
```

- `operation.get()` を同期的に一回だけ実行し、その戻り値をそのまま返す。
- 捕捉するのは `DataIntegrityViolationException` だけとし、既存の `translate(ex, table)` が返す例外を送出する。他の例外はそのまま伝播する。
- 対応表にない制約、制約名が取得できない違反は元の例外をそのまま送出する。既存の全域ハンドラによる分類を変えない。
- トランザクションの開始・終了、再試行、独自の flush は行わない。`Runnable` 版や repository を受ける overload は追加しない。戻り値を使わない保存も `Supplier<T>` で表せる。
- `IntegrityViolations` は共有カーネルの例外変換を担い、`DbConstraint` は変換対象の SQL 制約名を保持する。業務操作の意味を知る application 層が対応表を持つ。同じ制約でも、参照先の削除と存在しない参照先への保存では異なる業務例外になり得る。
- `user/application/IntegrityMappedSaves.java` は削除する。共有パッケージへの同名クラスの移設や互換ラッパーは設けない。

責務分担と対応表を呼出側に置く理由は `IntegrityViolations` のクラス Javadoc を正本とし、呼出点に同じ説明を複製しない。flush は現在の永続化コンテキストの変更を反映するため、渡した一つの entity だけの検査とは説明しない。また、コレクションの変更が必ず commit まで flush されないという断定を避け、明示 flush によって捕捉範囲内で違反を顕在化させる意図を書く。

## 採用点

以下のクラスは各モジュールの `application` パッケージに属する。

| 区分 | モジュール | クラス・メソッド | 処置 |
| --- | --- | --- | --- |
| 新規採用 | auth | `LineAuthService.link` | 既存の保存と変換を包む |
| 新規採用 | auth | `EmergencyElevationService.persist` | 同上 |
| 新規採用 | cast | `CastInvitationService.issue` | 保存を包み、返された entity から応答を組み立てる |
| 新規採用 | cast | `CastInvitationAcceptanceService.link` | 既存の保存と変換を包む |
| 新規採用 | member | `MemberRegistrationService.saveUser` | 同上 |
| 新規採用 | member | `MemberRegistrationService.saveLineUser` | 同上 |
| 新規採用 | point | `BenefitRuleService.persist` | 同上 |
| 新規採用 | shift | `AttendanceService.saveWithinUniqueness` | 同上 |
| 既存移行 | user | `PlatformStaffService.save` | helper 呼出を lambda に置換 |
| 既存移行 | user | `StoreStaffService.save` | 同上 |
| 既存移行 | user | `ServiceIdentityService.save` | 同上 |
| 既存移行 | user | `StoreManagerService.save` | 同上 |
| 既存移行 | user | `RoleService.save` | 同上。削除経路は含めない |

既存の保存順序、flush の回数・位置、戻り値、制約の対応先とメッセージを維持する。`EmergencyElevationService.persist` の保存結果の ID と `BenefitRuleService.persist` の更新後 version を従来どおり後続処理へ渡す。lambda には保存だけを入れ、周辺の業務操作まで捕捉範囲を広げない。

## 対象外

| 層 | クラス・メソッド | 除外理由 |
| --- | --- | --- |
| application | `CastInvitationAcceptanceService.saveUser` | 通常の `save`。即時 flush への変更は今回の範囲外 |
| application | `CastService.delete` | 削除と flush |
| application | `CustomerService.delete` | 削除と flush |
| application | `StoreRegistryService.delete` | 削除と flush |
| application | `RoleService.delete` | 削除と flush |
| application | `CustomerMergeService.repointLinks` | 一括付替更新 |
| application | `CustomerProvisioningService.ensureMemberRequestCustomer` | `violates` で競合を専用例外へ変換し、呼出元の再試行へ渡す別経路 |
| controller | `CustomerPointController.adjust` | トランザクション外で競合を捕捉し再試行 |
| controller | `OrderController.correctAttribution` | トランザクション外で競合を捕捉し再試行 |
| controller | `OrderController.pointRollback` | トランザクション外で競合を業務例外へ変換。再試行ではない |
| controller | `OrderApplicationController.confirm` | `MemberCustomerConflictException` を捕捉して一回再試行 |

controller の 4 箇所をすべて再試行とする元票の説明も修正対象となる。今回の共有インターフェースで表現できることは採用範囲を広げる理由にしない。`DbConstraint` の項目追加、HTTP 契約、DB スキーマの変更も含めない。

## 検証と完了条件

既存の `IntegrityViolationsTest` と各サービスの例外写像の断言を維持する。新メソッドの戻り値、対応済み違反、未対応違反の同一例外伝播、他の例外の伝播を既存のテストクラスで確認する。原因連鎖の解析テストは既存の `translate` のものを引き続き用い、重複させない。

実装時には対象サービスのテストで既存の repository 呼出と結果を確認し、最終検証は `CONTRIBUTING.md` の Taskfile 手順に従う。

- [x] 新規採用 8 箇所と既存移行 5 箇所を列挙した。
- [x] controller 4 箇所とその他の除外操作を列挙した。
- [x] 公開メソッドの配置・命名・依存・責務を決定した。
- [x] 制約から業務例外への対応表を呼出側に残すと定めた。
- [x] 実装と所定の検証が完了した。

これは実装仕様であり、業務用語の追加や既存の業務上の不変条件の変更はない。`CONTEXT.md` の変更および新しい ADR の作成は不要。

## 検証結果（2026-09-12）

- JDK 25 で共有インターフェースと対象サービスの単体テストを実行し、終了コード 0。
- `task lint-repo`、`task lint service=backend`、`task test service=backend`、`task build service=backend` はすべて終了コード 0。後端の全単体テスト・カバレッジ検査・統合テストを含む。
- 規約と本仕様の独立レビューはいずれも指摘 0 件。
- 前端テストと E2E は未実施。
