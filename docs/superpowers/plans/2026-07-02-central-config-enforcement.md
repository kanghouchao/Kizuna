# Central 設定の実効化 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/central/settings` で編集できるだけで実際には何も効いていないシステム設定（メンテナンスモード・SMTP）を、バックエンドで実際に機能させる（issue #170 の Central 設定部分）。

**Architecture:** 既存の `central_configs` キーバリューストアに `value_type` / `secret` 列を追加して型検証と秘匿マスキングを行い、`SystemConfigService.getConfigValue()`（Spring Cache 付き）を設定値の唯一の参照点にする。メンテナンスモードは `/tenant/**` への `HandlerInterceptor` で 503 を返し、SMTP は `MailServiceImpl` が送信時に DB 設定から `JavaMailSenderImpl` を構築する（DB 未設定時は従来の環境変数ベースにフォールバック）。

**Tech Stack:** Spring Boot 3.5 / Java 21 / Liquibase / MapStruct / Spring Cache (ConcurrentMap) / Next.js 16 + React 19 / Jest + React Testing Library

**スコープ外（別プランで実施）:** API レート制限、監査ログビューア、デフォルトテナント設定テンプレート、ユーザー設定、テナント設定拡充。

## Global Constraints

- コード内のコメント・ログ・エラーメッセージはすべて日本語で記述する（既存ファイル修正時に英語コメントを見つけたら日本語化する）
- TDD 厳守: テストを先に書き、失敗を確認してから実装する
- カバレッジ 70% 必須（CI で強制）。JaCoCo は `config` / `model` / `repository` / `mapper` / `exception` パッケージを除外
- Java: クラス・メソッド・変数は CamelCase。ワイルドカード import 禁止、FQCN 直書き禁止
- JSON キーは Jackson のグローバル設定（`JacksonConfig` の `SNAKE_CASE`）で自動変換されるため、Java 側は CamelCase のまま書く
- Frontend: API 関連の型・プロパティ名は snake_case。API クライアントは `src/lib/client.ts` の axios インスタンス
- DB 変更は Liquibase changeset（`backend/src/main/resources/db/changelog/releases/v0.1.0/central/` に YAML を作成し、`releases/v0.1.0/master.yaml` に include を追加）。適用済み changeset の変更は禁止
- 権限・メニュー: 既存の `SYSTEM_CONFIG` 権限と `/central/settings` メニュー（`central-005-seed-data` で登録済み）を再利用するため、新規の権限・メニュー登録は不要
- フォーマット: コミット前に `cd backend && ./gradlew spotlessApply`、frontend は `npm run lint:fix`
- 各タスクのコミット前に `task lint && task test`（Docker）またはローカルで `./gradlew test` / `npm test` を実行して green を確認する

## ファイル構成

| 操作 | パス | 責務 |
|------|------|------|
| Create | `backend/src/main/resources/db/changelog/releases/v0.1.0/central/06-config-extend.yaml` | value_type / secret 列追加、SMTP 認証シードデータ |
| Modify | `backend/src/main/resources/db/changelog/releases/v0.1.0/master.yaml` | 上記 include 追加 |
| Modify | `backend/src/main/java/com/kizuna/model/entity/central/SystemConfig.java` | valueType / secret フィールド追加 |
| Modify | `backend/src/main/java/com/kizuna/model/dto/central/config/SystemConfigResponse.java` | valueType / secret フィールド追加 |
| Modify | `backend/src/main/java/com/kizuna/mapper/central/SystemConfigMapper.java` | 更新マッピングの ignore 追加 |
| Modify | `backend/src/main/java/com/kizuna/service/central/config/SystemConfigService.java` | getConfigValue 追加 |
| Modify | `backend/src/main/java/com/kizuna/service/central/config/SystemConfigServiceImpl.java` | 型検証・秘匿マスキング・キャッシュ |
| Create | `backend/src/main/java/com/kizuna/config/interceptor/MaintenanceModeInterceptor.java` | メンテナンスモード 503 応答 |
| Modify | `backend/src/main/java/com/kizuna/config/WebMvcConfig.java` | インターセプタ登録 |
| Modify | `backend/src/main/java/com/kizuna/service/mail/MailServiceImpl.java` | DB の SMTP 設定から送信クライアント構築 |
| Modify | `backend/src/test/java/com/kizuna/service/central/config/SystemConfigServiceImplTest.java` | 検証・マスキング・getConfigValue テスト |
| Create | `backend/src/test/java/com/kizuna/config/interceptor/MaintenanceModeInterceptorTest.java` | インターセプタテスト |
| Modify | `backend/src/test/java/com/kizuna/service/mail/MailServiceImplTests.java` | DB 設定対応後のテストへ書き換え |
| Modify | `frontend/src/types/api.ts` | SystemConfigResponse に value_type / secret 追加 |
| Modify | `frontend/src/app/central/settings/page.tsx` | トグル・数値入力・秘匿マスク UI |
| Create | `frontend/src/app/central/settings/__tests__/page.test.tsx` | 設定画面テスト |

---

### Task 1: DB スキーマ拡張とエンティティ・DTO・マッパー対応

**Files:**
- Create: `backend/src/main/resources/db/changelog/releases/v0.1.0/central/06-config-extend.yaml`
- Modify: `backend/src/main/resources/db/changelog/releases/v0.1.0/master.yaml`
- Modify: `backend/src/main/java/com/kizuna/model/entity/central/SystemConfig.java`
- Modify: `backend/src/main/java/com/kizuna/model/dto/central/config/SystemConfigResponse.java`
- Modify: `backend/src/main/java/com/kizuna/mapper/central/SystemConfigMapper.java`

