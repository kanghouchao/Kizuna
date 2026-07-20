# Kizuna

プラットフォーム型の店舗運営システム（CMS/CRM/HRM）。単一のグループが運営するプラットフォームが複数の店舗を管理し、各店舗は自身のサイト・従業員・顧客・注文を運営する。

## Language（用語集）

### プラットフォームとテナント

**Tenant（テナント）**:
プラットフォーム管理の文脈で管理される契約対象。店舗と 1 対 1 で対応する。独自のドメインとサイトを持つ。
_Avoid_: Shop, Organization

**Store（店舗）**:
Tenant と同一の対象を、店舗運営の文脈から自称したもの。コード上は店舗側スコープの命名接頭辞（StoreUser、store-orders）。
_Avoid_: Branch

**Central（プラットフォーム側、旧称）**:
Central は構造概念としては退場済みで、プラットフォーム管理系権限グループ（`Capability.Console.PLATFORM`）の旧称として残るのみ。機能は職位（権限バンドル）に従い、データは店舗（StoreScope）に従う。Platform と Store は同一システムの 2 つのアクセスコンソール（権限境界）であり、独立した 2 つのシステムではない。

### アカウント

**PlatformUser（プラットフォームユーザー）**:
プラットフォーム共通アカウントとしての「プラットフォーム身分」。email でログインし、授権は「ロール×店舗集合」（店舗集合は「全店舗」「個別店舗」の 2 種のみ）で表す。旧 CentralUser / StoreUser の二本立て認証は撤去済みで、PlatformUser が唯一のアカウント種別である（#326）。
_Avoid_: PlatformAccount、「テナントユーザー」系の呼称

統一ログイン（`/platform/login`）はロールに応じて自動ルーティングする（HQ_ADMIN → Central、STORE_MANAGER/STORE_STAFF → Store）。店舗コンソールは平台トークン + `X-Store-ID` を集合作用域（授権店舗集合）で fail-closed 検証したうえで旧業務 API に過橋する（#324）。この過橋機構は撤去せず恒久的に運用する（旧 CentralUser/StoreUser の二本立て認証自体は #326 で撤去済み）。

**AuthSession（認証セッション）**:
発行済みの 1 枚の JWT が表す認証状態。ログアウトとパスワード変更はいずれも唯一の無効化経路（token ブラックリスト）を通じて現在のセッションを失効させる。
_Avoid_: Token の裸使用（token は担体、session は概念）

**集合作用域（StoreScope / storeSetFilter / @StoreSetScoped）**:
PlatformUser の授権を表す店舗集合（ALL_STORES または SPECIFIC_STORES の店舗 ID 集合）。読みは Hibernate の第二 filter（`storeSetFilter`）が機構的に濾過する fail-closed 設計（解決不能なら例外）。書きは明示的単一 storeId を受け取り、その storeId が授権集合に含まれるか検証したうえで既存の単店機構（StoreContext + storeFilter）へ委譲する。
_Avoid_: 読み・書きを同一機構と混同すること（読みは集合フィルタ、書きは単一 storeId 検証で別経路）

スタッフ管理（`/platform/staff`、HQ_ADMIN 限定）で PlatformUser のロール×店舗集合を付与・変更できる（#325）。対象は HQ_ADMIN/STORE_MANAGER/STORE_STAFF のみで、CAST/MEMBER は別チケットの専用フローが扱う。JWT はステートレスなため、変更は対象スタッフの次回ログインから反映される（即時セッション失効はしない）。

### 店舗運営

**Cast**:
店舗に在籍する接客担当者。HRM の管理対象。

**Customer（顧客）**:
店舗の顧客。CRM の管理対象。単一の店舗に帰属する。

**Member（会員）**:
プラットフォーム層の会員身分。Customer（店舗 CRM 台帳・店舗層）とは別概念であり、Member は複数店舗の Customer 台帳と紐づき得る。非会員の顧客は存在し続ける（Customer だけがあり Member がない状態）。集約の実装は後続チケット（本項は用語の予約）。
_Avoid_: Member と Customer の混用、「会員」を店舗台帳の意味で使うこと

**Order（注文）**:
顧客の店舗における 1 回の予約／受注記録。Customer、Cast、および接客担当（PlatformUser）に紐づく。
_Avoid_: Reservation, Booking

**StoreProfile（店舗サイト設定）**:
店舗サイトのブランドと外観の設定（テンプレート、logo、banner、SNS リンク等）。
_Avoid_: TenantConfig（旧名。SystemConfig と紛らわしい）

### プラットフォーム設定

**SystemConfig（システム設定）**:
プラットフォームレベルのキーバリュー設定（SMTP、メンテナンスモード等）。Central のみが管理できる。
_Avoid_: Config（裸使用。StoreProfile と混同しやすい）

**Menu（メニュー）**:
管理画面のナビゲーションメニューツリー。Central と Store の 2 スコープで同一概念を共有する。コード上は単一の Menu 集約（`t_menus`）へ統合済み（2026-07-18、#404 決定 2 / #409。CentralMenu / StoreMenu は統合前の歴史名）。
_Avoid_: TenantMenu（旧名）

## Open questions（未解決の論点）

- **Order.storeName**：注文上の自由テキストの「店名」フィールド。Tenant と店舗は 1:1 と確認済みのため、このフィールドの実際の意味（屋号／ブランド／チャネル名？）は明確化後に改名または削除する。
- **Customer.rank / classification**：いずれも自由テキストで、等級／区分の正式な値体系は未定義（DB デフォルトは rank='SILVER'）。UI はテキスト入力で暫定対応し、業務側で値集合が確定次第 enum + ドロップダウンに収束させる。
