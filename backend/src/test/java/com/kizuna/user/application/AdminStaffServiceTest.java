package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.api.dto.AdminStaffCreateRequest;
import com.kizuna.user.api.dto.AdminStaffResponse;
import com.kizuna.user.api.dto.AdminStaffUpdateRequest;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.HqPasswordResetNotAllowedException;
import com.kizuna.user.domain.InvalidRoleGrantException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.SelfPasswordResetNotAllowedException;
import com.kizuna.user.domain.SelfStopNotAllowedException;
import com.kizuna.user.domain.StaffOutOfDelegationScopeException;
import com.kizuna.user.domain.StaleStaffUpdateException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserCredentialsChanged;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** 委譲守衛（権限並集と店舗集合の部分集合）と名指しの守衛（自己停止・自己再設定・管理者の再設定）の単体テスト。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminStaffServiceTest {

  private static final String ACTOR = "actor@kizuna.test";
  private static final Pageable PAGEABLE = PageRequest.of(0, 20);

  /** 権限 id。ADMIN_ROLE は {1,2,3,ROLE_MANAGE}、MANAGER_ROLE は {1,2}、STAFF_ROLE は {1}。 */
  private static final long ROLE_MANAGE_PERMISSION = 99L;

  private static final long ADMIN_ROLE = 10L;
  private static final long MANAGER_ROLE = 11L;
  private static final long STAFF_ROLE = 12L;

  @Mock private BusinessAudit businessAudit;
  @Mock private UserRepository repository;
  @Mock private RoleRepository roleRepository;
  @Mock private RoleManageHolderGuard roleManageHolderGuard;
  @Mock private PasswordEncoder encoder;
  private final List<Object> events = new ArrayList<>();
  @Spy private CredentialOperations credentialOperations = new CredentialOperations(events::add);
  private final StoreScopeHolder holder = new StoreScopeHolder();

  private AdminStaffService service;

  @Captor private ArgumentCaptor<User> userCaptor;

  @BeforeEach
  void setUp() {
    service =
        new AdminStaffService(
            businessAudit,
            repository,
            roleRepository,
            roleManageHolderGuard,
            encoder,
            credentialOperations,
            holder);
    when(roleRepository.findAllById(anyCollection()))
        .thenAnswer(
            invocation -> {
              Collection<Long> ids = invocation.getArgument(0);
              return roles().stream().filter(role -> ids.contains(role.getId())).toList();
            });
    when(roleRepository.findAll()).thenReturn(roles());
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(ADMIN_ROLE));
    when(repository.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              User saved = invocation.getArgument(0);
              if (saved.getId() == null) saved.setId(9L);
              ReflectionTestUtils.setField(saved, "version", 0L);
              return saved;
            });
  }

  @AfterEach
  void tearDown() {
    holder.clear();
  }

  private static Role role(long id, String name, Set<Long> permissionIds) {
    Role role = Role.builder().name(name).systemRole(true).permissionIds(permissionIds).build();
    role.setId(id);
    return role;
  }

  private static List<Role> roles() {
    return List.of(
        role(ADMIN_ROLE, "統括", Set.of(1L, 2L, 3L, ROLE_MANAGE_PERMISSION)),
        role(MANAGER_ROLE, "店長", Set.of(1L, 2L)),
        role(STAFF_ROLE, "スタッフ", Set.of(1L)));
  }

  private static User staff(
      long id, String email, Set<Long> roleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    User user =
        User.builder()
            .email(email)
            .password("hash")
            .displayName("表示名")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(roleIds)
            .storeScopeType(scopeType)
            .storeIds(storeIds)
            .build();
    user.setId(id);
    ReflectionTestUtils.setField(user, "version", 0L);
    return user;
  }

  /** 行使者を差し込む。店舗集合は保持器（要求ごとの解決器）に、権限はロールから。 */
  private User actor(Set<Long> roleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    User actor = staff(1L, ACTOR, roleIds, scopeType, storeIds);
    when(repository.findByEmail(ACTOR)).thenReturn(Optional.of(actor));
    holder.install(
        () ->
            scopeType == StoreScopeType.ALL_STORES
                ? new StoreScope(true, Set.of())
                : new StoreScope(false, storeIds));
    return actor;
  }

  private User manager(Set<Long> stores) {
    return actor(Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, stores);
  }

  private User target(long id, Set<Long> roleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    User target = staff(id, "target" + id + "@kizuna.test", roleIds, scopeType, storeIds);
    when(repository.findById(id)).thenReturn(Optional.of(target));
    return target;
  }

  private static AdminStaffCreateRequest createRequest(
      Set<Long> roleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    AdminStaffCreateRequest req = new AdminStaffCreateRequest();
    req.setEmail("new@kizuna.test");
    req.setPassword("rawpass");
    req.setDisplayName("表示名");
    req.setRoleIds(roleIds);
    req.setStoreScopeType(scopeType);
    req.setStoreIds(storeIds);
    return req;
  }

  private static AdminStaffUpdateRequest updateRequest(
      Set<Long> roleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    AdminStaffUpdateRequest req = new AdminStaffUpdateRequest();
    req.setRoleIds(roleIds);
    req.setStoreScopeType(scopeType);
    req.setStoreIds(storeIds);
    req.setVersion(0L);
    return req;
  }

  @Test
  @DisplayName("作用域を解決できない行使者は fail-closed に 403")
  void unresolvedActorScopeIsDenied() {
    assertThatThrownBy(() -> service.list(null, null, PAGEABLE, ACTOR))
        .isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).findAll(ArgumentMatchers.<Specification<User>>any(), eq(PAGEABLE));
  }

  @Test
  @DisplayName("一覧は行使者の店舗集合に交わる者を返し、行ごとの editable は部分集合判定で決まる")
  void listMarksEditableBySubsetOfPermissionsAndStores() {
    manager(Set.of(1L));
    User peer =
        staff(
            2L,
            "peer@kizuna.test",
            Set.of(MANAGER_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    User admin =
        staff(3L, "admin@kizuna.test", Set.of(ADMIN_ROLE), StoreScopeType.ALL_STORES, Set.of());
    User twoStores =
        staff(
            4L,
            "two@kizuna.test",
            Set.of(STAFF_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L, 2L));
    when(repository.findAll(ArgumentMatchers.<Specification<User>>any(), eq(PAGEABLE)))
        .thenReturn(new PageImpl<>(List.of(peer, admin, twoStores), PAGEABLE, 3));

    var page = service.list(null, null, PAGEABLE, ACTOR);

    assertThat(page.getContent())
        .extracting(AdminStaffResponse::editable)
        .containsExactly(true, false, false);
    assertThat(page.getContent().get(1).roles())
        .containsExactly(new AdminStaffResponse.RoleRef(ADMIN_ROLE, "統括"));
  }

  @Test
  @DisplayName("付与できるロールは権限集合が行使者の権限並集に収まるものだけ")
  void grantableRolesAreSubsetsOfActorPermissions() {
    manager(Set.of(1L));

    List<RoleSummaryResponse> roles = service.grantableRoles(ACTOR);

    assertThat(roles)
        .extracting(RoleSummaryResponse::id)
        .containsExactlyInAnyOrder(MANAGER_ROLE, STAFF_ROLE);
  }

  @Test
  @DisplayName("作成: 行使者が持たない権限を含むロールは付与できない")
  void createRejectsRoleBeyondActorPermissions() {
    manager(Set.of(1L));

    assertThatThrownBy(
            () ->
                service.create(
                    createRequest(Set.of(ADMIN_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
                    ACTOR))
        .isInstanceOf(InvalidRoleGrantException.class);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("作成: 行使者の店舗集合を越える店舗（全店舗を含む）は付与できない")
  void createRejectsStoresBeyondActorScope() {
    manager(Set.of(1L));

    assertThatThrownBy(
            () ->
                service.create(
                    createRequest(
                        Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L, 2L)),
                    ACTOR))
        .isInstanceOf(InvalidStoreScopeException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    createRequest(Set.of(STAFF_ROLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR))
        .isInstanceOf(InvalidStoreScopeException.class);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("作成: 自分と同じロール（同格）は配れる — 同格の相互保護は置かない")
  void createAllowsGrantingActorsOwnRole() {
    manager(Set.of(1L));
    when(repository.findByEmail("new@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");

    AdminStaffResponse res =
        service.create(
            createRequest(Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)), ACTOR);

    var order = inOrder(roleRepository, repository);
    order.verify(roleRepository).lockIdsForGrant(Set.of(MANAGER_ROLE));
    order.verify(repository).saveAndFlush(userCaptor.capture());
    assertThat(userCaptor.getValue().getPassword()).isEqualTo("ENCODED");
    assertThat(userCaptor.getValue().getRoleIds()).containsExactly(MANAGER_ROLE);
    assertThat(res.editable()).isTrue();
    assertThat(res.id()).isEqualTo(9L);
  }

  @Test
  @DisplayName("作成: ALL_STORES の行使者は全店舗の対象も自分の全権限も配れる")
  void allStoresActorCanGrantEverythingItHolds() {
    actor(Set.of(ADMIN_ROLE), StoreScopeType.ALL_STORES, Set.of());
    when(repository.findByEmail("new@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");

    AdminStaffResponse res =
        service.create(
            createRequest(Set.of(ADMIN_ROLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR);

    assertThat(res.storeScopeType()).isEqualTo(StoreScopeType.ALL_STORES);
    assertThat(res.editable()).isTrue();
  }

  @Test
  @DisplayName("ROLE_MANAGE 保持者は自分が持たない権限のロールも配れ、付与可能ロールは全件になる")
  void roleManageHolderIsExemptFromThePermissionSubsetRule() {
    // 権限 {ROLE_MANAGE} だけのロールで、店舗業務の権限 {1,2} を含む MANAGER_ROLE は持たない。
    Role definer = role(13L, "ロール定義者", Set.of(ROLE_MANAGE_PERMISSION));
    when(roleRepository.findAllById(anyCollection()))
        .thenAnswer(
            invocation -> {
              Collection<Long> ids = invocation.getArgument(0);
              return Stream.concat(roles().stream(), Stream.of(definer))
                  .filter(role -> ids.contains(role.getId()))
                  .toList();
            });
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(ADMIN_ROLE, 13L));
    actor(Set.of(13L), StoreScopeType.ALL_STORES, Set.of());
    when(repository.findByEmail("new@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");

    assertThat(service.grantableRoles(ACTOR))
        .extracting(RoleSummaryResponse::id)
        .containsExactlyInAnyOrder(ADMIN_ROLE, MANAGER_ROLE, STAFF_ROLE);
    AdminStaffResponse res =
        service.create(
            createRequest(Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)), ACTOR);
    assertThat(res.roles())
        .extracting(AdminStaffResponse.RoleRef::id)
        .containsExactly(MANAGER_ROLE);
    assertThat(res.editable()).isTrue();
  }

  @Test
  @DisplayName("ROLE_MANAGE 保持者でも店舗集合の部分集合は免れない")
  void roleManageHolderIsStillBoundByTheStoreSet() {
    actor(Set.of(ADMIN_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L));

    assertThatThrownBy(
            () ->
                service.create(
                    createRequest(
                        Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L, 2L)),
                    ACTOR))
        .isInstanceOf(InvalidStoreScopeException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    createRequest(Set.of(STAFF_ROLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR))
        .isInstanceOf(InvalidStoreScopeException.class);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("作成: 重複メールと不存在ロールは 400")
  void createRejectsDuplicateEmailAndUnknownRole() {
    manager(Set.of(1L));
    when(repository.findByEmail("new@kizuna.test"))
        .thenReturn(
            Optional.of(
                staff(
                    5L,
                    "new@kizuna.test",
                    Set.of(STAFF_ROLE),
                    StoreScopeType.SPECIFIC_STORES,
                    Set.of(1L))));
    assertThatThrownBy(
            () ->
                service.create(
                    createRequest(Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
                    ACTOR))
        .isInstanceOf(DuplicateStaffEmailException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    createRequest(Set.of(999L), StoreScopeType.SPECIFIC_STORES, Set.of(1L)), ACTOR))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("ロール");
  }

  @Test
  @DisplayName("取得・更新: 行使者の店舗集合と交わらない対象は存在しても 404（不在と区別させない）")
  void invisibleTargetIsNotFound() {
    manager(Set.of(1L));
    target(2L, Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(2L));

    assertThatThrownBy(() -> service.get(2L, ACTOR)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.suspend(2L, ACTOR)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.resetPassword(2L, ACTOR))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("更新: 見えるが権限か店舗集合が行使者を超える対象は 400（委譲の境界）")
  void updateRejectsTargetBeyondDelegation() {
    manager(Set.of(1L));
    target(2L, Set.of(ADMIN_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    target(3L, Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L, 2L));

    for (long id : List.of(2L, 3L)) {
      assertThatThrownBy(
              () ->
                  service.update(
                      id,
                      updateRequest(Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
                      ACTOR))
          .isInstanceOf(StaffOutOfDelegationScopeException.class);
      assertThatThrownBy(() -> service.suspend(id, ACTOR))
          .isInstanceOf(StaffOutOfDelegationScopeException.class);
      assertThatThrownBy(() -> service.resume(id, ACTOR))
          .isInstanceOf(StaffOutOfDelegationScopeException.class);
    }
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("更新: 版の不一致は 409、通れば授権を書き換えて監査する")
  void updateChecksVersionAndReassigns() {
    manager(Set.of(1L, 2L));
    User target = target(2L, Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L));

    AdminStaffUpdateRequest stale =
        updateRequest(Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    stale.setVersion(5L);
    assertThatThrownBy(() -> service.update(2L, stale, ACTOR))
        .isInstanceOf(StaleStaffUpdateException.class);

    service.update(
        2L,
        updateRequest(Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L, 2L)),
        ACTOR);

    assertThat(target.getRoleIds()).containsExactly(MANAGER_ROLE);
    assertThat(target.getStoreIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(target.getCredentialVersion()).as("ロール集合の変更で再ログインさせる").isEqualTo(1L);
    assertThat(events).containsExactly(new UserCredentialsChanged(2L, 1L));
    verify(businessAudit)
        .recordCurrent(
            eq(null), eq("STAFF_GRANTS_CHANGED"), eq("USER_GRANTS"), eq("2"), any(), any());
  }

  @Test
  @DisplayName("更新: ロールの除去は ROLE_MANAGE 保持者をゼロにしない守衛を通す")
  void updateRunsRoleManageHolderGuardWhenRemovingRoles() {
    actor(Set.of(ADMIN_ROLE), StoreScopeType.ALL_STORES, Set.of());
    User target = target(2L, Set.of(ADMIN_ROLE), StoreScopeType.ALL_STORES, Set.of());

    service.update(
        2L, updateRequest(Set.of(MANAGER_ROLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR);

    var order = inOrder(roleRepository, roleManageHolderGuard, repository);
    order.verify(roleRepository).lockIdsForGrant(Set.of(MANAGER_ROLE));
    order.verify(roleManageHolderGuard).requireAfterGrantChange(target, Set.of(MANAGER_ROLE));
    order.verify(repository).saveAndFlush(target);
  }

  @Test
  @DisplayName("停止: 自分自身は拒否、対象は直列化点越しに読み直して停止し監査する")
  void suspendRefusesSelfAndStopsOthers() {
    User me = actor(Set.of(ADMIN_ROLE), StoreScopeType.ALL_STORES, Set.of());
    when(repository.findById(1L)).thenReturn(Optional.of(me));
    assertThatThrownBy(() -> service.suspend(1L, ACTOR))
        .isInstanceOf(SelfStopNotAllowedException.class);

    User target = target(2L, Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    when(roleManageHolderGuard.loadForSuspension(2L)).thenReturn(target);

    service.suspend(2L, ACTOR);

    verify(credentialOperations).stop(target);
    verify(businessAudit)
        .record(
            eq(ACTOR),
            eq(null),
            eq("STAFF_ACCOUNT_SUSPENDED"),
            eq("USER_GRANTS"),
            eq("2"),
            eq(null),
            eq(null),
            any(),
            any());
  }

  @Test
  @DisplayName("再開: 停止中なら再開して監査、有効なら何もしない（冪等）")
  void resumeIsIdempotent() {
    manager(Set.of(1L));
    User target = target(2L, Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    target.stop();

    service.resume(2L, ACTOR);
    service.resume(2L, ACTOR);

    assertThat(target.getEnabled()).isTrue();
    verify(repository).saveAndFlush(target);
  }

  @Test
  @DisplayName("再設定: 自分自身と管理者（ROLE_MANAGE 保持者）は拒否、通れば仮パスワードを返す")
  void resetPasswordGuards() {
    User me = actor(Set.of(ADMIN_ROLE), StoreScopeType.ALL_STORES, Set.of());
    when(repository.findById(1L)).thenReturn(Optional.of(me));
    assertThatThrownBy(() -> service.resetPassword(1L, ACTOR))
        .isInstanceOf(SelfPasswordResetNotAllowedException.class);

    target(2L, Set.of(ADMIN_ROLE), StoreScopeType.ALL_STORES, Set.of());
    assertThatThrownBy(() -> service.resetPassword(2L, ACTOR))
        .isInstanceOf(HqPasswordResetNotAllowedException.class);

    User target = target(3L, Set.of(STAFF_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    when(encoder.encode(any())).thenReturn("ENCODED");

    String temporary = service.resetPassword(3L, ACTOR);

    assertThat(temporary).hasSize(16);
    verify(credentialOperations).changePassword(target, "ENCODED");
  }
}
