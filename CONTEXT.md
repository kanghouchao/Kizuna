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
Central は構造概念としては退場済みで、プラットフォーム管理系権限グループ（`PermissionCode.Console.PLATFORM`）の旧称として残るのみ。機能は職位（ロール）に従い、データは店舗（StoreScope）に従う。Platform と Store は同一システムの 2 つのアクセスコンソール（権限境界）であり、独立した 2 つのシステムではない。

### アカウント

**PlatformUser（プラットフォームユーザー）**:
プラットフォーム共通アカウントとしての「プラットフォーム身分」。email でログインし、授権は「ロール×店舗集合」（店舗集合は「全店舗」「個別店舗」の 2 種のみ）で表す。旧 CentralUser / StoreUser の二本立て認証は撤去済みで、PlatformUser が唯一のアカウント種別である（#326）。
_Avoid_: PlatformAccount、「テナントユーザー」系の呼称

統一ログイン（`/platform/login`）はロールに応じて自動ルーティングする（HQ_ADMIN → Central、STORE_MANAGER/STORE_STAFF → Store）。店舗コンソールは平台トークン + `X-Store-ID` を集合作用域（授権店舗集合）で fail-closed 検証したうえで旧業務 API に過橋する（#324）。この過橋機構は撤去せず恒久的に運用する（旧 CentralUser/StoreUser の二本立て認証自体は #326 で撤去済み）。過橋資格（店舗コンソール権限の保持）はログイン時に JWT の `storeBridge` claim として確立され、`GET /platform/me` の `store_bridge` にも同源で露出される（フロントエンドに権限→コンソールの対応表を複製させない — #428）。

**AuthSession（認証セッション）**:
発行済みの 1 枚の JWT が表す認証状態。失効には 2 つの粒度がある。**セッション単位失効**（ログアウト・パスワード変更 = token ブラックリスト）は当該 1 枚のトークンのみを失効させる。**アカウント単位失効**（スタッフ停止 = user ブラックリスト）は当該ユーザーが保有する全セッションを一括して即時失効させ、再開すれば即時に解除される。
_Avoid_: Token の裸使用（token は担体、session は概念）

**集合作用域（StoreScope / storeSetFilter / @StoreSetScoped）**:
PlatformUser の授権を表す店舗集合（ALL_STORES または SPECIFIC_STORES の店舗 ID 集合）。読みは Hibernate の第二 filter（`storeSetFilter`）が機構的に濾過する fail-closed 設計（解決不能なら例外）。書きは明示的単一 storeId を受け取り、その storeId が授権集合に含まれるか検証したうえで既存の単店機構（StoreContext + storeFilter）へ委譲する。
_Avoid_: 読み・書きを同一機構と混同すること（読みは集合フィルタ、書きは単一 storeId 検証で別経路）

スタッフ管理（`/platform/staff`、HQ_ADMIN 限定）で PlatformUser のロール×店舗集合を付与・変更できる（#325）。対象は HQ_ADMIN/STORE_MANAGER/STORE_STAFF のみで、CAST/MEMBER は別チケットの専用フローが扱う。停止（enabled=false）だけが唯一の例外で即時失効する。ロール・店舗集合の変更は従来どおり次回ログイン反映。

**Role（ロール）/ Permission（権限）**:
授権モデルは伝統的な RBAC。**Permission** は機能権限の目録行で、コード定義（`PermissionCode` enum）を `t_permissions` へ播種したものが正本であり書き込み API を持たない。**Role** は権限の束（`t_roles` + `t_role_permissions`）で、平台既定ロール（`is_system=true`、播種の 3 件）は改廃を拒否し、利用者は `/platform/roles` から自作ロールを自由に追加・改廃できる。**ロールは店舗を持たない** — 担当店舗集合は PlatformUser 側（StoreScope）に付く。SecurityContext 上の authority は所持権限を `PERM_` 接頭辞で発行したもので、ロール名は authority に現れない（Spring Security の `ROLE_CAST` / `ROLE_MEMBER` は本人種別の標識であり別物）。
_Avoid_: 能力束・Capability（旧称）、ロールへの店舗の紐付け

**店舗コンテキスト（Store Context）**:
フロントエンドの「現在店舗・授権店舗・店舗切替・店舗リンク生成・ログイン後着地の授権店舗解決」を一手に担う seam（`entities/user` の StoreContextProvider / useStoreContext）。provider は platform / store 両コンソールの layout に搭載され、`me()` + `stores()` は provider で 1 回のみ取得する。店舗パス組立の知識は `shared/lib/store-route`（storePath / storeEntryPath / resolveStoreHref / replaceStoreIdInPath）へ集約し、各所での裸テンプレート字面を禁じる。店舗コンソールへの着地は入口ルート `/store/entry`（`_pages/store-entry`）一箇所に集約する。入口は UI を持たず、授権店舗（前回選択 cookie を優先、無ければ先頭）とメニュー由来の遷移先を解決して差し替え遷移する。着地先をメニューから引くのは、権限を絞ったロールでも必ず自分が到達できる画面に着かせるためで、固定の着地先は権限次第で拒否される。
_Avoid_: 各コンポーネントでの me()/stores() 個別取得、店舗パスの手組み

