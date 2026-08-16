# API 設計規約

## 1. 目的と適用範囲

本書はバックエンドが公開する全 HTTP API の設計規約であり、規約の正本である。

- **新規端点は本書に従う。** 既存端点への追加・変更も、触れた範囲は本書に合わせる。
- **既存の乖離は一括では直さない。** 是正はバッチで進める。ただし 9 章に挙げた項目は是正対象ですらない（模倣も修正もしない）。
- 迷ったときの優先順は「安全側 > 既存の形状との一貫性 > 教科書的な RESTfulness」である。

## 2. リソース命名

- パスは**複数形の名詞** + **kebab-case**（`/platform/cast-invitations`、`/store/shift-requests`）。
  例外は**親に対して一つしか存在し得ない子リソース**で、これは単数形でよい（`POST /store/orders/{id}/receipt-token`）。
  集合を指すパスは必ず複数形。
- ドメイン動作は**名詞化した子リソース**への `POST` で表す。これが正であり、「純 REST 化」を理由に崩してはならない。

  ```
  POST /store/orders/{id}/confirmation
  POST /store/orders/{id}/completion
  POST /store/orders/{id}/cancellation
  POST /store/shift-requests/{id}/approval
  POST /store/customers/{customerId}/merges
  POST /store/orders/{id}/attribution/correction
  ```

  不可逆な操作を「専用の操作」として切り出すのは
  [ADR 0013](adr/0013-terminal-orders-are-frozen-and-never-deleted.md) が定める作法である
  （専用 `POST` + 理由必須、二度目は静默冪等に委ねず明示的に撥ねる）。
  [ADR 0012](adr/0012-attribution-point-correction-addresses-the-attribution-record.md) の「訂正の宛先は帰属記録」、
  [ADR 0010](adr/0010-customer-merge-is-repoint-and-tombstone.md) の顧客統合、
  [ADR 0011](adr/0011-receipt-token-reissue-revokes-the-previous-one.md) の伝票トークン再発行も、
  この形で公開している。遷移ごとに端点が分かれることで、権限・冪等性・不正遷移の 400 を遷移単位で表現できる。
- **裸の動詞は禁止**。動作は名詞化するか、対象資源の生成として表す。
  この形は既に本仓に存在する（`POST /store/orders/{id}/decline`、`POST /store/shift-requests/{id}/decline`、
  `POST /files/upload`、`POST /platform/me/receipts/claim`）が、**これらは是正バッチの対象であって 9 章の保護対象ではない**。
  現状として模倣してよい先例と読まないこと。
  唯一の例外は 3 章の「秘匿トークンを受ける読み取り」（`/view` 型）で、これは命名ではなく安全上の理由による偏離である。
- 字面セグメント（`/public`、`/lookup`、`/work-queue`、`/archive` 等）と `{id}` テンプレートの共存は許容する。
  ただしこれらは **`{id}` の値空間を恒久的に侵食する**（同名の id が来ても字面側が勝つ）。
  id が数値であるうちは衝突しないが、将来 id が文字列化しうる資源では字面セグメントを避けるか、
  衝突しえない語を選ぶこと。
- 「自分自身」の読み口は `/platform/me/...` に集約する。認証主体から導ける情報を path に再掲しない。

## 3. HTTP メソッド意味論

| メソッド | 用途 |
| --- | --- |
| `GET` | 副作用のない読み取り。サーバ状態を変えない |
| `POST` | 生成、およびドメイン動作（名詞化子リソース） |
| `PUT` | **単一リソース URI** に対する全量置換 |
| `PATCH` | 部分更新 |
| `DELETE` | 削除 |

- `PUT` の宛先は必ず単一リソースの URI（`/platform/roles/{id}`）である。集合ルート（`/platform/xxxs`）への `PUT` で
  その中の 1 件を更新してはならない。宛先が曖昧な更新は lost-update と権限判定の穴を生む。
- **安全上の例外**: 秘匿トークンを入力に取る読み取りは、`GET` ではなく **`POST` + リクエストボディ**とする。
  URL のパス・クエリ文字列に載せたトークンはアクセスログ・Referer ヘッダ・ブラウザ履歴へ漏れる。
  `POST /platform/cast-invitations/view` がこの形であり、「読み取りだから `GET` へ」と是正してはならない。
  この例外を使うときは CSRF 免除の配線も同時に要る（7 章）。

