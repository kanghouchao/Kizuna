package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.RoleCreateRequest;
import com.kizuna.user.api.dto.RoleResponse;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.RoleUpdateRequest;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleInUseException;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.RoleSummary;
import com.kizuna.user.domain.StaleRoleUpdateException;
import com.kizuna.user.domain.SystemRoleImmutableException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

  private static final long ORDER_MANAGE_ID = 1L;
  private static final long CUSTOMER_MANAGE_ID = 2L;

  @Mock private RoleRepository roleRepository;
  @Mock private PermissionRepository permissionRepository;
  @Mock private PlatformUserRepository platformUserRepository;

  @InjectMocks private RoleService service;

  @Captor private ArgumentCaptor<Role> roleCaptor;

  private Permission permission(long id, PermissionCode code) {
    Permission permission = Permission.builder().code(code.name()).build();
    permission.setId(id);
    return permission;
  }

  private Role role(long id, String name, boolean systemRole, Set<Long> permissionIds) {
    Role role =
        Role.builder().name(name).systemRole(systemRole).permissionIds(permissionIds).build();
    role.setId(id);
    ReflectionTestUtils.setField(role, "version", 0L);
    return role;
  }

  private RoleCreateRequest createRequest(String name, Set<String> permissions) {
    RoleCreateRequest req = new RoleCreateRequest();
    req.setName(name);
    req.setPermissions(permissions);
    return req;
  }

  private RoleUpdateRequest updateRequest(String name, Set<String> permissions, long version) {
    RoleUpdateRequest req = new RoleUpdateRequest();
    req.setName(name);
    req.setPermissions(permissions);
    req.setVersion(version);
    return req;
  }

  private RoleSummary summary(long id, String name, boolean systemRole, long permissionCount) {
    return new RoleSummary() {
      @Override
      public Long getId() {
        return id;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public Boolean getSystemRole() {
        return systemRole;
      }

      @Override
      public long getPermissionCount() {
        return permissionCount;
      }
    };
  }

  @Test
  void list_returnsSummariesWithoutTouchingPermissionCatalog() {
    when(roleRepository.findAllSummaries()).thenReturn(List.of(summary(1L, "受付", false, 2L)));

    List<RoleSummaryResponse> res = service.list();

    assertThat(res).hasSize(1);
    assertThat(res.get(0).name()).isEqualTo("受付");
    assertThat(res.get(0).system()).isFalse();
    assertThat(res.get(0).permissionCount()).isEqualTo(2L);
    // 一覧は件数集計だけで完結する — 権限目録の解決（編集時の詳細取得の仕事）が混ざらないこと
    verify(permissionRepository, never()).findAllById(any());
  }

  @Test
  void get_resolvesPermissionCodesSorted() {
    when(roleRepository.findById(1L))
        .thenReturn(
            Optional.of(role(1L, "受付", false, Set.of(ORDER_MANAGE_ID, CUSTOMER_MANAGE_ID))));
    when(permissionRepository.findAllById(Set.of(ORDER_MANAGE_ID, CUSTOMER_MANAGE_ID)))
        .thenReturn(
            List.of(
                permission(ORDER_MANAGE_ID, PermissionCode.ORDER_MANAGE),
                permission(CUSTOMER_MANAGE_ID, PermissionCode.CUSTOMER_MANAGE)));

    RoleResponse res = service.get(1L);

    assertThat(res.name()).isEqualTo("受付");
    assertThat(res.system()).isFalse();
    assertThat(res.permissions()).containsExactly("CUSTOMER_MANAGE", "ORDER_MANAGE");
  }

  @Test
  void get_unknownId_throwsNotFound() {
    when(roleRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(404L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void create_resolvesCodesToIdsAndSavesCustomRole() {
    when(permissionRepository.findByCodeIn(Set.of("ORDER_MANAGE")))
        .thenReturn(List.of(permission(ORDER_MANAGE_ID, PermissionCode.ORDER_MANAGE)));
    when(roleRepository.saveAndFlush(roleCaptor.capture()))
        .thenAnswer(
            i -> {
              Role saved = i.getArgument(0);
              saved.setId(7L);
              ReflectionTestUtils.setField(saved, "version", 0L);
              return saved;
            });

    RoleResponse res = service.create(createRequest("受付", Set.of("ORDER_MANAGE")));

    assertThat(roleCaptor.getValue().getName()).isEqualTo("受付");
    assertThat(roleCaptor.getValue().getSystemRole()).as("API 経由の作成は常に自作ロール").isFalse();
    assertThat(roleCaptor.getValue().getPermissionIds()).containsExactly(ORDER_MANAGE_ID);
    assertThat(res.id()).isEqualTo(7L);
    assertThat(res.permissions()).containsExactly("ORDER_MANAGE");
  }

  @Test
  void create_unknownPermissionCode_throwsWithoutSaving() {
    when(permissionRepository.findByCodeIn(Set.of("NOT_A_CODE"))).thenReturn(List.of());

    assertThatThrownBy(() -> service.create(createRequest("受付", Set.of("NOT_A_CODE"))))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("権限");

    verify(roleRepository, never()).saveAndFlush(any());
  }

  @Test
  void create_duplicateName_convertsUniqueViolationToServiceException() {
    when(permissionRepository.findByCodeIn(Set.of("ORDER_MANAGE")))
        .thenReturn(List.of(permission(ORDER_MANAGE_ID, PermissionCode.ORDER_MANAGE)));
    when(roleRepository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new ConstraintViolationException(
                    "save failed",
                    new SQLException("duplicate key value violates unique constraint"),
                    "uq_t_roles_name")));

    assertThatThrownBy(() -> service.create(createRequest("店長", Set.of("ORDER_MANAGE"))))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("ロール名");
  }

  @Test
  void update_renamesAndReplacesPermissions() {
    Role existing = role(7L, "受付", false, Set.of(ORDER_MANAGE_ID));
    when(permissionRepository.findByCodeIn(Set.of("CUSTOMER_MANAGE")))
        .thenReturn(List.of(permission(CUSTOMER_MANAGE_ID, PermissionCode.CUSTOMER_MANAGE)));
    when(roleRepository.findById(7L)).thenReturn(Optional.of(existing));
    when(roleRepository.saveAndFlush(existing)).thenReturn(existing);

    RoleResponse res = service.update(7L, updateRequest("受付リーダー", Set.of("CUSTOMER_MANAGE"), 0L));

    assertThat(existing.getName()).isEqualTo("受付リーダー");
    assertThat(existing.getPermissionIds()).containsExactly(CUSTOMER_MANAGE_ID);
    assertThat(res.permissions()).containsExactly("CUSTOMER_MANAGE");
  }

  @Test
  void update_unknownId_throwsNotFound() {
    when(permissionRepository.findByCodeIn(Set.of("ORDER_MANAGE")))
        .thenReturn(List.of(permission(ORDER_MANAGE_ID, PermissionCode.ORDER_MANAGE)));
    when(roleRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(404L, updateRequest("受付", Set.of("ORDER_MANAGE"), 0L)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void update_staleVersion_throwsConflictWithoutMutating() {
    Role existing = role(7L, "受付", false, Set.of(ORDER_MANAGE_ID));
    ReflectionTestUtils.setField(existing, "version", 3L);
    when(permissionRepository.findByCodeIn(Set.of("CUSTOMER_MANAGE")))
        .thenReturn(List.of(permission(CUSTOMER_MANAGE_ID, PermissionCode.CUSTOMER_MANAGE)));
    when(roleRepository.findById(7L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () -> service.update(7L, updateRequest("受付リーダー", Set.of("CUSTOMER_MANAGE"), 2L)))
        .isInstanceOf(StaleRoleUpdateException.class);

    assertThat(existing.getName()).isEqualTo("受付");
    verify(roleRepository, never()).saveAndFlush(any());
  }

  @Test
  void update_systemRole_throwsImmutable() {
    Role existing = role(1L, "HQ管理者", true, Set.of(ORDER_MANAGE_ID));
    when(permissionRepository.findByCodeIn(Set.of("CUSTOMER_MANAGE")))
        .thenReturn(List.of(permission(CUSTOMER_MANAGE_ID, PermissionCode.CUSTOMER_MANAGE)));
    when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.update(1L, updateRequest("別名", Set.of("CUSTOMER_MANAGE"), 0L)))
        .isInstanceOf(SystemRoleImmutableException.class);

    verify(roleRepository, never()).saveAndFlush(any());
  }

  @Test
  void delete_unknownId_throwsNotFound() {
    when(roleRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(404L)).isInstanceOf(NotFoundException.class);
    verify(roleRepository, never()).delete(any());
  }

  @Test
  void delete_customUnusedRole_deletes() {
    Role existing = role(7L, "受付", false, Set.of(ORDER_MANAGE_ID));
    when(roleRepository.findById(7L)).thenReturn(Optional.of(existing));
    when(platformUserRepository.existsByRoleId(7L)).thenReturn(false);

    service.delete(7L);

    verify(roleRepository).delete(existing);
  }

  @Test
  void delete_systemRole_throwsImmutableWithoutDeleting() {
    Role existing = role(1L, "HQ管理者", true, Set.of(ORDER_MANAGE_ID));
    when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(SystemRoleImmutableException.class);

    verify(roleRepository, never()).delete(any());
  }

  @Test
  void delete_roleInUse_throwsConflictWithoutDeleting() {
    Role existing = role(7L, "受付", false, Set.of(ORDER_MANAGE_ID));
    when(roleRepository.findById(7L)).thenReturn(Optional.of(existing));
    when(platformUserRepository.existsByRoleId(7L)).thenReturn(true);

    assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(RoleInUseException.class);

    verify(roleRepository, never()).delete(any());
  }

  @Test
  void delete_grantedBetweenCheckAndFlush_convertsRestrictViolationToConflict() {
    // 事前検証を通過した後に授与が割り込む競合。DB の RESTRICT 違反も事前検証と同じ 409 へ揃える。
    Role existing = role(7L, "受付", false, Set.of(ORDER_MANAGE_ID));
    when(roleRepository.findById(7L)).thenReturn(Optional.of(existing));
    when(platformUserRepository.existsByRoleId(7L)).thenReturn(false);
    doThrow(
            new DataIntegrityViolationException(
                "delete failed",
                new ConstraintViolationException(
                    "delete failed",
                    new SQLException("update or delete violates foreign key constraint"),
                    "fk_t_user_roles_role")))
        .when(roleRepository)
        .flush();

    assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(RoleInUseException.class);
  }

  @Test
  void delete_propagatesUnrelatedDataIntegrityViolation() {
    // 授与 FK（RESTRICT）以外の整合性違反はサービスで握りつぶさず、そのまま伝播すること。
    // 無条件に 409 へ変換すると想定外の実装欠陥まで「使用中」に化けてしまう。
    Role existing = role(7L, "受付", false, Set.of(ORDER_MANAGE_ID));
    when(roleRepository.findById(7L)).thenReturn(Optional.of(existing));
    when(platformUserRepository.existsByRoleId(7L)).thenReturn(false);
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException(
            "delete failed",
            new ConstraintViolationException(
                "delete failed",
                new SQLException("new row violates check constraint"),
                "ck_t_roles_name_not_blank"));
    doThrow(violation).when(roleRepository).flush();

    assertThatThrownBy(() -> service.delete(7L)).isSameAs(violation);
  }

  @Test
  void create_propagatesUnrelatedDataIntegrityViolation() {
    // ロール名一意制約以外の整合性違反はサービスで握りつぶさず、そのまま伝播すること。
    // 一意違反→409 / それ以外→500 の分類は CommonExceptionHandler が SQLSTATE で行うため、
    // サービス層で 400 に変換すると想定外の実装欠陥まで「権限が存在しません」に化けてしまう。
    when(permissionRepository.findByCodeIn(Set.of("ORDER_MANAGE")))
        .thenReturn(List.of(permission(ORDER_MANAGE_ID, PermissionCode.ORDER_MANAGE)));
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException(
            "save failed",
            new ConstraintViolationException(
                "save failed",
                new SQLException("null value violates not-null constraint"),
                "name"));
    when(roleRepository.saveAndFlush(any())).thenThrow(violation);

    assertThatThrownBy(() -> service.create(createRequest("店長", Set.of("ORDER_MANAGE"))))
        .isSameAs(violation);
  }
}