**Interfaces:**
- Consumes: 既存の `SystemConfig` エンティティ（`configKey` / `configValue` / `category` / `description`）
- Produces: `SystemConfig.getValueType(): String`（`"STRING"` / `"NUMBER"` / `"BOOLEAN"`、デフォルト `"STRING"`）、`SystemConfig.getSecret(): Boolean`（デフォルト `false`）、`SystemConfigResponse.getValueType()` / `getSecret()`。DB 列 `central_configs.value_type` / `central_configs.secret`。SMTP シードキー: `smtp_username` / `smtp_password`（secret=true）/ `smtp_from`

- [ ] **Step 1: changeset ファイルを作成**

`backend/src/main/resources/db/changelog/releases/v0.1.0/central/06-config-extend.yaml` を以下の内容で作成:

```yaml
databaseChangeLog:
  - changeSet:
      id: central-006-config-extend-schema
      author: kanghouchao
      changes:
        - addColumn:
            tableName: central_configs
            columns:
              - column:
                  name: value_type
                  type: VARCHAR(20)
                  defaultValue: STRING
                  remarks: 設定値の型（STRING, NUMBER, BOOLEAN）
                  constraints:
                    nullable: false
              - column:
                  name: secret
                  type: BOOLEAN
                  defaultValueBoolean: false
                  remarks: 秘匿設定（レスポンスで値をマスクする）
                  constraints:
                    nullable: false
  - changeSet:
      id: central-006-config-extend-data
      author: kanghouchao
      changes:
        # 既存キーへの型付け
        - update:
            tableName: central_configs
            columns:
              - column: { name: value_type, value: BOOLEAN }
            where: config_key = 'maintenance_mode'
        - update:
            tableName: central_configs
            columns:
              - column: { name: value_type, value: NUMBER }
            where: config_key = 'smtp_port'
        # 旧シードの smtp_host=localhost は実際には未使用だったため、
        # 「未設定＝環境変数フォールバック」となるよう空に戻す（手動変更済みの値は温存）
        - update:
            tableName: central_configs
            columns:
              - column: { name: config_value, value: "" }
            where: config_key = 'smtp_host' AND config_value = 'localhost'
        # SMTP 認証・送信元のシード追加
        - insert:
            tableName: central_configs
            columns:
              - column: { name: config_key, value: "smtp_username" }
              - column: { name: config_value, value: "" }
              - column: { name: category, value: "SMTP" }
              - column: { name: description, value: "SMTP認証ユーザー名" }
        - insert:
            tableName: central_configs
            columns:
              - column: { name: config_key, value: "smtp_password" }
              - column: { name: config_value, value: "" }
              - column: { name: category, value: "SMTP" }
              - column: { name: description, value: "SMTP認証パスワード" }
              - column: { name: secret, valueBoolean: true }
        - insert:
            tableName: central_configs
            columns:
              - column: { name: config_key, value: "smtp_from" }
              - column: { name: config_value, value: "" }
              - column: { name: category, value: "SMTP" }
              - column: { name: description, value: "メール送信元アドレス" }
```

- [ ] **Step 2: master.yaml に include を追加**

`backend/src/main/resources/db/changelog/releases/v0.1.0/master.yaml` の `central/05-initial-data.yaml` の include の直後に追加:

```yaml
  - include:
      file: central/06-config-extend.yaml
      relativeToChangelogFile: true
```

- [ ] **Step 3: エンティティにフィールドを追加**

`backend/src/main/java/com/kizuna/model/entity/central/SystemConfig.java` の `description` フィールドの後に追加:

```java
  @Builder.Default
  @Column(name = "value_type", nullable = false, length = 20)
  private String valueType = "STRING";

  @Builder.Default
  @Column(name = "secret", nullable = false)
  private Boolean secret = false;
```

（`@Builder.Default` は既にクラスに `@Builder` が付いているため必須。import 追加は不要 — `lombok.Builder` は既存 import に含まれる）

- [ ] **Step 4: レスポンス DTO にフィールドを追加**

`backend/src/main/java/com/kizuna/model/dto/central/config/SystemConfigResponse.java` の `description` フィールドの後に追加:

```java
  private String valueType;
  private Boolean secret;
```

（Jackson のグローバル SNAKE_CASE 設定により JSON では `value_type` / `secret` になる）

- [ ] **Step 5: マッパーの更新マッピングに ignore を追加**

`backend/src/main/java/com/kizuna/mapper/central/SystemConfigMapper.java` の `updateEntityFromRequest` の既存 `@Mapping` 群に追加:

```java
  @Mapping(target = "valueType", ignore = true)
  @Mapping(target = "secret", ignore = true)
```

（`toResponse` は同名フィールドの自動マッピングで対応されるため変更不要）

- [ ] **Step 6: コンパイルと既存テストの確認**

Run: `cd backend && ./gradlew spotlessApply test`
Expected: BUILD SUCCESSFUL（既存テストはすべて PASS。`@Builder.Default` によりビルダー経由の既存テストも影響なし）

- [ ] **Step 7: コミット**

```bash
git add backend/src/main/resources/db/changelog backend/src/main/java/com/kizuna/model backend/src/main/java/com/kizuna/mapper
git commit -m "feat(config): central_configs に value_type / secret 列と SMTP 認証シードを追加"
```

---

### Task 2: SystemConfigService — 型検証・秘匿マスキング・キャッシュ付き参照

**Files:**
- Modify: `backend/src/main/java/com/kizuna/service/central/config/SystemConfigService.java`
- Modify: `backend/src/main/java/com/kizuna/service/central/config/SystemConfigServiceImpl.java`
- Test: `backend/src/test/java/com/kizuna/service/central/config/SystemConfigServiceImplTest.java`