## 4. ステータスコード

- **生成 = 201**。ボディには生成された資源の id を含める。`Location` ヘッダは要求しない（フロントが消費しないため）。
- **削除 = 204**（ボディ無し）。
- **ドメイン動作 = 200** + 動作結果の表現。動作が新たな資源を生む場合（トークン発行等）は 201。
- **401 と 403 を混同しない。** 認証の失敗は 401、認証済みで権限が足りない拒否は 403。
  401 の出所は二つあり、Bearer 検証の失敗は `PlatformAuthenticationEntryPoint`、ログイン時の認証失敗（資格情報誤り・アカウント無効）は
  `CommonExceptionHandler` から返る。403 は `CommonExceptionHandler` が返す。テストの断言でも両者を潰さない。
- **入力検証エラー・不正な状態遷移 = 400**。ドメイン例外（`Order.confirm()` 等が投げる遷移違反）はここに落ちる。
- **404 は「存在しない」を表す。** 作用域の外にある資源が見えない結果としての 404 は正しい。
  一方、404 で列挙耐性を稼ぐ判断を端点ごとに単独で下さない（同じ述語を共有する同族端点と挙動が割れる）。

### エラーボディの形

失敗応答の本体は `CommonExceptionHandler` が組み立てる次の形で統一する。個別のハンドラで別形を作らない。

```json
{ "error": "利用者向けの固定文言", "details": { "start_at": "必須です" } }
```

- `error` は**型ごとに固定した利用者向け文言**。フレームワークや DB の生 message は Java の型名や内部構造を含むため転送しない。
- `details` は任意で、キーは**要求で送るときと同じ綴り（snake_case）**に揃える。
  束縛結果が持つのは camelCase のプロパティパスなので、ここで写像してから載せる。
- 例外の internal message をそのまま `error` に流すのはワイヤ契約への漏出であり、禁止。

## 5. ページネーションの選択基準

3 形態が併存する。用途で選ぶのであって、書きやすさで選ばない。

- **`Page`（Spring Data）**: 管理画面の一覧で、ページ番号 UI と総件数表示がある読み口。
  offset ページングは**全順序**を要求する。並び替えキーに一意な副キー（`id` 等）を必ず添える。
  副キーが無いと、更新のたびに行が別ページへ滑って重複・欠落が起きる。
- **`CursorPage`（`shared/web/CursorPage.java`）**: 作業キュー、無限スクロール、件数を必要としない履歴。
  総件数を返さない代わりに毎回の count 問い合わせを撒かない。取得件数は `CursorPage.MAX_SIZE` で頭打ちにする。
- **裸の `List`**: 「有界であることを説明できる小集合」に限る。権限カタログ、ロール一覧、公開中のキャスト等、
  上限が業務上明らかなもの。**無界に増える履歴系を裸の `List` で返すのは違反**であり、`CursorPage` を使う。

新しい読み口を足すときは、その集合が何によって有界なのかを一文で説明できること。説明できないなら有界ではない。

## 6. 一覧 / 詳細 DTO の分離

- 一覧は **Summary DTO**、詳細は **Detail DTO** に分ける。範式は `RoleSummaryResponse`（一覧）と `RoleResponse`（詳細）である。
  一覧に必要のない重い関連（権限コードの全列挙等）を一覧の型から外すことで、必要になった項目だけを詳細で取りに行ける。
- **秘匿値（トークンの生値等）と、一覧に不要な機微 PII は、一覧 DTO の型に存在させない。**
  「型には残したまま実行時に抑制する」形（MapStruct の `@Mapping(ignore = true)`、null 代入、シリアライズ時の除外）は
  抑制漏れが 1 箇所でも起きれば漏出になる。抑制ではなく**型から消す**。
  `OrderResponse.receiptToken` は一つのレスポンスにだけ現れる生値を共有 DTO に持たせている反例であり、新規に模倣しない。
- レスポンス DTO の JSON キーは snake_case（Jackson が写像）。ただし**利用者定義キーを持つ Map**（カスタム項目等）は
  キーが「名前」ではなく「データ」なので、命名戦略の対象にしてはならない。

## 7. ゼロトラスト授権

