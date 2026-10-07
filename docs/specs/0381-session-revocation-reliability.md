# 資格情報の失効反映が失敗しても旧 JWT を受理しない

関連: #381。会員退会そのものの仕様・API・復帰方針は対象外。

## 問題と受け入れ条件

停止・パスワード変更などで `credential_version` の増分が DB にコミットされた後、Redis への反映が失敗すると、旧版のキャッシュが残る。JWT の版とキャッシュが一致するだけで許可すると、停止済みの本人が `/platform/me` を利用できる。

- 失効コミット後に開始した JWT 検証は、キャッシュ更新の成否にかかわらず旧版を受理しない。
- STAFF・CAST・MEMBER の共通認証で成立し、会員画面だけの状態検査にしない。
- 再開しても旧版は復活しない。正当な新しい版は受理できる。
- 失効を含む取引がロールバックした場合、現在の JWT を失効させない。
- Redis / DB の照合失敗を認証成功に変換しない。

## 判定と性能境界

署名検証の後、期限・issuer・token 単位 blacklist・資格情報の版の順に照合し、最初の拒否で後続の検証を止める。`DelegatingOAuth2TokenValidator` は `failOnError=true` とし、既に期限切れ・issuer 不一致・ログアウト済みと判定された token では資格情報の DB 照会を実行しない。

DB が正本、Redis は確定版の単調キャッシュとする。`claim < cache` は旧さが確定しているため DB を読まず拒否する。それ以外は既存の `findCredentialVersionByEmail` で DB のスカラー値を読み、claim と相等比較する。DB に主体がなければ拒否する。

キャッシュ miss・遅延時は既存 Lua で現在版を単調に反映する。一致時は Redis を書き直さない。反映・照合の例外は従来どおり伝播させ、障害中に通すフォールバックは追加しない。

**潜在的に有効な JWT の検証ごとに、email の一意インデックスを利用するスカラー SELECT が 1 本増える。** ユーザー実体・ロール・店舗集合はロードしない。古い JWT の高速拒否、token 単位の blacklist、コミット後の版通知、キャッシュの単調性は維持する。実際の負荷環境でのレイテンシーや最大処理量を測定したとの主張はしない。

既に検証を通った進行中の要求を取り消す保証ではない。停止が DB 読取りの後にコミットする競合もこの境界に含む。afterCommit が失敗した操作は DB が確定済みでも 500 になり得るという操作応答の契約は変更しない。

履歴の [ADR 0022](https://github.com/kanghouchao/Kizuna/blob/0b6e30e5/docs/adr/0022-session-invalidation-by-credential-version.md) は、定常 DB 0 回の代わりに反映失敗時の TTL までの延命を許容していた。**その性能・障害時の折衷は本仕様により superseded とする。** 資格情報の版・コミット後通知・単調キャッシュという元の決定は維持し、親タスクの承認に従い受理前の正本照合を必須にする。対応する「cache 一致時は DB を読まない」単体テストも、新しい安全性の条件に更新する。ADR ファイル自体は現行ツリーに存在しないため、削除済み文書を復活させず、この仕様を後継の正本とする。

## 検証

- 単体: 一致、miss、古い cache、新しい cache、DB からの主体消失、DB / Redis 例外。実署名・解読を通し、期限・issuer・blacklist の拒否後は資格情報照合に到達せず、有効な token は照合を省略しないことも固定する。
- 実 PostgreSQL / Redis: `CredentialOperations` と実トランザクション、実 afterCommit listener、実 Lua、標準 JwtDecoder、標準 Bearer filter、実 `/platform/me` controller / service を接続する。
- 専用 Redis の EVAL / EVALSHA を ACL で一時拒否し、反映が失敗しても DB は更新済み、cache は旧版という条件を確認する。ACL を回復してから旧 JWT の `/platform/me` が 401 となることを検証する。
- 合成 STAFF / CAST / MEMBER、停止・再開、パスワード変更、rollback、古い版の遅着通知を検証する。token・口令は出力しない。

統合テストは `KIZUNA_AUTH_TEST_JDBC_URL` と `KIZUNA_AUTH_TEST_REDIS_PORT` を設定して `./gradlew test --tests '*CredentialRevocationPostgresTest'` で実行する。専用の使い捨て PostgreSQL（postgres、trust 接続）と loopback 上の専用 Redis のみを使う。テストは実行ごとのスキーマを作成し、Redis の default ユーザーの ACL を一時変更して finally で復旧するため、既存環境を接続先にしない。通常の Docker 単体ゲートでは環境変数未指定によりこの外部サービス付きテストを実行しない。

API、スキーマ、権限付与、会員退会、注文の状態やポイント台帳は変更しない。最終検証は Docker Taskfile による lint / test / build / E2E と上記の実サービス回帰を併用する。