**Interfaces:**
- Consumes: Task 1 の `SystemConfig.getValueType()` / `getSecret()`、既存の `SystemConfigRepository.findByConfigKey(String): Optional<SystemConfig>`
- Produces: `Optional<String> SystemConfigService.getConfigValue(String configKey)`（キャッシュ名 `"systemConfigValues"`、`updateConfig` で evict）。検証失敗時は `ServiceException`（メッセージ: `"真偽値（true / false）を指定してください: <キー>"` / `"数値を指定してください: <キー>"`）。秘匿設定はすべてのレスポンスで `configValue` が `null`

- [ ] **Step 1: 失敗するテストを追加**

`backend/src/test/java/com/kizuna/service/central/config/SystemConfigServiceImplTest.java` に以下を追加。import に `import static org.junit.jupiter.api.Assertions.assertNull;` を追加すること:

```java
  @Test
  @DisplayName("真偽値型の設定に不正な値を指定すると例外が発生すること")
  void updateConfig_invalidBoolean() {
    SystemConfigUpdateRequest request =
        SystemConfigUpdateRequest.builder()
            .configKey("maintenance_mode")
            .configValue("yes")
            .build();
    SystemConfig config =
        SystemConfig.builder().configKey("maintenance_mode").valueType("BOOLEAN").build();
    when(systemConfigRepository.findByConfigKey("maintenance_mode"))
        .thenReturn(Optional.of(config));

    assertThrows(ServiceException.class, () -> systemConfigService.updateConfig(request));
  }

  @Test
  @DisplayName("数値型の設定に不正な値を指定すると例外が発生すること")
  void updateConfig_invalidNumber() {
    SystemConfigUpdateRequest request =
        SystemConfigUpdateRequest.builder().configKey("smtp_port").configValue("abc").build();
    SystemConfig config =
        SystemConfig.builder().configKey("smtp_port").valueType("NUMBER").build();
    when(systemConfigRepository.findByConfigKey("smtp_port")).thenReturn(Optional.of(config));

    assertThrows(ServiceException.class, () -> systemConfigService.updateConfig(request));
  }

  @Test
  @DisplayName("秘匿設定の値はレスポンスでマスクされること")
  void getAllConfigs_masksSecret() {
    SystemConfig config =
        SystemConfig.builder()
            .configKey("smtp_password")
            .configValue("secret-value")
            .secret(true)
            .build();
    SystemConfigResponse response =
        SystemConfigResponse.builder()
            .configKey("smtp_password")
            .configValue("secret-value")
            .build();
    when(systemConfigRepository.findAll()).thenReturn(List.of(config));
    when(systemConfigMapper.toResponse(config)).thenReturn(response);

    List<SystemConfigResponse> result = systemConfigService.getAllConfigs();

    assertEquals(1, result.size());
    assertNull(result.get(0).getConfigValue());
  }

  @Test
  @DisplayName("getConfigValue で設定値を取得できること")
  void getConfigValue() {
    SystemConfig config =
        SystemConfig.builder().configKey("maintenance_mode").configValue("true").build();
    when(systemConfigRepository.findByConfigKey("maintenance_mode"))
        .thenReturn(Optional.of(config));

    assertEquals(Optional.of("true"), systemConfigService.getConfigValue("maintenance_mode"));
  }

  @Test
  @DisplayName("存在しないキーの getConfigValue は空を返すこと")
  void getConfigValue_missing() {
    when(systemConfigRepository.findByConfigKey("unknown")).thenReturn(Optional.empty());

    assertEquals(Optional.empty(), systemConfigService.getConfigValue("unknown"));
  }
```

- [ ] **Step 2: テストの失敗を確認**

Run: `cd backend && ./gradlew test --tests "com.kizuna.service.central.config.SystemConfigServiceImplTest"`
Expected: コンパイルエラー「シンボルを見つけられません: メソッド getConfigValue(String)」で FAIL

- [ ] **Step 3: インターフェースにメソッドを追加**

`backend/src/main/java/com/kizuna/service/central/config/SystemConfigService.java` を以下に置き換え:

```java
package com.kizuna.service.central.config;

import com.kizuna.model.dto.central.config.SystemConfigResponse;
import com.kizuna.model.dto.central.config.SystemConfigUpdateRequest;
import java.util.List;
import java.util.Optional;

public interface SystemConfigService {
  List<SystemConfigResponse> getAllConfigs();

  List<SystemConfigResponse> getConfigsByCategory(String category);

  SystemConfigResponse updateConfig(SystemConfigUpdateRequest request);

  /** 設定値を取得する（キャッシュされる。バックエンド内部からの設定参照はこのメソッドを使うこと） */
  Optional<String> getConfigValue(String configKey);
}
```

- [ ] **Step 4: 実装を置き換え**

`backend/src/main/java/com/kizuna/service/central/config/SystemConfigServiceImpl.java` を以下に置き換え:

