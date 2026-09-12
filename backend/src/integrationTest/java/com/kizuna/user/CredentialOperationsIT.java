package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kizuna.auth.application.EmergencyElevationService;
import com.kizuna.auth.infrastructure.CredentialVersionService;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.application.CredentialOperations;
import com.kizuna.user.domain.EmergencyElevation;
import com.kizuna.user.domain.EmergencyElevationRepository;
import com.kizuna.user.domain.EmergencyElevationStatus;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class CredentialOperationsIT extends CrossStoreTestSupport {
  @Autowired private PlatformUserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PermissionRepository permissions;
  @Autowired private PasswordEncoder encoder;
  @Autowired private CredentialOperations operations;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private RedisTemplate<String, Object> redis;
  @Autowired private EmergencyElevationService elevations;
  @Autowired private EmergencyElevationRepository elevationRepository;
  @MockitoSpyBean private CredentialVersionService versions;

  private PlatformUser target;
  private Long roleId;
  private String key;
  private TransactionTemplate transaction;

  @BeforeEach
  void createTarget() {
    transaction = new TransactionTemplate(transactionManager);
    roleId =
        roles
            .findByName("資格情報IT店員")
            .orElseGet(
                () ->
                    roles.save(
                        Role.builder()
                            .name("資格情報IT店員")
                            .permissionIds(
                                permissions
                                    .findByCodeIn(Set.of(PermissionCode.ORDER_MANAGE.name()))
                                    .stream()
                                    .map(Permission::getId)
                                    .collect(Collectors.toSet()))
                            .build()))
            .getId();
    target =
        users.saveAndFlush(
            PlatformUser.builder()
                .email("credential-it-" + UUID.randomUUID() + "@kizuna.test")
                .password(encoder.encode(NEW_ACCOUNT_PASSWORD))
                .displayName("資格情報IT")
                .enabled(true)
                .userType(UserType.STAFF)
                .roleIds(Set.of(roleId))
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(STORE_A))
                .build());
    key = "credential-version:" + target.getEmail();
  }

  @AfterEach
  void cleanCache() {
    redis.delete(key);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stoppedStaffRecoversOnlyAfterReadingCurrentDetails(boolean changeGrants) {
    var headers = managerHeaders(STORE_A);
    String oldToken = loginWithPassword(target.getEmail(), NEW_ACCOUNT_PASSWORD);
    versions.reflect(target.getEmail(), 0L);
    JsonNode before = detail(headers);
    var original = updateFrom(before, false);
    doThrow(new RedisConnectionFailureException("Redis 書込み失敗"))
        .when(versions)
        .reflect(target.getEmail(), 1L);

    assertThat(update(headers, original).getStatusCode())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    PlatformUser stopped = users.findById(target.getId()).orElseThrow();
    assertThat(stopped.getEnabled()).isFalse();
    assertThat(stopped.getCredentialVersion()).isEqualTo(1L);
    assertThat(stopped.getVersion()).isGreaterThan(before.path("version").asLong());
    assertThat(redis.opsForValue().get(key)).isEqualTo("0");
    clearInvocations(versions);

    assertThat(update(headers, original).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    verify(versions, never()).reflect(target.getEmail(), 1L);
    Long changedRole =
        changeGrants
            ? roles
                .saveAndFlush(
                    Role.builder()
                        .name("回復IT授権変更-" + UUID.randomUUID())
                        .permissionIds(
                            permissions
                                .findByCodeIn(Set.of(PermissionCode.ORDER_MANAGE.name()))
                                .stream()
                                .map(Permission::getId)
                                .collect(Collectors.toSet()))
                        .build())
                .getId()
            : roleId;
    if (changeGrants) {
      transaction.executeWithoutResult(
          status -> {
            PlatformUser current = users.findById(target.getId()).orElseThrow();
            current.reassignGrants(
                Set.of(changedRole), StoreScopeType.SPECIFIC_STORES, Set.of(STORE_A));
            users.saveAndFlush(current);
          });
    }
    JsonNode current = detail(headers);
    assertThat(current.path("enabled").asBoolean()).isFalse();
    assertThat(current.path("store_scope_type").asString()).isEqualTo("SPECIFIC_STORES");
    assertThat(current.path("roles").get(0).path("id").asLong()).isEqualTo(changedRole);
    doCallRealMethod().when(versions).reflect(target.getEmail(), 1L);
    assertThat(update(headers, updateFrom(current, false)).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(users.findCredentialVersionByEmail(target.getEmail())).contains(1L);
    assertThat(redis.opsForValue().get(key)).isEqualTo("1");
    JsonNode recovered = detail(headers);
    assertThat(recovered.path("store_scope_type")).isEqualTo(current.path("store_scope_type"));
    assertThat(recovered.path("store_ids")).isEqualTo(current.path("store_ids"));
    assertThat(recovered.path("roles")).isEqualTo(current.path("roles"));
    var tokenHeaders = new HttpHeaders();
    tokenHeaders.setBearerAuth(oldToken);
    assertThat(
            rest.exchange(
                    "/platform/me", HttpMethod.GET, new HttpEntity<>(tokenHeaders), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void concurrentChangeAfterDetailStillRejectsStaleStop() {
    var headers = managerHeaders(STORE_A);
    JsonNode before = detail(headers);
    transaction.executeWithoutResult(
        status -> {
          PlatformUser current = users.findById(target.getId()).orElseThrow();
          current.updateDisplayName("並行更新");
          users.saveAndFlush(current);
        });
    clearInvocations(versions);
    assertThat(update(headers, updateFrom(before, false)).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    verify(versions, never()).reflect(target.getEmail(), 1L);
    assertThat(users.findById(target.getId()).orElseThrow().getEnabled()).isTrue();
  }

  @Test
  void proxyRejectsAllOperationsWithoutTransaction() {
    assertThat(AopUtils.isAopProxy(operations)).isTrue();
    assertThatThrownBy(() -> operations.stop(target))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> operations.changePassword(target, "encoded"))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> operations.invalidateSessions(target))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(target.getCredentialVersion()).isZero();
    assertThat(target.getEnabled()).isTrue();
  }

  @Test
  void outerRollbackPersistsNeitherCredentialsNorRedis() {
    versions.reflect(target.getEmail(), 0L);
    clearInvocations(versions);
    transaction.executeWithoutResult(
        status -> {
          PlatformUser current = users.findById(target.getId()).orElseThrow();
          operations.stop(current);
          users.saveAndFlush(current);
          assertThat(redis.opsForValue().get(key)).isEqualTo("0");
          status.setRollbackOnly();
        });
    PlatformUser current = users.findById(target.getId()).orElseThrow();
    assertThat(current.getEnabled()).isTrue();
    assertThat(current.getCredentialVersion()).isZero();
    assertThat(current.getVersion()).isEqualTo(target.getVersion());
    verify(versions, never()).reflect(target.getEmail(), 1L);
    assertThat(redis.opsForValue().get(key)).isEqualTo("0");
  }

  @Test
  void emergencyRevocationAndActivatorVersionCommitAndRollbackTogether() {
    var first =
        elevationRepository.saveAndFlush(
            EmergencyElevation.activate(target.getId(), STORE_A, "復旧検証", OffsetDateTime.now()));
    var second =
        elevationRepository.saveAndFlush(
            EmergencyElevation.activate(target.getId(), STORE_A, "並行発動", OffsetDateTime.now()));
    String revoker = "tanaka.hanako@kizuna.test";
    long revokerVersion = users.findCredentialVersionByEmail(revoker).orElseThrow();
    versions.reflect(target.getEmail(), 0L);
    transaction.executeWithoutResult(
        status -> {
          elevations.revoke(first.getId(), revoker);
          users.flush();
          elevationRepository.flush();
          status.setRollbackOnly();
        });
    assertThat(users.findCredentialVersionByEmail(target.getEmail())).contains(0L);
    assertThat(redis.opsForValue().get(key)).isEqualTo("0");
    assertThat(elevationRepository.findById(first.getId()).orElseThrow().getStatus())
        .isEqualTo(EmergencyElevationStatus.ACTIVE);
    assertThat(elevationRepository.findById(second.getId()).orElseThrow().getStatus())
        .isEqualTo(EmergencyElevationStatus.ACTIVE);
    elevations.revoke(first.getId(), revoker);
    assertThat(users.findCredentialVersionByEmail(target.getEmail())).contains(1L);
    assertThat(users.findCredentialVersionByEmail(revoker)).contains(revokerVersion);
    assertThat(redis.opsForValue().get(key)).isEqualTo("1");
    assertThat(elevationRepository.findById(first.getId()).orElseThrow().getStatus())
        .isEqualTo(EmergencyElevationStatus.REVOKED);
    assertThat(elevationRepository.findById(second.getId()).orElseThrow().getStatus())
        .isEqualTo(EmergencyElevationStatus.REVOKED);
  }

  private JsonNode detail(HttpHeaders headers) {
    var response =
        rest.exchange(
            "/store/staff-members/" + target.getId(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private Map<String, Object> updateFrom(JsonNode detail, boolean enabled) {
    Set<Long> currentRoles = new HashSet<>();
    detail.path("roles").forEach(role -> currentRoles.add(role.path("id").asLong()));
    Set<Long> currentStores = new HashSet<>();
    detail.path("store_ids").forEach(store -> currentStores.add(store.asLong()));
    return Map.of(
        "version",
        detail.path("version").asLong(),
        "role_ids",
        currentRoles,
        "store_scope_type",
        detail.path("store_scope_type").asString(),
        "store_ids",
        currentStores,
        "enabled",
        enabled);
  }

  private ResponseEntity<JsonNode> update(HttpHeaders headers, Map<String, Object> request) {
    return rest.exchange(
        "/store/staff-members/" + target.getId(),
        HttpMethod.PUT,
        new HttpEntity<>(request, headers),
        JsonNode.class);
  }
}
