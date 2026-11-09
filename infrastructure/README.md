# infrastructure 運用手順

構成の規約は [AGENTS.md](AGENTS.md)、環境ごとの compose は `development/` と `release/` にある。`.env` は [`.env.example`](.env.example) をもとに各環境ディレクトリへ置き、コミットしない。

## backend / frontend のレプリカ数

`.env` の `BACKEND_REPLICAS` / `FRONTEND_REPLICAS`（既定はどちらも 1）で台数を決める。compose の `deploy.replicas` と gateway の
転送先の両方がこの値を読むので、変えるのはこの二つだけでよい。レプリカのコンテナ名は `kizuna-backend-1`、`kizuna-backend-2` … になる。

```bash
docker compose -p kizuna up -d --wait
```

`.env` を書き換えたあと上を環境ディレクトリで実行すると、増減したサービスと gateway が作り直される。

- gateway（Traefik）はレプリカごとにヘルスチェックし（backend は `/actuator/health/readiness`、frontend は `/health`、5 秒間隔）、
  落ちた・準備のできていないレプリカへは送らない。停止の直後にまだ外れていないレプリカへの接続失敗は、別のレプリカへ送り直す
  （GET などの冪等な要求だけ。POST は送り直さないので、その瞬間の POST は 502 になりうる）。
- 振り分けの状況は Traefik の API で見る（development はホストの 8080）。

  ```bash
  curl -s http://localhost:8080/api/http/services/backend@file
  ```

- compose にはローリング更新が無い。イメージや環境変数を替えて `up -d` するとそのサービスの全レプリカを同時に作り直すので、
  backend が healthy に戻るまでの数十秒は応答が止まる。
- backend の DB 接続は一台あたり最大 `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`（既定 10）本で、台数分だけ増える。
  `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE × BACKEND_REPLICAS` が PostgreSQL の `max_connections`（既定 100）から管理用の余裕を引いた値に収まるようにする。
- メモリはレプリカ一台ごとに `BACKEND_MEMORY_LIMIT`（既定 1536m）/ `FRONTEND_MEMORY_LIMIT`（既定 512m）で上限を付ける。backend のヒープは
  上限の 16%〜33%（既定で約 246〜508MiB）、frontend の V8 ヒープも上限から自動で決まるので、ヒープだけを別に指定しない。台数 × 上限がホストのメモリに収まるようにする。
  既定値の根拠（e2e でヒープを最大まで確保して実測）：backend の非解放メモリの最大は 971MiB で、うち JVM が 937MiB（ヒープ 512・メタスペース 165・コード 90・GC 52 ほか）。
  これに添付の一時ファイル（tmpfs、最大 128MiB）と 5 秒ごとのヘルスチェックの JVM を足した約 1.1GiB に、約 25% の余裕を見た。
  上限を超えるとカーネルが強制終了し、`restart: on-failure:3` で 3 回続くとそのレプリカは止まったままになるので、1536m 未満にしない。frontend は約 5,000 件の描画で最大 201MiB。
  CPU には上限を付けない（JVM は見える CPU 数でスレッド数や GC を決めるため、1 CPU 前後に絞ると性質が変わる）。
- JVM オプションの追加・上書きは `.env` の `JDK_JAVA_OPTIONS` で行う。同じコンテナで動くヘルスチェックの `java` もこれを読むので、
  ポートを開くもの（JMX・リモートデバッグ）や `-javaagent` を入れるとヘルスチェックの JVM が起動に失敗し、レプリカが unhealthy になる。
