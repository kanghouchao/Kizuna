# StoreScopedEntity は storeFilter・storeSetFilter を静的に全量宣言する

Status: Accepted

## 決定時の背景

フィルタ宣言が欠けると静かに no-op となり、店舗集合外のデータを返し得る。JPQL を含む到達可能性解析や手動登録台帳では宣言対象を閉じられないため、実体の構造から機械的に検証できる規則を選ぶ。

## 決定

StoreScopedEntity を継承する全実体に storeFilter と storeSetFilter を静的宣言する。対象の固定リストは持たず、StoreIsolationTests が store_id 列を持つ全実体の継承・両宣言を検証する。主キー直接ロードへの適用も同テストで固定する。

宣言は有効化されるまで休眠し、集合照会の存在を意味しない。現行の集合照会例は PlatformOrderService.list と OrderRepository.findPlatformViews。Cast 本人は platform 級であり、この規則の対象は店舗在籍・公開プロフィール等の StoreScopedEntity 側である（ADR 0026）。

## 帰結

未使用の宣言には実行時フィルタのコストがなく、新しい集合照会で宣言漏れを起こさない。休眠の説明は StoreIsolationTests の Javadoc 一箇所に集約する。

フィルタは Session 内の対象実体へ適用されるため、照会・関連読み込みを変更するときは結果への影響を確認する。宣言の存在だけでは、プロキシ経由の有効化・transaction 順序・実際のクエリへの適用を保証しない。これらは backend/AGENTS.md の店舗分離規約に従う。

StoreScopedEntity 外の実体や DTO を読む新経路はこの走査だけでは保護できない。代替の所有者検証を設計し、分離テストで固定する。