### 店舗運営

**Cast**:
店舗に在籍する接客担当者。HRM の管理対象。**在籍状態**（在籍中 = ACTIVE / 在籍停止 = INACTIVE）を持ち、指名候補になれるのは在籍中のキャストだけ。
_Avoid_: 在籍状態を「有効／無効」と呼ぶこと（アカウントの停止＝AuthSession のアカウント単位失効と紛らわしい）

**Customer（顧客）**:
店舗の顧客。CRM の管理対象。単一の店舗に帰属する。

**Member（会員）**:
プラットフォーム層の会員身分。Customer（店舗 CRM 台帳・店舗層）とは別概念であり、Member は複数店舗の Customer 台帳と紐づき得る。非会員の顧客は存在し続ける（Customer だけがあり Member がない状態）。集約の実装は後続チケット（本項は用語の予約）。
_Avoid_: Member と Customer の混用、「会員」を店舗台帳の意味で使うこと

**PointLedger（会員ポイント台帳）**:
プラットフォーム層にある会員ポイントの唯一の正本。全増減（付与・利用・取消・失効・手動調整・退会消去）を仕訳（種別・理由・実行主体・元取引・発生店舗・日時）として記帳し、残高は仕訳から再構成する。期限別残高を持ち、利用は期限の早い仕訳から充当する。会員（Member）に帰属して店舗を跨ぐ（A 店獲得→B 店利用）。発生店舗は帰属属性 `originating_store_id` であり StoreScope の隔離軸ではない（ADR 0006）。非会員（未紐づけ顧客）にポイントは存在しない。
_Avoid_: 店舗レベルの残高列（旧 Customer.points — 廃止済み）を復活させること、残高だけを保存して仕訳を省くこと

**Order（注文）**:
顧客の店舗における 1 回の予約／受注記録。Customer、Cast、および接客担当（PlatformUser）に紐づく。店舗への帰属は `store_id`（StoreScopedEntity）のみで表し、店舗名を非正規化して保存しない（表示が必要な場合は Store への JOIN で導出する。ADR 0003）。
_Avoid_: Reservation, Booking

**指名 / 指名候補（Nomination / Cast Candidate）**:
Order に紐づく 1 名の Cast が**指名**。**指名候補**は「当該店舗に在籍中の Cast」であり、この条件は候補の読み口と書き込み側の検証がひとつの述語として共有する — 候補に出さないだけでは、キャスト ID を直接送る要求を防げない。確定（Order の確定操作）ではさらに当日の確定シフトを要求する。
店舗側の候補の読み口は受注の配下（受注権限）に置く。指名は受注の操作であり、候補の範囲も要る権限も受注側が決めるため。キャスト管理の一覧は別物で、在籍停止も返し、キャスト管理権限を要求する。
_Avoid_: キャスト管理一覧を候補として流用すること、絞り込みを画面側だけで行うこと

**出勤希望（ShiftRequest）**:
キャストが所属店舗を指定して提出する勤務希望（店舗・日付・時間帯・備考）。状態は受付済み（提出直後）・確定済み（店舗が承認）・却下（店舗が辞退）の 3 つ。店舗の承認によって確定シフト（Shift = 排班の事実）が新規作成される——希望そのものはシフトへ変化せず、申請の履歴として残る。所属店舗の判定は「当該店舗に本人の cast 行が存在すること」で、cast の在籍状態は見ない（在籍停止の統制はアカウント層が担う）。これは出勤希望の所属判定に限った話であり、在籍状態を可否判定に使わないという通則ではない — 受注の指名可否（上記「指名 / 指名候補」）は在籍状態を見る。確定済みシフトに対する**変更申請**（#320 story 7、未実装）はさらに別の概念であり、本用語には含めない。
_Avoid_: 希望とシフト（Shift）の混用、変更申請との混用

**StoreProfile（店舗サイト設定）**:
店舗サイトのブランドと外観の設定（テンプレート、logo、banner、SNS リンク等）。
_Avoid_: TenantConfig（旧名。SystemConfig と紛らわしい）

### プラットフォーム設定

**SystemConfig（システム設定）**:
プラットフォームレベルのキーバリュー設定（SMTP、メンテナンスモード等）。SYSTEM_CONFIG_MANAGE 権限の保持者のみが管理できる。
_Avoid_: Config（裸使用。StoreProfile と混同しやすい）

**Menu（メニュー）**:
管理画面のナビゲーションメニューツリー。Central と Store の 2 スコープで同一概念を共有する。コード上は単一の Menu 集約（`t_menus`）へ統合済み（2026-07-18、#404 決定 2 / #409。CentralMenu / StoreMenu は統合前の歴史名）。
_Avoid_: TenantMenu（旧名）

## Open questions（未解決の論点）

- **Customer.rank / classification**：いずれも自由テキストで、等級／区分の正式な値体系は未定義（DB デフォルトは rank='SILVER'）。UI はテキスト入力で暫定対応し、業務側で値集合が確定次第 enum + ドロップダウンに収束させる。
