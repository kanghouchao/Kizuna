# Kizuna

単一グループの複数店舗を運営する CMS / CRM / HRM。Spring Boot の API と Next.js の画面を、Docker Compose と Task で構築・検証する。

## システムの入口

- プラットフォームコンソール: 店舗・アカウント・ロール・システム設定の管理。
- 店舗コンソール: 授権された店舗の顧客・注文・キャスト・シフト管理。
- キャスト／会員ポータル: 本人の出勤希望、予約申請、来店・ポイント情報。
- 公開店舗サイト: 店舗ドメインの `/`、`/casts`、`/schedule`、`/menu`、`/about`、`/reservation`。

ログインは `/platform/login` に統一し、サーバが返すコンソールと本人種別から遷移先を決める。ホスト名による公開サイトの選択と、API の権限・店舗分離は別の仕組みである。Traefik は `/api` を除去してバックエンドへ転送する。JWT の失効はセッション単位とアカウント単位で管理する。

## 開発環境の起動

前提: Git、Docker Compose / Buildx、[Task](https://taskfile.dev)。E2E にはホストの `jq` も必要。

```bash
git clone https://github.com/kanghouchao/Kizuna.git
cd Kizuna
cp infrastructure/.env.example infrastructure/development/.env
```

コピー先の環境設定を調整する。ローカルのプラットフォームホストは `APP_DOMAIN=kizuna.test` とし、必須変数はサンプルの説明に従う。実際の `.env` はコミットしない。

初回の `task up` より前に、開発用の管理者と demo スタッフのパスワードを自分で決め、bcrypt ハッシュを生成する。`htpasswd`（macOS 標準、Linux は Apache の utilities パッケージ）が必要。次のコマンドはパスワードを非表示で 2 回入力させる。平文を引数やシェル履歴に残す `-b` は使わない。

```bash
htpasswd -nBC 10 initial-admin
htpasswd -nBC 10 demo-staff
```

それぞれの出力の `initial-admin:` / `demo-staff:` より後ろをコピーし、無視対象の `infrastructure/development/.env` に `INITIAL_ADMIN_PASSWORD_HASH` / `DEMO_USER_PASSWORD_HASH` として設定する。Compose の変数展開でハッシュ内の `$` が変わらないよう、各値の全体をシングルクォートで囲む。サンプルにはこの 2 変数がないため自分で追加する。管理者は最初に選んだパスワード、demo スタッフ 2 名は二つ目に選んだパスワードでログインする。この手順は空の開発 DB への初回投入用であり、適用済み DB のパスワード変更には使わない。

`/etc/hosts` に次を追加する。

```text
127.0.0.1 kizuna.test store1.kizuna.test store2.kizuna.test
```

```bash
task build
task up
```

[プラットフォーム](http://kizuna.test/platform/login)と[公開店舗サイト](http://store1.kizuna.test)から利用できる。`task up` は再ビルドしないため、変更後は対象サービスを先にビルドする。

開発環境は `LIQUIBASE_CONTEXTS=demo` でサンプル店舗とスタッフを投入する。HQ の `admin@kizuna.test` は baseline、店長 `tanaka.hanako@kizuna.test` とスタッフ `yamada.jiro@kizuna.test` は demo データである。初期パスワードのハッシュは `INITIAL_ADMIN_PASSWORD_HASH` / `DEMO_USER_PASSWORD_HASH` の設定を参照する。初回適用後の変更は Liquibase のチェックサムに影響するため、パスワード更新はアプリから行う。

起動しない場合は `task ps` と `task logs service=backend` で確認する。`localhost:8080` はバックエンドではなく Traefik。baseline の適用済みチェックサムが変わった開発 DB は、[DB 再作成手順](backend/src/main/resources/db/AGENTS.md#after-editing-the-baseline-the-dev-db-must-be-recreated)に従う。Docker ボリュームは削除しない。

## 文書案内

| 目的 | 文書 |
| --- | --- |
| 開発・検証・PR | [貢献ガイド](CONTRIBUTING.md) |
| ドメインの用語と境界 | [CONTEXT.md](CONTEXT.md) |
| UI の設計 | [デザインシステム](frontend/DESIGN.md) |
| 設計の理由・履歴 | [ADR と資料索引](docs/README.md) |
| AI の作業規則 | [AGENTS.md](AGENTS.md) |
| 脆弱性の報告 | [セキュリティ方針](SECURITY.md) |

機能・不具合は [GitHub Issues](https://github.com/kanghouchao/Kizuna/issues)へ。脆弱性は公開 issue に記載しない。