- 起動時の Liquibase は DB 上のロックで直列になる。後から起動したレプリカはロックが外れるまで待つ。

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
5. **保持期間の設定**：システム設定（分類 RECRUITMENT）で `applicant_retention_rejected_months`（不採用）・`applicant_retention_withdrawn_months`（辞退）・`applicant_retention_hired_months`（採用）の三つを設定する。未設定のままだと匿名化は何もせず、添付は無期限に残る。応募者詳細の「情報保持方針」に三つの期間が表示されることを確かめる。保持期間を過ぎた応募者を実際に匿名化するには、続けて[定期処理の有効化](#定期処理の有効化)で応募者の匿名化を有効にする。

## 定期処理の有効化

応募者の匿名化（処理名 `APPLICANT_ANONYMIZATION`、毎日 0:15）、ポイント失効（処理名 `POINT_EXPIRY`、毎日 0:05）、ポイント期限前案内の起票（処理名 `POINT_EXPIRY_NOTICE`、毎日 9:00）、業務通知の送信（処理名 `NOTIFICATION_DELIVER`、毎分）は、どれも既定で無効である。時刻は `APP_TIMEZONE` の業務タイムゾーンで決まる。実行主体は対話ログインできないサービス ID（[ADR 0025](../docs/adr/0025-service-identity-lives-in-t-users-without-credentials.md)）で、稼働中のシステムで作る行なので、id は compose ではなく `.env` に書く。

| 処理 | 有効化フラグ | サービス ID | 処理固有の権限 |
|---|---|---|---|
| 応募者の匿名化 | `APP_APPLICANT_ANONYMIZATION_ENABLED` | `APP_APPLICANT_ANONYMIZATION_SERVICE_USER_ID` | `APPLICANT_ANONYMIZE` |
| ポイント失効 | `APP_POINTS_EXPIRY_ENABLED` | `APP_POINTS_EXPIRY_SERVICE_USER_ID` | `POINT_EXPIRE` |
| ポイント期限前案内の起票 | `APP_POINTS_EXPIRY_NOTICE_ENABLED` | `APP_POINTS_EXPIRY_NOTICE_SERVICE_USER_ID` | `POINT_EXPIRE` |
| 業務通知の送信 | `APP_NOTIFICATION_DELIVERY_ENABLED` | `APP_NOTIFICATION_DELIVERY_SERVICE_USER_ID` | `NOTIFICATION_DELIVER` |

起動時刻は、ポイント期限前案内の起票を `APP_POINTS_EXPIRY_NOTICE_CRON`、業務通知の送信を `APP_NOTIFICATION_DELIVERY_CRON`（どちらも秒から始まる六項目の cron）で変えられる。書かなければ上の既定の時刻で動く。

有効にしたのにサービス ID が空、または 0 以下だと backend は起動に失敗する。サービス ID の授権が足りない場合は起動には成功し、実行の時点で拒否される。

1. **ロールを作る**：ロール管理で、`TASK_EXECUTE` と処理固有の権限を持つロールを作る。システムロールはサービス ID に授けられないので、新しいロールにする。複数の処理を一つのサービス ID で動かすなら、一つのロールに `TASK_EXECUTE` とそれぞれの処理固有の権限を持たせてよい。期限前案内は送信の処理が流して初めて会員に届くので、期限前案内を有効にするなら業務通知の送信も有効にする。
2. **サービス ID を作る**：サービスID管理で、1 のロールを授け、店舗集合を「全店舗」（`ALL_STORES`）にして作る。どの処理もグループ全体が対象で、個別店舗の授権では実行を拒否される。作成後の詳細に出る id を控える。
3. **`.env` に書いて有効化する**：環境ディレクトリの `.env` に次を書き、その環境ディレクトリで backend を作り直す。`task restart` は古い環境変数のまま再起動するので使わない。

   ```dotenv
   APP_APPLICANT_ANONYMIZATION_ENABLED=true
   APP_APPLICANT_ANONYMIZATION_SERVICE_USER_ID=<2 の id>
   ```

   ```bash
   docker compose -p kizuna up -d --wait backend
   ```

   ほかの処理も同じく、表の有効化フラグとサービス ID を書く。

4. **手動で実行して記録を確かめる**：処理の実行履歴で、処理を選び、実行主体に 2 のサービス ID、対象日に本日を指定して実行する。操作する担当者のアカウントには `TASK_MANAGE` に加えて処理固有の権限（`APPLICANT_ANONYMIZE` / `POINT_EXPIRE` / `NOTIFICATION_DELIVER`）と全店舗の授権が要る。`APPLICANT_ANONYMIZE` は既定のロールのどれにも入っていないので、担当者のロールに足しておく（`POINT_EXPIRE` と `NOTIFICATION_DELIVER` は既定ロール「統括」に入っている）。
   成功の記録が一覧に残り、処理件数が出ることを確かめる。
5. **定期実行を確かめる**：日次の処理は翌日、業務通知の送信は数分後に、処理の実行履歴に記録が増えていることを確かめる。定期実行の記録は、日次の処理なら実行キーが日付そのもの（例 `2026-10-31`）、業務通知の送信なら起動した分の UTC 時刻（例 `2026-10-31T00:05:00Z`）で、画面からの手動実行は無作為な実行キーになるので見分けられる。記録が無ければ、サービス ID の授権が足りないなどで実行前に拒否されているので、backend のログを見る。

   ```bash
   task -d infrastructure/<環境> logs service=backend
   ```

   `<環境>` は `release` または `development`。

止めるときは有効化フラグを `false` に戻して backend を作り直す。サービス ID はサービスID管理で停止すると実行を拒否されるが、行は残り、過去の実行記録から辿れる。
