# Kizuna

マルチテナントの店舗運営システム（CMS/CRM/HRM）。プラットフォーム側（Central）が複数のテナントを管理し、各テナントは 1 つの店舗として、自身のサイト・従業員・顧客・注文を運営する。

## Language（用語集）

### プラットフォームとテナント

**Tenant（テナント）**:
プラットフォーム管理の文脈で管理される契約対象。店舗と 1 対 1 で対応する。独自のドメインとサイトを持つ。
_Avoid_: Shop, Organization

**Store（店舗）**:
Tenant と同一の対象を、店舗運営の文脈から自称したもの。コード上は店舗側スコープの命名接頭辞（StoreUser、store-orders）。
_Avoid_: Branch

**Central（プラットフォーム側）**:
プラットフォーム運営側のスコープ。Central と Store は同一システムの 2 つのアクセススコープ（権限境界）であり、独立した 2 つのシステムではない。

### アカウント

**CentralUser（プラットフォーム管理者）**:
プラットフォーム側の管理者アカウント。username でログインし、ロールのディレクトリは店舗側から独立している。

**StoreUser（店舗ユーザー）**:
店舗の従業員アカウント。email でログインし、データはテナント単位で分離される。
_Avoid_: TenantUser（旧名）

**AuthSession（認証セッション）**:
発行済みの 1 枚の JWT が表す認証状態。ログアウトとパスワード変更はいずれも唯一の無効化経路（token ブラックリスト）を通じて現在のセッションを失効させる。
_Avoid_: Token の裸使用（token は担体、session は概念）

### 店舗運営

**Cast**:
店舗に在籍する接客担当者。HRM の管理対象。

**Customer（顧客）**:
店舗の顧客。CRM の管理対象。単一の店舗に帰属する。

**Order（注文）**:
顧客の店舗における 1 回の予約／受注記録。Customer、Cast、および接客担当（StoreUser）に紐づく。
_Avoid_: Reservation, Booking

**StoreProfile（店舗サイト設定）**:
店舗サイトのブランドと外観の設定（テンプレート、logo、banner、SNS リンク等）。
_Avoid_: TenantConfig（旧名。SystemConfig と紛らわしい）

### プラットフォーム設定

**SystemConfig（システム設定）**:
プラットフォームレベルのキーバリュー設定（SMTP、メンテナンスモード等）。Central のみが管理できる。
_Avoid_: Config（裸使用。StoreProfile と混同しやすい）

**Menu（メニュー）**:
管理画面のナビゲーションメニューツリー。Central と Store の 2 スコープで同一概念を共有する。コード上は menu モジュール内の 2 エンティティ（CentralMenu / StoreMenu。2026-07 にテーブル統合しないと決定）。
_Avoid_: TenantMenu（旧名）

## Open questions（未解決の論点）

- **Order.storeName**：注文上の自由テキストの「店名」フィールド。Tenant と店舗は 1:1 と確認済みのため、このフィールドの実際の意味（屋号／ブランド／チャネル名？）は明確化後に改名または削除する。
- **Customer.rank / classification**：いずれも自由テキストで、等級／区分の正式な値体系は未定義（DB デフォルトは rank='SILVER'）。UI はテキスト入力で暫定対応し、業務側で値集合が確定次第 enum + ドロップダウンに収束させる。