```java
package com.kizuna.service.central.config;

import com.kizuna.exception.ServiceException;
import com.kizuna.mapper.central.SystemConfigMapper;
import com.kizuna.model.dto.central.config.SystemConfigResponse;
import com.kizuna.model.dto.central.config.SystemConfigUpdateRequest;
import com.kizuna.model.entity.central.SystemConfig;
import com.kizuna.repository.central.SystemConfigRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

  private final SystemConfigRepository systemConfigRepository;
  private final SystemConfigMapper systemConfigMapper;

  @Override
  @Transactional(readOnly = true)
  public List<SystemConfigResponse> getAllConfigs() {
    return systemConfigRepository.findAll().stream()
        .map(this::toMaskedResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SystemConfigResponse> getConfigsByCategory(String category) {
    return systemConfigRepository.findByCategory(category).stream()
        .map(this::toMaskedResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  @CacheEvict(value = "systemConfigValues", key = "#request.configKey")
  public SystemConfigResponse updateConfig(SystemConfigUpdateRequest request) {
    SystemConfig config =
        systemConfigRepository
            .findByConfigKey(request.getConfigKey())
            .orElseThrow(() -> new ServiceException("設定キーが見つかりません: " + request.getConfigKey()));

    validateValue(config, request.getConfigValue());
    systemConfigMapper.updateEntityFromRequest(request, config);
    SystemConfig saved = systemConfigRepository.save(config);
    return toMaskedResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "systemConfigValues", key = "#configKey")
  public Optional<String> getConfigValue(String configKey) {
    return systemConfigRepository.findByConfigKey(configKey).map(SystemConfig::getConfigValue);
  }

  /** value_type に応じて設定値を検証する */
  private void validateValue(SystemConfig config, String value) {
    if (value == null || value.isBlank()) {
      return; // 未設定（空）は許容する
    }
    if ("BOOLEAN".equals(config.getValueType())
        && !"true".equals(value)
        && !"false".equals(value)) {
      throw new ServiceException("真偽値（true / false）を指定してください: " + config.getConfigKey());
    }
    if ("NUMBER".equals(config.getValueType())) {
      try {
        Long.parseLong(value.trim());
      } catch (NumberFormatException e) {
        throw new ServiceException("数値を指定してください: " + config.getConfigKey());
      }
    }
  }

  /** 秘匿設定の値をマスクしてレスポンスへ変換する */
  private SystemConfigResponse toMaskedResponse(SystemConfig config) {
    SystemConfigResponse response = systemConfigMapper.toResponse(config);
    if (Boolean.TRUE.equals(config.getSecret())) {
      response.setConfigValue(null);
    }
    return response;
  }
}
```

注意: `@Cacheable` は `Optional` を自動的にアンラップして格納する（Spring 標準動作）。`CacheConfig` の `ConcurrentMapCacheManager` は null 値を許容するため追加設定は不要。単一インスタンス構成では `@CacheEvict` により更新が即時反映される（複数インスタンス化する場合は Redis キャッシュへの切り替えが必要 — `CacheConfig` の Javadoc 参照）。

- [ ] **Step 5: テストの成功を確認**

Run: `cd backend && ./gradlew test --tests "com.kizuna.service.central.config.SystemConfigServiceImplTest"`
Expected: 9 件すべて PASS（既存 4 件 + 新規 5 件）

- [ ] **Step 6: 全テストとフォーマットの確認**

Run: `cd backend && ./gradlew spotlessApply test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: コミット**

```bash
git add backend/src/main/java/com/kizuna/service/central/config backend/src/test/java/com/kizuna/service/central/config
git commit -m "feat(config): 設定値の型検証・秘匿マスキング・キャッシュ付き参照を追加"
```

---

### Task 3: メンテナンスモードインターセプタ

**Files:**
- Create: `backend/src/main/java/com/kizuna/config/interceptor/MaintenanceModeInterceptor.java`
- Modify: `backend/src/main/java/com/kizuna/config/WebMvcConfig.java`
- Test: `backend/src/test/java/com/kizuna/config/interceptor/MaintenanceModeInterceptorTest.java`

**Interfaces:**
- Consumes: Task 2 の `SystemConfigService.getConfigValue(String): Optional<String>`（キー `"maintenance_mode"`、値 `"true"` / `"false"`）
- Produces: メンテナンスモード中の `/tenant/**` リクエストへ HTTP 503 + `{"error":"メンテナンス中です。しばらくしてから再度お試しください"}`。`/central/**` は影響を受けない（管理者が解除操作できるようにするため）

- [ ] **Step 1: 失敗するテストを作成**

`backend/src/test/java/com/kizuna/config/interceptor/MaintenanceModeInterceptorTest.java` を作成:

```java
package com.kizuna.config.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kizuna.service.central.config.SystemConfigService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class MaintenanceModeInterceptorTest {

  @Mock private SystemConfigService systemConfigService;

  @InjectMocks private MaintenanceModeInterceptor interceptor;

  @Test
  @DisplayName("メンテナンスモード中はリクエストを 503 で拒否すること")
  void preHandle_maintenanceOn() throws Exception {
    when(systemConfigService.getConfigValue("maintenance_mode")).thenReturn(Optional.of("true"));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tenant/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).contains("メンテナンス中");
  }

  @Test
  @DisplayName("メンテナンスモードでなければ通過すること")
  void preHandle_maintenanceOff() throws Exception {
    when(systemConfigService.getConfigValue("maintenance_mode")).thenReturn(Optional.of("false"));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tenant/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isTrue();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("設定が存在しない場合は通過すること")
  void preHandle_configMissing() throws Exception {
    when(systemConfigService.getConfigValue("maintenance_mode")).thenReturn(Optional.empty());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tenant/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
  }
}
```

- [ ] **Step 2: テストの失敗を確認**

Run: `cd backend && ./gradlew test --tests "com.kizuna.config.interceptor.MaintenanceModeInterceptorTest"`
Expected: コンパイルエラー「シンボルを見つけられません: クラス MaintenanceModeInterceptor」で FAIL

- [ ] **Step 3: インターセプタを実装**

`backend/src/main/java/com/kizuna/config/interceptor/MaintenanceModeInterceptor.java` を作成:

```java
package com.kizuna.config.interceptor;

import com.kizuna.service.central.config.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** メンテナンスモード中にテナント向けリクエストを 503 で拒否するインターセプタ。 */
@Component
@RequiredArgsConstructor
public class MaintenanceModeInterceptor implements HandlerInterceptor {

  private static final String CONFIG_KEY_MAINTENANCE = "maintenance_mode";

