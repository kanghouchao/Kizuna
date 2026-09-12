# 資格情報の版更新と失効通知の責務境界

対象: #878。状態: 設計確定（2026-09-12）。実装は未着手。

## 問題（Problem Statement）

実装者は PlatformUser の版更新と失効イベント発行を毎回組み合わせなければならない。発行は四つの業務サービスの五箇所に分散し、通知漏れの恐れがある。停止済みへの再要求でも現在版を通知する必要があり、この Redis 更新失敗からの回復規則は状態差分だけを見る実装では失われる。SERVICE は増版するが通知しないという例外も呼出側に埋もれている。

## 解決策（Solution）

user モジュールの application 層に統一操作を置き、集約の行為と要求に応じた通知を一回の呼出にまとめる。停止・パスワード変更・全セッション失効の意図を受け、通知条件と SERVICE の例外を引き受ける。利用者向けの失効・再試行・エラーの挙動を維持する。

## ユーザーストーリー（User Stories）

1. アカウント管理者として、停止時に版更新と通知を行いたい。既発行セッションを失効させるため。
2. アカウント管理者として、停止済みでも停止を再要求したい。Redis 更新失敗後に現在版を再通知して回復するため。
3. アカウント管理者として、停止の再要求では版を進めたくない。停止の冪等性を維持するため。
4. 店長として、店員編集で停止を指定した場合に統一操作を使いたい。同じ通知規則を守るため。
5. 店長として、停止を指定しない編集では通知を起こしたくない。無関係な変更で失効させないため。
6. アカウント管理者として、再開時は版を戻さず通知もしないようにしたい。停止前のセッションを復活させないため。
7. アカウント管理者として、代理パスワード再設定を統一操作へ渡したい。更新と通知の組み忘れを防ぐため。
8. 利用者として、自助パスワード変更後に全端末を失効させたい。既存の安全性を維持するため。
9. 利用者として、現在のパスワードが不正なら変更も通知も起こしたくない。拒否された操作の副作用を避けるため。
10. 緊急昇格の撤回権限を持つ利用者として、発動者の版更新と撤回を一緒に確定したい。片方だけが成功することを防ぐため。
11. サービスID管理者として、SERVICE の停止でも版を進めたい。ADR 0025 の停止規則を維持するため。
12. サービスID管理者として、SERVICE の停止ではイベントを発行したくない。セッションを持たない本人への通知を避けるため。
13. 運用者として、Redis 反映を commit 後に行い、失敗が応答に現れてほしい。未確定データの反映と失敗の見逃しを防ぐため。
14. 店長として、授権と停止を一緒に更新しても既存の制約エラー応答を維持したい。保存順序の変更で業務エラーが内部エラーにならないようにするため。
15. 開発者として、一つの契約テストで通知規則を検証したい。重複検証と例外の分散をなくすため。
16. 開発者として、統一操作の迂回を自動検出したい。将来の追加経路でも通知漏れを防ぐため。

## 実装上の決定（Implementation Decisions）

- user.application に具体クラスの統一操作を置く。停止・パスワード変更・明示的な全セッション失効を扱う。汎用コールバックや設定可能な操作フレームワークは導入しない。
- 統一操作クラスだけを型単位の `@NamedInterface("credential-operations")` で公開し、auth の PlatformAuthService と EmergencyElevationService は `user::credential-operations` を経由して同期参照する。ActorIdentityService と ReceptionistEligibilityService の既存の公開方式に揃え、application パッケージ全体の公開や公開のためだけの interface / Impl 分割は行わない。公開メソッドは上記三操作に限定し、引数の PlatformUser は既存の `user::domain` 公開境界を使う。
- 増版は PlatformUser の行為メソッドに残す。application 層での read-modify-write や HQL 一括更新は行わない。
- 人と SERVICE は初回停止で増版し、停止済みへの再要求では増版しない。人は再要求でも現在版を通知し、SERVICE は統一操作内の明示的な種別分岐で通知しない。
- パスワード変更と明示失効は集約が進めた版を通知する。SERVICE の資格情報保持禁止は維持する。
- PlatformUserCredentialsChanged、AuthSessionService の同期受信と手書き afterCommit を維持する。user から auth のサービスを直接参照せず、集約イベントの蓄積・repository 自動発行へ移行しない。
- 対象は PlatformStaffAccountService の停止・代理再設定、StoreStaffService の停止指定、PlatformAuthService の自助変更、EmergencyElevationService の撤回、ServiceIdentityService の停止。
- 権限検証、対象取得、既存のロック順序、パスワード照合とエンコード、緊急昇格の対象選択は各業務サービスに残す。

