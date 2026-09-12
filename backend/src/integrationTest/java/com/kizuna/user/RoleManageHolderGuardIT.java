package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.api.dto.RoleUpdateRequest;
import com.kizuna.user.application.PlatformStaffAccountService;
import com.kizuna.user.application.PlatformStaffService;
import com.kizuna.user.application.RoleManageHolderGuard;
import com.kizuna.user.application.RoleService;
import com.kizuna.user.domain.LastRoleManageHolderException;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 実際の業務トランザクションを競合させ、検査から確定まで G5 の保護が継続することを検証する。 */
class RoleManageHolderGuardIT extends CrossStoreTestSupport {
  @Autowired private PlatformStaffService grants;
  @Autowired private PlatformStaffAccountService accounts;
  @Autowired private RoleService roleService;
  @Autowired private RoleManageHolderGuard guard;
  @Autowired private PlatformUserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PermissionRepository permissions;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;

  private final List<Long> createdUsers = new ArrayList<>();
  private final List<Long> createdRoles = new ArrayList<>();
  private final List<Long> temporarilyDisabled = new ArrayList<>();

  @AfterEach
  void restorePopulation() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              users.deleteAllById(createdUsers);
              users.flush();
              roles.deleteAllById(createdRoles);
              roles.flush();
              temporarilyDisabled.forEach(
                  id -> jdbc.update("update t_users set enabled = true where id = ?", id));
            });
  }

  @ParameterizedTest
  @CsvSource({
    "GRANT,SUSPEND",
    "SUSPEND,GRANT",
    "GRANT,ROLE",
    "ROLE,GRANT",
    "SUSPEND,ROLE",
    "ROLE,SUSPEND"
  })
  void differentEntrypointsSerializeAndTheWaiterCannotRemoveTheLastHolder(
      Operation first, Operation second) throws Exception {
    Fixture fixture = preparePopulation();
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    // 各操作が単独なら許容されることを、同じ初期状態でロールバックして確かめる。
    tx.executeWithoutResult(
        status -> {
          apply(second, fixture.secondUser(), fixture.secondRole(), fixture.hqRole());
          status.setRollbackOnly();
        });
    AtomicInteger waiterPid = new AtomicInteger();
    AtomicReference<Future<?>> result = new AtomicReference<>();
    CountDownLatch started = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      tx.executeWithoutResult(
          status -> {
            int blockerPid = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            apply(first, fixture.firstUser(), fixture.firstRole(), fixture.hqRole());
            result.set(
                executor.submit(
                    () ->
                        tx.executeWithoutResult(
                            waiterStatus -> {
                              waiterPid.set(
                                  jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                              started.countDown();
                              apply(
                                  second,
                                  fixture.secondUser(),
                                  fixture.secondRole(),
                                  fixture.hqRole());
                            })));
            awaitStarted(started);
            awaitBlockedBy(blockerPid, waiterPid.get());
          });
      assertThatThrownBy(() -> result.get().get(30, TimeUnit.SECONDS))
          .hasCauseInstanceOf(LastRoleManageHolderException.class);
    }
    tx.executeWithoutResult(
        status -> {
          assertThat(users.findEnabledRoleHolderIds(roles.findIdsByPermissionCode("ROLE_MANAGE")))
              .containsExactly(fixture.secondUser());
          assertThat(users.findById(fixture.secondUser()).orElseThrow().getEnabled()).isTrue();
          assertThat(users.findById(fixture.secondUser()).orElseThrow().getRoleIds())
              .contains(fixture.secondRole());
          assertThat(roles.findIdsByPermissionCode("ROLE_MANAGE")).contains(fixture.secondRole());
        });
  }

  @Test
  void suspensionWaiterLoadsTheCommittedDisabledStateWithoutIncrementingCredentialsAgain()
      throws Exception {
    Fixture fixture = preparePopulation();
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    AtomicReference<Future<?>> result = new AtomicReference<>();
    AtomicInteger waiterPid = new AtomicInteger();
    CountDownLatch started = new CountDownLatch(1);
    long before = users.findById(fixture.firstUser()).orElseThrow().getCredentialVersion();
    try (var executor = Executors.newSingleThreadExecutor()) {
      tx.executeWithoutResult(
          status -> {
            int blockerPid = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            accounts.suspend(fixture.firstUser(), "operator@kizuna.test");
            result.set(
                executor.submit(
                    () ->
                        tx.executeWithoutResult(
                            waiterStatus -> {
                              waiterPid.set(
                                  jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                              started.countDown();
                              accounts.suspend(fixture.firstUser(), "operator@kizuna.test");
                            })));
            awaitStarted(started);
            awaitBlockedBy(blockerPid, waiterPid.get());
          });
      result.get().get(30, TimeUnit.SECONDS);
    }
    PlatformUser target = users.findById(fixture.firstUser()).orElseThrow();
    assertThat(target.getEnabled()).isFalse();
    assertThat(target.getCredentialVersion()).isEqualTo(before + 1);
  }

  @Test
  void populationCountsPeopleOnceAndExcludesDisabledStaffAndServiceIdentities() {
    Fixture fixture = preparePopulation();
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(
        status -> {
          PlatformUser first = users.findById(fixture.firstUser()).orElseThrow();
          first.reassignGrants(
              Set.of(fixture.firstRole(), fixture.secondRole()),
              StoreScopeType.ALL_STORES,
              Set.of());
          users.saveAndFlush(first);
          PlatformUser second = users.findById(fixture.secondUser()).orElseThrow();
          second.stop();
          users.saveAndFlush(second);
          saveUser(UserType.SERVICE, Set.of(fixture.firstRole()));
          assertThat(
                  users.findEnabledRoleHolderIds(Set.of(fixture.firstRole(), fixture.secondRole())))
              .containsExactly(first.getId());
        });
    assertThatThrownBy(() -> accounts.suspend(fixture.firstUser(), "operator@kizuna.test"))
        .isInstanceOf(LastRoleManageHolderException.class);
    // 一人が別の供給ロールを保持し続ける場合は、片方の供給を除去しても保持者は残る。
    tx.executeWithoutResult(
        status ->
            apply(Operation.ROLE, fixture.firstUser(), fixture.firstRole(), fixture.hqRole()));
    assertThatThrownBy(() -> accounts.suspend(fixture.firstUser(), "operator@kizuna.test"))
        .isInstanceOf(LastRoleManageHolderException.class);
  }

  @Test
  void rolePermissionRemovalAllowsAnAlreadyEmptyPopulation() {
    Fixture fixture = preparePopulation();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              for (Long id : List.of(fixture.firstUser(), fixture.secondUser())) {
                PlatformUser user = users.findById(id).orElseThrow();
                user.stop();
                users.saveAndFlush(user);
              }
              apply(Operation.ROLE, fixture.firstUser(), fixture.firstRole(), fixture.hqRole());
              apply(Operation.ROLE, fixture.secondUser(), fixture.secondRole(), fixture.hqRole());
            });
    assertThat(roles.findIdsByPermissionCode("ROLE_MANAGE"))
        .doesNotContain(fixture.firstRole(), fixture.secondRole());
  }

  @Test
  void everyGuardOperationRejectsCallsWithoutAnExistingTransaction() {
    assertThatThrownBy(() -> guard.requireAfterGrantChange(null, Set.of()))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> guard.loadForSuspension(-1L))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> guard.requireAfterRolePermissionRemoval(-1L))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  private Fixture preparePopulation() {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              Set<Long> suppliers = roles.findIdsByPermissionCode("ROLE_MANAGE");
              if (!suppliers.isEmpty()) {
                temporarilyDisabled.addAll(users.findEnabledRoleHolderIds(suppliers));
                temporarilyDisabled.forEach(
                    id -> jdbc.update("update t_users set enabled = false where id = ?", id));
              }
              long firstRole = saveRole(Set.of("ROLE_MANAGE", "STAFF_ACCOUNT_MANAGE"));
              long secondRole = saveRole(Set.of("ROLE_MANAGE", "STAFF_ACCOUNT_MANAGE"));
              long hqRole = saveRole(Set.of("STAFF_ACCOUNT_MANAGE"));
              return new Fixture(
                  saveUser(UserType.STAFF, Set.of(firstRole)),
                  saveUser(UserType.STAFF, Set.of(secondRole)),
                  firstRole,
                  secondRole,
                  hqRole);
            });
  }

  private long saveRole(Set<String> codes) {
    Set<Long> ids =
        permissions.findByCodeIn(codes).stream().map(Permission::getId).collect(Collectors.toSet());
    Role role =
        roles.saveAndFlush(
            Role.builder().name("守衛IT_" + UUID.randomUUID()).permissionIds(ids).build());
    createdRoles.add(role.getId());
    return role.getId();
  }

  private long saveUser(UserType type, Set<Long> roleIds) {
    PlatformUser user =
        users.saveAndFlush(
            PlatformUser.builder()
                .email(type == UserType.SERVICE ? null : UUID.randomUUID() + "@kizuna.test")
                .password(type == UserType.SERVICE ? null : "hash")
                .displayName("守衛IT対象")
                .enabled(true)
                .userType(type)
                .roleIds(roleIds)
                .storeScopeType(StoreScopeType.ALL_STORES)
                .storeIds(Set.of())
                .build());
    createdUsers.add(user.getId());
    return user.getId();
  }

  private void apply(Operation operation, Long userId, Long roleId, Long hqRole) {
    switch (operation) {
      case GRANT -> {
        PlatformUser user = users.findById(userId).orElseThrow();
        PlatformStaffUpdateRequest request = new PlatformStaffUpdateRequest();
        request.setRoleIds(Set.of(hqRole));
        request.setStoreScopeType(StoreScopeType.ALL_STORES);
        request.setStoreIds(Set.of());
        request.setVersion(user.getVersion());
        grants.update(userId, request);
      }
      case SUSPEND -> accounts.suspend(userId, "operator@kizuna.test");
      case ROLE -> {
        Role role = roles.findById(roleId).orElseThrow();
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName(role.getName());
        request.setPermissions(Set.of("STAFF_ACCOUNT_MANAGE"));
        request.setVersion(role.getVersion());
        roleService.update(roleId, request);
      }
    }
  }

  private void awaitBlockedBy(int blockerPid, int waiterPid) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForObject(
                            "select ? = any(pg_blocking_pids(?))",
                            Boolean.class,
                            blockerPid,
                            waiterPid))
                    .as("待機側の業務操作が先行トランザクションのロックを待っていること")
                    .isTrue());
  }

  private static void awaitStarted(CountDownLatch started) {
    try {
      assertThat(started.await(20, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private enum Operation {
    GRANT,
    SUSPEND,
    ROLE
  }

  private record Fixture(
      Long firstUser, Long secondUser, Long firstRole, Long secondRole, Long hqRole) {}
}