  private final SystemConfigService systemConfigService;

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler)
      throws Exception {
    boolean maintenance =
        systemConfigService
            .getConfigValue(CONFIG_KEY_MAINTENANCE)
            .map(Boolean::parseBoolean)
            .orElse(false);
    if (!maintenance) {
      return true;
    }
    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"error\":\"メンテナンス中です。しばらくしてから再度お試しください\"}");
    return false;
  }
}
```

- [ ] **Step 4: WebMvcConfig に登録**

`backend/src/main/java/com/kizuna/config/WebMvcConfig.java` を以下に置き換え:

```java
package com.kizuna.config;

import com.kizuna.config.interceptor.MaintenanceModeInterceptor;
import com.kizuna.config.interceptor.TenantIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @NonNull private final TenantIdInterceptor tenantIdInterceptor;
  @NonNull private final MaintenanceModeInterceptor maintenanceModeInterceptor;

  public WebMvcConfig(
      @NonNull TenantIdInterceptor tenantIdInterceptor,
      @NonNull MaintenanceModeInterceptor maintenanceModeInterceptor) {
    this.tenantIdInterceptor = tenantIdInterceptor;
    this.maintenanceModeInterceptor = maintenanceModeInterceptor;
  }

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    // メンテナンス判定はテナントコンテキスト設定より先に行う
    registry.addInterceptor(maintenanceModeInterceptor).addPathPatterns("/tenant/**");
    registry.addInterceptor(tenantIdInterceptor).addPathPatterns("/tenant/**", "/files/**");
  }
}
```

- [ ] **Step 5: テストの成功を確認**

Run: `cd backend && ./gradlew test --tests "com.kizuna.config.interceptor.MaintenanceModeInterceptorTest"`
Expected: 3 件すべて PASS

- [ ] **Step 6: 全テストとフォーマットの確認**

Run: `cd backend && ./gradlew spotlessApply test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: コミット**

```bash
git add backend/src/main/java/com/kizuna/config backend/src/test/java/com/kizuna/config/interceptor
git commit -m "feat(config): メンテナンスモード中にテナント API を 503 で拒否するインターセプタを追加"
```

---

### Task 4: MailService の SMTP 設定 DB 反映

**Files:**
- Modify: `backend/src/main/java/com/kizuna/service/mail/MailServiceImpl.java`
- Test: `backend/src/test/java/com/kizuna/service/mail/MailServiceImplTests.java`

**Interfaces:**
- Consumes: Task 2 の `SystemConfigService.getConfigValue(String): Optional<String>`（キー: `smtp_host` / `smtp_port` / `smtp_username` / `smtp_password` / `smtp_from`）
- Produces: `MailService.send(String, String, String)` の既存シグネチャは不変。`MailServiceImpl` のコンストラクタは `MailServiceImpl(SystemConfigService)` に変わる（`JavaMailSender` はフィールドインジェクションのまま）。パッケージプライベートの `JavaMailSender resolveSender()` がテスト用に参照可能

- [ ] **Step 1: テストを書き換え（失敗させる）**

`backend/src/test/java/com/kizuna/service/mail/MailServiceImplTests.java` を以下に置き換え:

```java
package com.kizuna.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.service.central.config.SystemConfigService;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@ExtendWith(MockitoExtension.class)
class MailServiceImplTests {

  @Mock private SystemConfigService systemConfigService;

  private MailServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new MailServiceImpl(systemConfigService);
  }

  /** 環境変数ベースのフォールバック送信クライアントをリフレクションで注入する */
  private void injectMailSender(JavaMailSender sender) {
    try {
      Field field = MailServiceImpl.class.getDeclaredField("mailSender");
      field.setAccessible(true);
      field.set(service, sender);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("SMTP 設定もフォールバックもない場合はログのみで例外を投げないこと")
  void send_noConfigNoFallback() {
    when(systemConfigService.getConfigValue("smtp_host")).thenReturn(Optional.empty());

    service.send("to@example.com", "件名", "本文");
    // 例外が出なければ成功
  }

  @Test
  @DisplayName("DB の SMTP 設定がない場合はフォールバック送信クライアントを使用すること")
  void send_usesFallbackSender() {
    when(systemConfigService.getConfigValue("smtp_host")).thenReturn(Optional.empty());
    when(systemConfigService.getConfigValue("smtp_from")).thenReturn(Optional.empty());
    JavaMailSender fallback = mock(JavaMailSender.class);
    injectMailSender(fallback);

    service.send("to@example.com", "件名", "本文");

    verify(fallback).send(any(SimpleMailMessage.class));
  }

  @Test
  @DisplayName("smtp_from が設定されていれば送信元が設定されること")
  void send_setsFromAddress() {
    when(systemConfigService.getConfigValue("smtp_host")).thenReturn(Optional.empty());
    when(systemConfigService.getConfigValue("smtp_from"))
        .thenReturn(Optional.of("noreply@kizuna.test"));
    JavaMailSender fallback = mock(JavaMailSender.class);
    injectMailSender(fallback);

    service.send("to@example.com", "件名", "本文");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(fallback).send(captor.capture());
    assertThat(captor.getValue().getFrom()).isEqualTo("noreply@kizuna.test");
  }

  @Test
  @DisplayName("DB の SMTP 設定から送信クライアントが構築されること")
  void resolveSender_buildsFromDbConfig() {
    when(systemConfigService.getConfigValue("smtp_host"))
        .thenReturn(Optional.of("smtp.example.com"));
    when(systemConfigService.getConfigValue("smtp_port")).thenReturn(Optional.of("587"));
    when(systemConfigService.getConfigValue("smtp_username")).thenReturn(Optional.of("user"));
    when(systemConfigService.getConfigValue("smtp_password")).thenReturn(Optional.of("pass"));

    JavaMailSender sender = service.resolveSender();

    assertThat(sender).isInstanceOf(JavaMailSenderImpl.class);
    JavaMailSenderImpl impl = (JavaMailSenderImpl) sender;
    assertThat(impl.getHost()).isEqualTo("smtp.example.com");
    assertThat(impl.getPort()).isEqualTo(587);
    assertThat(impl.getUsername()).isEqualTo("user");
    assertThat(impl.getPassword()).isEqualTo("pass");
    assertThat(impl.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
  }

  @Test
  @DisplayName("smtp_username が空なら認証なしで構築されること")
  void resolveSender_withoutAuth() {
    when(systemConfigService.getConfigValue("smtp_host"))
        .thenReturn(Optional.of("smtp.example.com"));
    when(systemConfigService.getConfigValue("smtp_port")).thenReturn(Optional.of(""));
    when(systemConfigService.getConfigValue("smtp_username")).thenReturn(Optional.of(""));

    JavaMailSenderImpl impl = (JavaMailSenderImpl) service.resolveSender();

    assertThat(impl.getPort()).isEqualTo(25);
    assertThat(impl.getUsername()).isNull();
    assertThat(impl.getJavaMailProperties().getProperty("mail.smtp.auth")).isNull();
  }

  @Test
  @DisplayName("送信時の例外は握りつぶしてログに記録すること")
  void send_swallowsException() {
    when(systemConfigService.getConfigValue("smtp_host")).thenReturn(Optional.empty());
    when(systemConfigService.getConfigValue("smtp_from")).thenReturn(Optional.empty());
    JavaMailSender fallback = mock(JavaMailSender.class);
    doThrow(new RuntimeException("接続失敗")).when(fallback).send(any(SimpleMailMessage.class));
    injectMailSender(fallback);

    service.send("to@example.com", "件名", "本文");
    // 例外が伝播しなければ成功
  }
}
```

