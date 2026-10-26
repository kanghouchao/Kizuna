# Kizuna

単一グループの複数店舗を運営する CMS / CRM / HRM。Spring Boot の API と Next.js の画面を、Docker Compose と Task で構築・検証する。

## システムの入口

- 管理画面: 店舗・従業員・ロール・システム設定の管理と、現在店舗の顧客・注文・キャスト・シフト管理を一つの画面で行う。見える機能は権限、扱う店舗は本人の店舗集合と現在店舗で決まる。
- キャスト／会員ポータル: 本人の出勤希望・サービス条件の受諾・報酬明細、会員の予約申請・来店・ポイント情報。
- 公開店舗サイト: 店舗ドメインの `/`、`/casts`、`/schedule`、`/menu`、`/about`、`/reservation`。

ログインは `/auth/login` に統一し、本人種別（従業員・キャスト・会員）から遷移先を決める。ホスト名による公開サイトの選択と、API の権限・店舗分離は別の仕組みである。Traefik は `/api` を除去してバックエンドへ転送する。JWT の失効はセッション単位とアカウント単位で管理する。LINE ログインも同じ入口から利用でき、チャネル資格情報の設定時に有効になる。

## 開発環境の起動

前提: Git、Docker Compose / Buildx、[Task](https://taskfile.dev)。E2E にはホストの `jq` も必要。

```bash
git clone https://github.com/kanghouchao/Kizuna.git
cd Kizuna
cp infrastructure/.env.example infrastructure/development/.env
```

コピー先の環境設定を調整する。ローカルのアプリホスト（管理画面・ポータルのドメイン）は `APP_DOMAIN=kizuna.test` とし、`APP_JWT_SECRET` は自分で生成したランダム値へ置き換える。実装は文字列の UTF-8 バイト列をそのまま HS256 の鍵として使い、32 バイト未満なら起動を拒否する。DB・Redis・SeaweedFS の資格情報も設定し、実際の `.env` はコミットしない。

オブジェクトストレージは SeaweedFS 4.50 の単一ノード構成を使用する。`S3_ENDPOINT=http://storage:8333`、`S3_BUCKET=uploads`、`S3_ACCESS_KEY`、`S3_SECRET_KEY` を設定する。画像は gateway の `/static/uploads/` から公開する。

開発環境の管理画面は [http://localhost:23646](http://localhost:23646) で開ける（ホストのループバックのみ公開）。リリース・E2E では管理画面を無効にする。既存データで管理画面が 404 になる場合は、`/data/mini.options` の `admin.ui` が `false` のまま保存されていないか確認し、バックアップ後に `true` へ変更して storage を再作成する。

永続データは専用の `seaweedfs-data` ボリュームに保存するため、切り替え後は空のストレージで起動する。旧ストレージのボリュームは自動削除・変換しない。

`/etc/hosts` に次を追加する。

```text
127.0.0.1 kizuna.test store1.kizuna.test store2.kizuna.test
```

```bash
task build
task up
```

[管理画面](http://kizuna.test/auth/login)と[公開店舗サイト](http://store1.kizuna.test)から利用できる。`task up` は再ビルドしないため、変更後は対象サービスを先にビルドする。

初回の `task up` の後、[初期設定画面](http://kizuna.test/auth/initial-setup)を開いて最初の管理者を登録する。入口の URL はバックエンドの起動ログ（`task -d infrastructure/development logs service=backend`）にも表示される。最初の管理者は統括ロールと全店舗の授権を持つ。baseline に店舗は含まれないため、管理画面の店舗一覧から店舗を登録し（公開店舗サイトで開くには `/etc/hosts` に載せたドメインを使う）、以降の従業員は従業員管理から作成する。登録したデータはデータボリュームを削除しない限り残る。

起動しない場合は `task -d infrastructure/development ps` と `task -d infrastructure/development logs service=backend` で確認する。`localhost:8080` はバックエンドではなく Traefik。baseline の適用済みチェックサムが変わった開発 DB は、[DB 再作成手順](backend/src/main/resources/db/AGENTS.md#after-editing-the-baseline-the-dev-db-must-be-recreated)に従う。作り直した DB にはアカウントも店舗も無いため、初期設定から管理者を登録し、店舗を登録し直す。Docker ボリュームは削除しない。

## ポイント失効の運用

期限切れポイントの記帳は既定で停止している。明示的に `app.points-expiry.enabled=true` と
`app.points-expiry.service-user-id` を設定した環境だけで、アプリケーションの暦日・時区に従い
日次に実行する。手動の新規実行・再試行には運用者の `TASK_MANAGE`・`POINT_EXPIRE` と `ALL_STORES` が必要となる。
サービス ID には `TASK_EXECUTE`・`POINT_EXPIRE` と `ALL_STORES` を明示授与する。
既存の人・サービスへの自動授与はない。

`app.points-expiry.cron` の既定は `0 5 0 * * *`、`app.points-expiry.max-lots` は 10,000。
上限・共通実行時間制限・監査の失敗では仕訳全体を取り消し、処理の実行履歴に失敗を残す。
運用者は原因を直して失敗試行を再試行する。必要な上限変更は DB 負荷と実行時間を確認して行う。
補実行は処理画面で「期限切れポイントの記帳」と本日以前の一日を選ぶ。
その日より前に期限を過ぎた未消費ロットをまとめて拾うので、停止期間の日数分を個別に実行する必要はない。
成功後に利用取消で返った期限切れ量は、後続の実行で記帳する。

記帳は会員ごとに一仕訳で、利用可能残高は変わらない。源ロットの期限と仕訳の日時に加え、
監査の `POINT_EXPIRED` から仕訳 ID・実行試行 ID・サービス ID を辿れる。
授権は実行開始と確定前の検証点ごとに、別の短い読み取り専用取引で確定済みの状態を読み直す。
最後の検証後に確定した権限の取り消しは、次の検証点から有効になる。

## 文書案内

| 目的 | 文書 |
| --- | --- |
| 開発・検証・PR | [貢献ガイド](CONTRIBUTING.md) |
| ドメインの用語と境界 | [GLOSSARY.md](GLOSSARY.md) |
| UI の設計 | [デザインシステム](frontend/DESIGN.md) |
| 設計の履歴 | Git 履歴と [ADR](docs/adr/) |
| AI の作業規則 | [AGENTS.md](AGENTS.md) |
| 脆弱性の報告 | [セキュリティ方針](SECURITY.md) |

機能・不具合は [GitHub Issues](https://github.com/kanghouchao/Kizuna/issues)へ。脆弱性は公開 issue に記載しない。