- 統一操作は集約操作とイベント発行を担い、保存・flush は業務サービスに残す。StoreStaffService の授権変更の flush は既存の制約例外変換を通す必要があり、統一操作による先行 flush を避ける。
- 統一操作は MANDATORY で既存トランザクションへの参加を要求する。独立したトランザクションは開始せず、業務変更と版更新は一緒に commit または rollback する。
- ArchUnit で三つの集約行為の呼出と失効イベントの構築を統一操作に限定する。SERVICE の迂回許可や集約の自己呼出の一律免除は設けない。

## テスト方針（Testing Decisions）

主要な境界は統一操作の公開メソッドとする。内部の呼出順を写さず、要求に対する版・モジュール間通知・例外・トランザクションの結果を検証する。

| 現在の検証 | 変更後の責務 |
| --- | --- |
| PlatformStaffAccountServiceTest | 権限・対象・ロック・パスワード処理・停止済みでも統一操作へ進むことを存続。イベント内容を統一契約へ移す |
| StoreStaffServiceTest | false は停止済みでも統一操作へ進み、true/null は進まないことと、既存の保存エラー変換を検証する |
| PlatformAuthServiceTest | 照合・エンコード・対象を存続。不正パスワードでは統一操作を呼ばない。イベント内容を統一契約へ移す |
| EmergencyElevationServiceTest | 記録終了、撤回者ではなく発動者の失効、他の有効な発動の終了を存続。版・通知の詳細を統一契約へ移す |
| ServiceIdentityServiceTest | ロック・対象・反復停止の入口を存続。SERVICE の増版と無通知を統一契約へ移す |
| PlatformUserTest | 三つの行為による増版と SERVICE の資格情報禁止など集約不変条件を存続 |
| AuthSessionServiceTest | commit 前・rollback 時の無反映と commit 後の反映を存続。Redis 更新例外の伝播検証を追加 |
| CredentialVersionServiceTest / CredentialVersionIT | 単調キャッシュ反映と実際のセッション失効を存続 |
| 統一操作の契約テスト | 人の初回停止、同版再通知、パスワード変更、明示失効、SERVICE の初回・反復停止と無通知を集中検証 |
| トランザクション結合テスト | 実際の Spring プロキシで、トランザクションなしの拒否、外側 rollback 時の版の非永続化と Redis 無反映を検証 |
| ArchUnit / ModularityTests | 迂回を検出し、統一操作クラスが `user::credential-operations` に公開されることを検証。auth の二つの呼出元を含む `modules.verify()` で内部型への不正参照と依存循環がないことを検証 |

ArchUnit の先例は AttributionMaterializationTests、commit/rollback の先例は AuthSessionServiceTest、実際の認証結果の先例は CredentialVersionIT。既存境界を再利用し、業務サービスごとのイベント内容検証は複製しない。

## 対象外（Out of Scope）

- 発行時の enabled / SERVICE 守衛（#887）。
- 緊急昇格の失効語義の変更（ADR 0024）。
- Redis キャッシュ方式、JWT claim、ログアウトによる単一セッション失効の変更。
- HTTP API、DB スキーマ、フロントエンド、認証モジュール全体の再編。
- 互換レイヤー、移行処理、新規依存。

## 補足（Further Notes）

- 親の地図は #873。本票は設計 spec であり、ここでは実装しない。
- ADR 0022 の版照合・楽観ロック・commit 後の単調キャッシュ反映と矛盾しない。同 ADR に要求単位の通知責務と保存境界を補足する。新規 ADR は不要。
- ADR 0025 の SERVICE も増版する規則は変更不要。
- 保存・トランザクション境界とテスト境界は確認済み。実装時はこの仕様と既存 ADR に従う。

### spec 完了条件

- [x] application 層で束ね、既存イベント機構と afterCommit を維持する。
- [x] 統一操作だけを公開する named interface と auth からの参照境界を定義した。
- [x] SERVICE は増版し、統一操作内で通知を省く。
- [x] テストの置換・存続対応表を作成した。
- [x] ADR 0022 と ADR 0025 との整合を確認した。
- [x] 保存・トランザクション境界とテスト境界を最終確認した。
