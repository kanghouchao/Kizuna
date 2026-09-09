# キャスト三層分離の API 契約案

状態: 承認済み（2026-09-08）。対象: #859。本人・在籍・公開プロフィールの裁定は #383、#850〜#856 に従う。

## 識別子と境界

店舗 API の `id`、受注・シフトの `cast_id`、招待の宛先は CastEnrollment の ID を表す。名前 `name` は CastProfile の源氏名で、必須。Cast の本人 ID・本名・生年月日・platform_user_id は店舗 CRUD と公開応答に追加しない。本人行は招待受諾時に作成または解決する。

## 端点

| メソッド・パス | 要求 | 成功応答 | 授権・ページング |
| --- | --- | --- | --- |
| GET /store/casts | 任意 search:string、既存 page/size/sort | 200、Page<CastSummaryResponse> | PERM_CAST_MANAGE。ページ番号と総件数を維持。ソートには id を副キーとして付加 |
| GET /store/casts/{id} | 在籍 ID:string | 200、CastResponse | PERM_CAST_MANAGE |
| POST /store/casts | 下記作成 DTO | 201、CastResponse | PERM_CAST_MANAGE |
| PUT /store/casts/{id} | 下記更新 DTO | 200、CastResponse | PERM_CAST_MANAGE。既存 CRUD の形を維持 |
| DELETE /store/casts/{id} | 在籍 ID:string | 204、本体なし | PERM_CAST_MANAGE。既存の参照保護を維持 |
| PATCH /store/casts/{id}/publication | publication_status:必須 enum(PUBLISHED, UNPUBLISHED) | 200、{publication_status:enum} | PERM_CAST_MANAGE。公開状態だけを変更する |
| GET /store/casts/public | 本体なし | 200、List<CastPublicResponse> | PermitAll。店舗文脈必須。店舗の公開キャストという有界集合 |
| POST /store/casts/{id}/invitation | 本体なし | 201、{token:string, expires_at:日時文字列} | PERM_CAST_INVITE |
| POST /platform/cast-invitations/view | token:必須 string | 200、既存招待詳細 DTO | PermitAll。既存の秘匿トークン用 POST を維持 |
| POST /platform/cast-invitations/acceptance | token/email/password/display_name:必須 string | 201、{store_name:string} | PermitAll |
| POST /platform/cast-invitations/acceptance/existing | token:必須 string | 200、{store_name:string} | ROLE_CAST |

店舗端点は既存の店舗コンテキスト・StoreScoped を維持する。認証失敗 401、権限不足・店舗文脈不足 403、対象なし・他店舗 ID 404、入力不正 400。削除時の業務参照競合と同店重複受諾は 409。同店重複受諾はトランザクション全体をロールバックし、招待は PENDING のまま残す。エラーは既存の `{error:string, details?:object}`。

## CRUD の項目

- 作成: `name` は必須の非空 string。`status` は省略可能な ENROLLED/SUSPENDED（省略時 ENROLLED）。`photo_url`・`introduction` は任意 string、`age`・`height`・`bust`・`waist`・`hip`・`display_order` は任意 integer。プロフィールは必ず UNPUBLISHED で作成する。
- 更新: 既存の省略時据え置き契約を維持し、作成と同じ編集項目を任意で受ける。指定した `name` は非空。`custom_fields` は任意の Map<string,string|null>、指定時は全置換・空 map は全消去。公開定義の値は profile、内部定義の値は enrollment に分配する。未知キー・500 文字超過は 400。
- 状態語彙は ENROLLED/SUSPENDED/WITHDRAWN の enum。旧 ACTIVE/INACTIVE は受け付けない。本票の既存状態編集は ENROLLED と SUSPENDED の切替に限定し、退店・再入店の操作と履歴記帳は後続票に委ねる。WITHDRAWN 行を CRUD の状態更新で復活させない。
- 詳細応答: `id`・`name`:string、`status`:在籍 enum、`publication_status`:公開 enum、`photo_url`・`introduction`:nullable string、`age`・`height`・`bust`・`waist`・`hip`:nullable integer、`display_order`:integer、`custom_fields`:map、`created_at`・`updated_at`:日時文字列。`invitation_status` は既存どおり一覧・詳細で返し、作成・更新では省略可能。日時は在籍行の監査項目を表す。
- 一覧応答: `id`・`name`・`status`・`publication_status`・`photo_url`・`age`・`bust`・`waist`・`hip`・`display_order`・`invitation_status`。型と nullable は詳細と同じ。紹介文・custom fields・監査日時を持たない。

## 公開応答

`CastPublicResponse` は `id:string`、`name:string`、`photo_url/introduction:nullable string`、`age/height/bust/waist/hip:nullable integer`、`display_order:integer`、`custom_fields:Array<{key:string,label:string,value:string}>` のみを持つ。`id` は既存の詳細リンクとの整合のため在籍 ID を使い、profile の enrollment_id から得る。

`status`、`publication_status`、`created_at`、`updated_at`、本人情報、内部 custom fields は DTO の型にも存在させない。問い合わせは profile を取得対象とし、在籍 ENROLLED を絞り込み述語だけに使う。露出条件は PUBLISHED ∧ ENROLLED。公開 custom fields のラベル等は公開定義から取得し、既存の表示順を維持する。

## カスタム項目定義

既存の GET/POST /store/casts/fields、PUT/DELETE /store/casts/fields/{id} を維持する。成功コードは順に 200/201/200/204。一覧は PERM_CAST_FIELD_DEF_VIEW、書込みは PERM_CAST_FIELD_DEF_MANAGE。一覧は店舗あたり最大 20 件の List。

作成は `key:string`・`label:string` 必須、`is_public:boolean` 任意（既定 false）。更新は `label:string`・`display_order:integer` 任意。公開区分は作成時固定とし、既存値と異なる `is_public` を指定した更新は 400。フロントの更新型と編集モーダルから is_public を除去する。定義の応答形は維持する。削除・同一キー再作成で過去の値が別区分へ露出しないよう、削除時に当該定義の値を除去する。

## 既存契約の追随

受注・シフト・実績・ポータルの端点、DTO の形、権限、ページングは維持し、参照先を在籍に切り替える。指名候補の読み書きは共通の ENROLLED 述語を使う。本人ポータルは本人の在籍 ID 集合で絞り込み、出勤希望の未退店判定（提出・承認）は #860 に委ね、本票は在籍 ID 参照への置換を行う。

## 検証境界と実施順

TDD の確認対象は次の公開境界とする。

1. 店舗キャスト HTTP API: 作成既定非公開、公開切替、PUBLISHED ∧ ENROLLED、公開応答の禁止項目不在、内部値の非露出と定義区分変更拒否。
2. 招待受諾 HTTP API: 同一アカウントの跨店受諾、同店重複 409、拒否後の招待が未消費であること。
3. 受注候補・本人ポータル HTTP API: 指名の読み書き一致、他人・他店舗のシフト非露出。
4. 既存のフロント画面テストと E2E: 状態語彙・公開切替・公開 fixture・custom field 編集の追随。

合意した境界ごとに検証して実装する。JDK 25 と Taskfile で最終 lint・型検査・全テスト（integration を含む）・build・E2E を実施し、実装開始時の HEAD `5f78c5b3e2f0307943b9091dcaf53bd1811aba50` を基準に code-review の二軸自審を行う。

現在のチェックアウトは detached HEAD のため、実装時に `codex/issue-859-cast-three-layers` を作成し、そのブランチにコミットする。DB baseline は終形宣言で更新し、検証には独立したテスト DB を使う。既存開発 DB の削除はこの作業には含めない。