- [ ] **Step 2: テストの失敗を確認**

Run: `cd backend && ./gradlew test --tests "com.kizuna.service.mail.MailServiceImplTests"`
Expected: コンパイルエラー（`MailServiceImpl(SystemConfigService)` コンストラクタと `resolveSender()` が存在しない）で FAIL

- [ ] **Step 3: MailServiceImpl を実装**

`backend/src/main/java/com/kizuna/service/mail/MailServiceImpl.java` を以下に置き換え:

```java
package com.kizuna.service.mail;

import com.kizuna.service.central.config.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/** システム設定（DB）の SMTP 設定を優先して使用するメール送信サービス。 */
@Log4j2
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

  private final SystemConfigService systemConfigService;

  @Autowired(required = false)
  private JavaMailSender mailSender;

  @Override
  public void send(String to, String subject, String body) {
    try {
      JavaMailSender sender = resolveSender();
      if (sender == null) {
        // フォールバック: メール設定がなくてもシステムが動作するようログ出力のみ行う
        log.info("[MAIL-FALLBACK] to={} subject={} body={} ", to, subject, body);
        return;
      }
      SimpleMailMessage msg = new SimpleMailMessage();
      systemConfigService
          .getConfigValue("smtp_from")
          .filter(v -> !v.isBlank())
          .ifPresent(msg::setFrom);
      msg.setTo(to);
      msg.setSubject(subject);
      msg.setText(body);
      sender.send(msg);
    } catch (Exception e) {
      log.error("メール送信に失敗しました to={}: {}", to, e.getMessage());
    }
  }

  /** DB の SMTP 設定があればそこから送信クライアントを構築し、なければ環境変数ベースの設定にフォールバックする。 */
  JavaMailSender resolveSender() {
    String host = systemConfigService.getConfigValue("smtp_host").orElse("");
    if (host.isBlank()) {
      return mailSender;
    }
    // ponytail: 送信の度にクライアントを生成する。送信量が増えたら設定更新時に再生成するキャッシュ方式へ
    JavaMailSenderImpl impl = new JavaMailSenderImpl();
    impl.setHost(host);
    impl.setPort(
        systemConfigService
            .getConfigValue("smtp_port")
            .filter(v -> !v.isBlank())
            .map(v -> Integer.parseInt(v.trim()))
            .orElse(25));
    systemConfigService
        .getConfigValue("smtp_username")
        .filter(v -> !v.isBlank())
        .ifPresent(
            username -> {
              impl.setUsername(username);
              systemConfigService.getConfigValue("smtp_password").ifPresent(impl::setPassword);
              impl.getJavaMailProperties().put("mail.smtp.auth", "true");
            });
    return impl;
  }
}
```

- [ ] **Step 4: テストの成功を確認**

Run: `cd backend && ./gradlew test --tests "com.kizuna.service.mail.MailServiceImplTests"`
Expected: 6 件すべて PASS

- [ ] **Step 5: 全テストとフォーマットの確認**

Run: `cd backend && ./gradlew spotlessApply test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: コミット**

```bash
git add backend/src/main/java/com/kizuna/service/mail backend/src/test/java/com/kizuna/service/mail
git commit -m "feat(mail): SMTP 設定を DB のシステム設定から動的に反映"
```

---

### Task 5: フロントエンド — 型定義と設定画面のトグル・数値・秘匿対応

**Files:**
- Modify: `frontend/src/types/api.ts:192-206`（`SystemConfigResponse`）
- Modify: `frontend/src/app/central/settings/page.tsx`
- Test: `frontend/src/app/central/settings/__tests__/page.test.tsx`

**Interfaces:**
- Consumes: Task 1/2 の REST レスポンス（`value_type`: `"STRING" | "NUMBER" | "BOOLEAN"`、`secret`: boolean、秘匿設定の `config_value` は省略される）。バリデーションエラーは HTTP 400 の `{ "error": "<日本語メッセージ>" }`
- Produces: なし（末端の UI）

- [ ] **Step 1: 失敗するテストを作成**

`frontend/src/app/central/settings/__tests__/page.test.tsx` を作成（`__tests__` はアンダースコア始まりのため Next.js のルーティングから除外される）:

```tsx
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import SystemSettingsPage from '../page';