- `SecurityConfig` の HTTP 授権は `anyRequest().permitAll()` であり、**授権は方法級の注釈だけが担う**。
  したがって **全 handler は `@PreAuthorize` か `@PermitAll` を明示する**。書き忘れは 403 ではなく
  「誰でも叩ける公開端点」を静默に生む。
- この不変量は `EndpointAuthorizationDeclarationTests`（`backend/src/test/java/com/kizuna/`）が機械強制する。
  豁免リストは持たない。
- **`@PermitAll` を新設するときは、公開端点の四点セットを同時に配線する**:
  1. handler に `@PermitAll`
  2. **CSRF 免除**: 状態を変える（`GET` 以外の）匿名端点は `SecurityConfig` の `CSRF_IGNORED_MATCHERS` へ追加する。
     既存の一括免除は「Bearer 付きリクエスト」が条件なので、匿名 POST はそこに当たらず個別列挙が要る。
     エントリは `PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/...")` の形で
     **メソッド + パス**を指定する。パスだけで書くと、同一パス上の認証必須な別メソッドの handler まで CSRF 保護を失う。
  3. **Bearer 免除**: `PlatformBearerTokenResolver` の `BEARER_EXEMPT_MATCHERS` へ追加する。
     陳腐化した token cookie を持つ利用者が、`@PermitAll` の判定に届く前に 401 で弾かれるのを防ぐ。
     こちらも **メソッド + パス**で指定する。パスだけで書くと、同一パス上の認証必須な別メソッドの handler でも Bearer が
     捨てられ、その handler の `@PreAuthorize` は常に匿名を見ることになる — その端点は誰にも通せない恒久的な 401/403 になる。
  4. **店舗文脈**: `/store/**` と `/files/**` は `StoreIdInterceptor` の対象で、店舗文脈ヘッダ（`X-Role` / `X-Store-ID`）が
     無いリクエストを fail-closed で 403 にする。匿名でも店舗文脈は要る。文脈無しで通す端点だけが `@StoreOptional` を明示する。

  3 点目の欠落は正常系では緑のままで、「壊れた Bearer 付きの匿名リクエストが 401」という形でだけ露出する。
- 店舗側の読み口は **`@StoreScoped`（`shared/storescope/StoreScoped.java`）による行レベル分離**が前提である。
  自前の `where store_id = ?` で代替しない。この機構を迂回する読み口を作る場合は、
  代替統制（属主の明示検証等）とその根拠をコード上に残す。
- 権限 authority の字面は `PermissionCode` に写像できるものだけを書く（`PermissionLiteralTests` が強制）。

## 8. API-first の進め方

前後端にまたがるタスクでは、**実装に入る前に API 契約を提示して裁定を得る**。提示する項目は以下:

- 端点（パス）とメソッド
- ステータスコード（成功時・主要な失敗時）
- リクエスト / レスポンスの項目（型と省略可否）
- 授権（`@PreAuthorize` の権限、または `@PermitAll` とその根拠）
- ページネーション形態（5 章のどれか、およびその理由）

契約が決まってから実装する。実装後に契約を事後説明する順序は取らない。

## 9. 既知の乖離（模倣も修正もしない）

以下は現状そうなっているが、**追随して新規に増やしてはならず、同時に「ついでの修正」もしない**。
是正には呼出側の改修が伴い、単独の変更としては割に合わないためである。

- `/store/config`（単数）と `/platform/configs`（複数）の不揃い。
- `/platform/staff`（不可算名詞のため複数形化されていない）。
- **DTO 名の収斂規則**: 新規は要求本体が `XxxRequest`、応答本体が `XxxResponse`。接尾辞は例外なく付ける。
  既知の乖離は以下で、いずれも**模倣も修正もしない**:
  - 応答側の `*VO` 系: `StoreVO` / `StoreStatusVO` / `MenuVO`
  - 応答側で接尾辞を持たない `Token`（`auth/api/dto/Token.java`、ログインと LINE 登録確定の応答）
  - 要求側の `*DTO` 系: `StoreCreateDTO` / `StoreUpdateDTO`（`store/api/dto/`。他の要求本体は全て `XxxRequest`）

これらのパスや型名に触れる作業をしていても、名前だけを直す変更は差分に混ぜないこと。
