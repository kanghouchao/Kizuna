# infrastructure 運用手順

構成の規約は [AGENTS.md](AGENTS.md)、環境ごとの compose は `development/` と `release/` にある。`.env` は [`.env.example`](.env.example) をもとに各環境ディレクトリへ置き、コミットしない。

## 応募者添付の非公開ストレージ

決定の経緯は [ADR 0033](../docs/adr/0033-applicant-attachments-use-a-separate-storage-instance-without-backup.md)。

### 構成の要点

- 応募者添付は公開の `storage` とは別の SeaweedFS 実例 `private-storage` に保存する。独立したボリューム `private-seaweedfs-data` を持ち、Traefik のルートもホストポートも Admin UI も持たない。backend とだけ共有する専用ネットワーク `private-network`（外部への出口なし）に置き、backend が `http://private-storage:8333` で使う。SeaweedFS の `mini` は S3 以外に認証の無い filer（8888）や master（9333）も開くので、他のサービスをこのネットワークに参加させてはならない。
- バケット `applicant-images` は compose が `-bucket=applicant-images` で宣言する。backend はバケットを作らず、起動時に存在と policy が無いことを確かめるだけである。
- 資格情報は `.env` の `PRIVATE_S3_ACCESS_KEY` / `PRIVATE_S3_SECRET_KEY` で、同じ値が `private-storage` と backend の両方に渡る。公開側の `S3_ACCESS_KEY` / `S3_SECRET_KEY` とは必ず別の値にする。endpoint・バケット名・アクセスキー・シークレットのどれかが公開側と同じだと backend は隔離が無いとみなす。
- release は二つの資格情報が未設定なら compose が起動を拒否する（添付機能を無効にしていても同じ）。既存の本番 `.env` にも追記が必要である。
- 有効化フラグ `APP_PRIVATE_ATTACHMENTS_ENABLED` は release では既定で無効、development では常に有効。有効なときに隔離・バケットの存在・policy の不在のどれかが満たされなければ backend は起動に失敗する。
- backend は添付の一時ファイルを tmpfs の `/tmp/kizuna-private-attachments`（0700、所有者は実行ユーザー 65532）に書く。コンテナの書き込み層やボリュームには残らないが、ホストで swap が有効だとメモリ上の内容が swap 領域へ書き出されうる。

### 資格情報のローテーション

漏洩の疑いがあるとき、または担当者が交代したときに行う。定期的なローテーションはしない。

1. release の `.env` で `PRIVATE_S3_ACCESS_KEY` と `PRIVATE_S3_SECRET_KEY` を新しい値に書き換える（公開側とは別の値にする）。
2. `infrastructure/release` で `private-storage` と backend を同時に作り直す。`docker compose restart`（`task restart`）は古い環境変数のまま再起動するので使わない。

   ```bash
   docker compose -p kizuna up -d --wait private-storage backend
   ```

3. backend も作り直すため、healthy になるまでの数十秒は添付に限らず API 全体が応答しない。利用の少ない時間帯に行う。
4. 画面で添付を一つ開き、閲覧できることを確かめる。

### バックアップしない理由

`private-seaweedfs-data` は意図してバックアップの対象から外す。バックアップ対象を足すときもこのボリュームは含めない。

- 保持期間を過ぎた応募者は匿名化され、添付の実体も削除される。バックアップがあると削除済みの添付が世代の満了まで残り、保持期間を越えて個人情報を持ち続けることになる。
- 添付は再符号化した副本で、証拠保全の原本ではない。失っても応募者の行と選考記録は DB に残る。
- その代価として、ディスク障害が起きれば非公開添付は失われる。復旧の手段は無い。

### 本番での有効化の確認

release の `.env` に `APP_PRIVATE_ATTACHMENTS_ENABLED=true` と二つの資格情報を設定し、`task up env=release` で起動したうえで次を順に確かめる。`<ドメイン>` は `APP_DOMAIN`、`<ホスト>` はサーバーの IP アドレスまたはホスト名。

1. **起動ログ**：backend が起動に成功し healthy になること。失敗した場合はログに満たされなかった条件（隔離・バケットの存在・policy の不在）が出るので、`.env` と compose を直して起動し直す。

   ```bash
   task -d infrastructure/release logs service=backend
   ```

2. **公開ドメインから届かないこと**：`/static` は公開の `storage` へ転送され、そこに `applicant-images` は無い。200 が返らず、画像の中身が返らないことを確かめる。

   ```bash
   curl -sS -o /dev/null -w '%{http_code}\n' https://<ドメイン>/static/applicant-images/
   ```

3. **ホストへの直接アクセスが拒否されること**：`private-storage` はホストポートを持たないので接続そのものが拒否される。

   ```bash
   curl -sS -m 5 http://<ホスト>:8333/
   ```

   ```bash
   docker port private-storage
   ```

   前者は接続失敗、後者は何も表示しないことを確かめる。

   他のサービスからも届かないこと（`private-storage` が backend 以外とネットワークを共有していないこと）を、公開の `storage` コンテナから確かめる。どれも名前解決に失敗し、HTTP の応答が返らない。

   ```bash
   docker exec storage curl -sS -m 5 http://private-storage:8888/
   ```

   ```bash
   docker exec storage curl -sS -m 5 http://private-storage:8333/
   ```

4. **画面での追加・閲覧**：応募者詳細の「非公開の添付画像」で画像を一つ追加し、一覧に出ること、ダウンロードして開けることを確かめる。画面に添付を削除する操作は無く、添付の実体は保持期間を過ぎた応募者の匿名化で削除される。
5. **保持期間の設定**：システム設定（分類 RECRUITMENT）で `applicant_retention_rejected_months`（不採用）・`applicant_retention_withdrawn_months`（辞退）・`applicant_retention_hired_months`（採用）の三つを設定する。未設定のままだと匿名化は何もせず、添付は無期限に残る。応募者詳細の「情報保持方針」に三つの期間が表示されることを確かめる。なお匿名化の定期実行は `app.applicant-anonymization.enabled` と実行主体のサービス ID で別に有効化するもので、現在の compose はこれらを backend に渡していない。