const mockGetAllConfigs = jest.fn();
const mockUpdateConfig = jest.fn();

jest.mock('@/services/central/config', () => ({
  systemConfigService: {
    getAllConfigs: (...args: unknown[]) => mockGetAllConfigs(...args),
    updateConfig: (...args: unknown[]) => mockUpdateConfig(...args),
  },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const configs = [
  {
    id: 1,
    config_key: 'maintenance_mode',
    config_value: 'false',
    value_type: 'BOOLEAN',
    secret: false,
    category: 'SYSTEM',
    description: 'システムメンテナンスモード',
    created_at: '',
    updated_at: '',
  },
  {
    id: 2,
    config_key: 'smtp_port',
    config_value: '25',
    value_type: 'NUMBER',
    secret: false,
    category: 'SMTP',
    description: 'SMTPサーバーポート',
    created_at: '',
    updated_at: '',
  },
  {
    id: 3,
    config_key: 'smtp_password',
    value_type: 'STRING',
    secret: true,
    category: 'SMTP',
    description: 'SMTP認証パスワード',
    created_at: '',
    updated_at: '',
  },
];

describe('SystemSettingsPage', () => {
  beforeEach(() => {
    mockGetAllConfigs.mockResolvedValue(configs);
    mockUpdateConfig.mockResolvedValue({});
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('真偽値設定はトグルで表示され、クリックで更新される', async () => {
    render(<SystemSettingsPage />);
    const toggle = await screen.findByRole('switch', { name: 'maintenance_mode' });
    expect(toggle).toHaveAttribute('aria-checked', 'false');

    fireEvent.click(toggle);

    await waitFor(() =>
      expect(mockUpdateConfig).toHaveBeenCalledWith({
        config_key: 'maintenance_mode',
        config_value: 'true',
      })
    );
  });

  it('秘匿設定は値がマスク表示される', async () => {
    render(<SystemSettingsPage />);
    expect(await screen.findByText('(秘匿設定)')).toBeInTheDocument();
  });

  it('数値設定は編集時に数値入力欄になる', async () => {
    render(<SystemSettingsPage />);
    fireEvent.click(await screen.findByText('25'));
    expect(screen.getByRole('spinbutton')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: テストの失敗を確認**

Run: `cd frontend && npm test -- src/app/central/settings`
Expected: FAIL（`switch` ロールが見つからない、`(秘匿設定)` が見つからない）

- [ ] **Step 3: 型定義を更新**

`frontend/src/types/api.ts` の `SystemConfigResponse` を以下に置き換え（`SystemConfigUpdateRequest` は変更なし）:

```ts
// システム設定レスポンス
export interface SystemConfigResponse {
  id: number;
  config_key: string;
  config_value?: string;
  value_type: 'STRING' | 'NUMBER' | 'BOOLEAN';
  secret: boolean;
  category: string;
  description?: string;
  created_at: string;
  updated_at: string;
}
```

（バックエンドの Jackson は `NON_NULL` 設定のため、秘匿設定の `config_value` は JSON に含まれない → optional にする）

- [ ] **Step 4: 設定画面を更新**

`frontend/src/app/central/settings/page.tsx` を以下に置き換え:

```tsx
'use client';

import { useState, useEffect } from 'react';
import { toast } from 'react-hot-toast';
import { systemConfigService } from '@/services/central/config';
import { SystemConfigResponse, SystemConfigUpdateRequest } from '@/types/api';

type ConfigGroup = {
  [category: string]: SystemConfigResponse[];
};

// axios エラーからサーバーのバリデーションメッセージを取り出す
function errorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const data = (error as { response?: { data?: { error?: string } } }).response?.data;
    if (data?.error) return data.error;
  }
  return '設定の更新に失敗しました';
}

export default function SystemSettingsPage() {
  const [configs, setConfigs] = useState<SystemConfigResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchConfigs();
  }, []);

  const fetchConfigs = async () => {
    try {
      const data = await systemConfigService.getAllConfigs();
      setConfigs(data);
    } catch (error) {
      console.error('設定の取得に失敗しました', error);
      toast.error('設定の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const saveConfig = async (configKey: string, configValue: string) => {
    const request: SystemConfigUpdateRequest = {
      config_key: configKey,
      config_value: configValue,
    };
    await systemConfigService.updateConfig(request);
    setConfigs(prev =>
      prev.map(c => (c.config_key === configKey ? { ...c, config_value: configValue } : c))
    );
    toast.success('設定を更新しました');
  };

  const handleToggle = async (config: SystemConfigResponse) => {
    setSaving(true);
    try {
      await saveConfig(config.config_key, config.config_value === 'true' ? 'false' : 'true');
    } catch (error) {
      console.error('設定の更新に失敗しました', error);
      toast.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (config: SystemConfigResponse) => {
    setEditingKey(config.config_key);
    // 秘匿設定は現在値を取得できないため空欄から入力する
    setEditValue(config.secret ? '' : config.config_value || '');
  };

  const handleCancel = () => {
    setEditingKey(null);
    setEditValue('');
  };

  const handleSave = async () => {
    if (!editingKey) return;
    setSaving(true);
    try {
      await saveConfig(editingKey, editValue);
      setEditingKey(null);
    } catch (error) {
      console.error('設定の更新に失敗しました', error);
      toast.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  // カテゴリごとにグループ化
  const groupedConfigs: ConfigGroup = configs.reduce((acc, config) => {
    const category = config.category || 'その他';
    if (!acc[category]) {
      acc[category] = [];
    }
    acc[category].push(config);
    return acc;
  }, {} as ConfigGroup);

  if (loading) {
    return <div className="p-8 text-center text-gray-500">読み込み中...</div>;
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">システム設定</h1>
        <p className="mt-2 text-sm text-gray-600">
          プラットフォーム全体の共通設定を管理します。変更は即座に反映される場合があります。
        </p>
      </div>

      {Object.keys(groupedConfigs).length === 0 ? (
        <div className="bg-white p-8 rounded-lg shadow text-center text-gray-500">
          設定項目がありません。
        </div>
      ) : (
        Object.entries(groupedConfigs).map(([category, items]) => (
          <div
            key={category}
            className="bg-white shadow rounded-lg overflow-hidden border border-gray-200"
          >
            <div className="px-6 py-4 border-b border-gray-200 bg-gray-50 flex items-center justify-between">
              <h3 className="text-lg font-medium text-gray-900">{category}</h3>
              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                {items.length} 項目
              </span>
            </div>
            <ul className="divide-y divide-gray-200">
              {items.map(config => (
                <li key={config.config_key} className="p-6 hover:bg-gray-50 transition-colors">
                  <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-900">
                          {config.config_key}
                        </span>
                        {config.description && (
                          <span className="text-xs text-gray-500 ml-2">{config.description}</span>
                        )}
                      </div>

                      {config.value_type === 'BOOLEAN' ? (
                        <div className="mt-2">
                          <button
                            type="button"
                            role="switch"
                            aria-checked={config.config_value === 'true'}
                            aria-label={config.config_key}
                            onClick={() => handleToggle(config)}
                            disabled={saving}
                            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                              config.config_value === 'true' ? 'bg-indigo-600' : 'bg-gray-300'
                            }`}
                          >
                            <span
                              className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                                config.config_value === 'true' ? 'translate-x-6' : 'translate-x-1'
                              }`}
                            />
                          </button>
                        </div>
                      ) : editingKey === config.config_key ? (
                        <div className="mt-3">
                          {config.secret ? (
                            <input
                              type="password"
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                              placeholder="新しい値を入力"
                              className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
                            />
                          ) : config.value_type === 'NUMBER' ? (
                            <input
                              type="number"
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                              className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
                            />
                          ) : (
                            <textarea
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                              className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
                              rows={3}
                            />
                          )}
                          <div className="mt-2 flex justify-end gap-2">
                            <button
                              onClick={handleCancel}
                              className="px-3 py-1.5 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                              disabled={saving}
                            >
                              キャンセル
                            </button>
                            <button
                              onClick={handleSave}
                              className="px-3 py-1.5 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
                              disabled={saving}
                            >
                              {saving ? '保存中...' : '保存'}
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div
                          className="mt-2 text-sm text-gray-700 break-all font-mono bg-gray-50 p-2 rounded cursor-pointer hover:bg-gray-100 border border-transparent hover:border-gray-300"
                          onClick={() => handleEdit(config)}
                          title="クリックして編集"
                        >
                          {config.secret ? (
                            <span className="text-gray-400 italic">(秘匿設定)</span>
                          ) : (
                            config.config_value || (
                              <span className="text-gray-400 italic">(未設定)</span>
                            )
                          )}
                        </div>
                      )}
                    </div>

                    {config.value_type !== 'BOOLEAN' && editingKey !== config.config_key && (
                      <div className="shrink-0">
                        <button
                          onClick={() => handleEdit(config)}
                          className="text-indigo-600 hover:text-indigo-900 text-sm font-medium"
                        >
                          編集
                        </button>
                      </div>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        ))
      )}
    </div>
  );
}
```

- [ ] **Step 5: テストの成功を確認**

Run: `cd frontend && npm test -- src/app/central/settings`
Expected: 3 件すべて PASS

- [ ] **Step 6: 全テストと lint の確認**

Run: `cd frontend && npm run lint:fix && npm run format && npm test`
Expected: lint エラーなし、全テスト PASS

- [ ] **Step 7: コミット**

```bash
git add frontend/src/types/api.ts frontend/src/app/central/settings
git commit -m "feat(frontend): システム設定画面をトグル・数値・秘匿設定に対応"
```

---

### Task 6: 結合確認（手動）

**Files:** なし（検証のみ、コミットなし）

- [ ] **Step 1: 全体テスト（Docker）**

Run: `task lint && task test`
Expected: すべて green（カバレッジ 70% 以上）

- [ ] **Step 2: フルスタック起動と Liquibase 適用確認**

Run: `task build && task up && task logs service=backend`
Expected: 起動ログに changeset `central-006-config-extend-schema` / `central-006-config-extend-data` の適用が出力され、エラーなし

- [ ] **Step 3: メンテナンスモードの動作確認**

1. ブラウザで `http://kizuna.test` にアクセスし、Central 管理者（admin / pass）でログイン
2. 「設定 > システム設定」で `maintenance_mode` がトグル表示されることを確認し、ON にする
3. 確認: `curl -s -o /dev/null -w "%{http_code}" -X POST -H "Host: store1.kizuna.test" -H "Content-Type: application/json" -d '{"email":"admin@store1.kizuna.com","password":"pass"}' http://127.0.0.1/api/tenant/login`
   Expected: `503`
4. トグルを OFF に戻し、同じ curl が `503` 以外（`200` または認証系のステータス）に戻ることを確認（キャッシュ evict による即時反映の確認）

- [ ] **Step 4: 秘匿設定とバリデーションの確認**

1. システム設定画面で `smtp_password` の値が「(秘匿設定)」と表示されることを確認
2. `smtp_port` に `abc` を入力して保存し、「数値を指定してください: smtp_port」のトーストが表示されることを確認
3. `smtp_port` に `587` を入力して保存が成功することを確認

- [ ] **Step 5: issue #170 へ進捗コメント（任意）**

Central 設定の実効化が完了したことを issue #170 にコメントする（残タスク: API レート制限・監査ログ・ユーザー設定・テナント設定拡充は別プラン）。
