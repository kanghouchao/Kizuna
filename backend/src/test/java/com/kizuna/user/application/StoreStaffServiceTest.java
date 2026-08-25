package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.StoreStaffCreateRequest;
import com.kizuna.user.api.dto.StoreStaffResponse;
import com.kizuna.user.api.dto.StoreStaffUpdateRequest;
import com.kizuna.user.domain.InvalidRoleGrantException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserStopped;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.RoleSummary;
import com.kizuna.user.domain.StaffOutOfDelegationScopeException;
import com.kizuna.user.domain.StaleStaffUpdateException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 店舗スタッフ管理の防提権守衛（ADR 0020 の G1〜G3）を固定する。行使者の事実は JWT（authorities と授権店舗集合）、対象の現況は DB という分担で、 ここでは JWT
 * 側を組んで境界の内外を撃ち分ける。
 */
@ExtendWith(MockitoExtension.class)
class StoreStaffServiceTest {

  /** HQ 側ロール（Console.PLATFORM の権限を含む）。店舗スタッフ管理では対象にも付与先にもならない。 */
  private static final long HQ_ROLE = 10L;

  /** 委譲権限 STAFF_MANAGE を含む店舗側ロール（店長相当）。再委譲の禁止（G1）と G3 の両方に効く。 */
  private static final long MANAGER_ROLE = 11L;

  /** 委譲権限を含まない店舗側ロール。店長が付与できる唯一の種類。 */
  private static final long CLERK_ROLE = 12L;

  private static final long CONTEXT_STORE = 1L;
  private static final long OTHER_STORE = 2L;

  private static final Pageable PAGEABLE = PageRequest.of(0, 20);

  /** 作成要求へ載せる素の値。符号化して保存する経路を通すためだけの固定値で、意味は持たない。 */
  private static final String RAW_CREDENTIAL = "rawpass";

  @Mock private PlatformUserRepository repository;
  @Mock private RoleRepository roleRepository;
  @Mock private PasswordEncoder encoder;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private StoreContext storeContext;

  @InjectMocks private StoreStaffService service;

  @Captor private ArgumentCaptor<PlatformUser> userCaptor;

  @AfterEach
  void clearActor() {
    SecurityContextHolder.clearContext();
  }

  /** 店長（STAFF_MANAGE のみ・特定店舗担当）を行使者に据える。 */
  private void givenManagerActor(Long... storeIds) {
    givenActor(false, "SPECIFIC_STORES", List.of(storeIds));
  }

  /** HQ（ROLE_MANAGE 保持・全店舗担当）を行使者に据える。 */
  private void givenHqActor() {
    givenActor(true, "ALL_STORES", null);
  }

  private void givenActor(boolean roleManage, String scopeType, List<Long> storeIds) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("actor@kizuna.test")
            .claim("storeScopeType", scopeType);
    if (storeIds != null) {
      builder.claim("storeIds", storeIds);
    }
    List<SimpleGrantedAuthority> authorities =
        roleManage
            ? List.of(
                new SimpleGrantedAuthority(PermissionCode.STAFF_MANAGE.authority()),
                new SimpleGrantedAuthority(PermissionCode.ROLE_MANAGE.authority()))
            : List.of(new SimpleGrantedAuthority(PermissionCode.STAFF_MANAGE.authority()));
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(builder.build(), authorities));
  }

  /** HQ 側ロールの解決を差し込む。既定では HQ_ROLE だけが HQ 側。 */
  private void givenHqSideRoles() {
    when(roleRepository.findIdsByPermissionCodeIn(ArgumentMatchers.anyCollection()))
        .thenReturn(Set.of(HQ_ROLE));
  }

  /** 委譲権限を含むロールの解決を差し込む。 */
  private void givenStaffManageRoles() {
    when(roleRepository.findIdsByPermissionCode(PermissionCode.STAFF_MANAGE.name()))
        .thenReturn(Set.of(MANAGER_ROLE));
  }

  private Role role(long id, String name) {
    Role role = Role.builder().name(name).permissionIds(Set.of(1L)).build();
    role.setId(id);
    return role;
  }

  private void givenRoleNames() {
    when(roleRepository.findAllById(ArgumentMatchers.anyCollection()))
        .thenAnswer(
            invocation -> {
              java.util.Collection<?> ids = invocation.getArgument(0);
              return ids.stream()
                  .map(id -> role((Long) id, "ロール" + id))
                  .map(Role.class::cast)
                  .toList();
            });
  }

  private PlatformUser staff(
      long id, String email, Set<Long> roleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    PlatformUser user =
        PlatformUser.builder()
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

  private StoreStaffCreateRequest createRequest(
      Set<Long> roleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    StoreStaffCreateRequest req = new StoreStaffCreateRequest();
    req.setEmail("new@kizuna.test");
    req.setPassword(RAW_CREDENTIAL);
    req.setDisplayName("表示名");
    req.setRoleIds(roleIds);
    req.setStoreScopeType(scopeType);
    req.setStoreIds(storeIds);
    return req;
  }

  private StoreStaffUpdateRequest updateRequest(
      Set<Long> roleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    StoreStaffUpdateRequest req = new StoreStaffUpdateRequest();
    req.setRoleIds(roleIds);
    req.setStoreScopeType(scopeType);
    req.setStoreIds(storeIds);
    req.setVersion(0L);
    return req;
  }

  /** 店舗文脈（X-Store-ID で確立済みの店）。一覧と同じ可視性の述語が詳細・編集にも掛かる。 */
  private void givenContextStore() {
    when(storeContext.getStoreId()).thenReturn(CONTEXT_STORE);
  }

  private void givenListReturns(PlatformUser... users) {
    when(storeContext.getStoreId()).thenReturn(CONTEXT_STORE);
    when(repository.findAll(ArgumentMatchers.<Specification<PlatformUser>>any(), eq(PAGEABLE)))
        .thenReturn(new PageImpl<>(List.of(users), PAGEABLE, users.length));
  }

  @Test
  @DisplayName("一覧: 委譲権限の実効保持者は表示されるが editable=false になること（G3）")
  void listShowsStaffManageHolderAsReadOnly() {
    givenListReturns(
        staff(
            1L,
            "clerk@kizuna.test",
            Set.of(CLERK_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(CONTEXT_STORE)),
        staff(
            2L,
            "mgr@kizuna.test",
            Set.of(MANAGER_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(CONTEXT_STORE)));
    givenHqSideRoles();
    givenStaffManageRoles();
    givenRoleNames();
    givenManagerActor(CONTEXT_STORE);

    List<StoreStaffResponse> rows = service.list(null, PAGEABLE).getContent();

    assertThat(rows).extracting(StoreStaffResponse::id).containsExactly(1L, 2L);
    assertThat(rows).extracting(StoreStaffResponse::editable).containsExactly(true, false);
  }

  @Test
  @DisplayName("一覧: 担当外店舗を含む対象は editable=false になること（G3 の店舗集合境界）")
  void listMarksStaffReachingOutsideActorStoresAsReadOnly() {
    givenListReturns(
        staff(
            3L,
            "cross@kizuna.test",
            Set.of(CLERK_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(CONTEXT_STORE, OTHER_STORE)),
        staff(4L, "all@kizuna.test", Set.of(CLERK_ROLE), StoreScopeType.ALL_STORES, Set.of()));
    givenHqSideRoles();
    givenStaffManageRoles();
    givenRoleNames();
    givenManagerActor(CONTEXT_STORE);

    List<StoreStaffResponse> rows = service.list(null, PAGEABLE).getContent();

    assertThat(rows).extracting(StoreStaffResponse::editable).containsExactly(false, false);
  }

  @Test
  @DisplayName("一覧: ROLE_MANAGE 保持者には全行が editable=true になること")
  void listGivesRoleManageActorFullEditRights() {
    givenListReturns(
        staff(
            2L,
            "mgr@kizuna.test",
            Set.of(MANAGER_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(CONTEXT_STORE)),
        staff(4L, "all@kizuna.test", Set.of(CLERK_ROLE), StoreScopeType.ALL_STORES, Set.of()));
    givenHqSideRoles();
    givenRoleNames();
    givenHqActor();

    List<StoreStaffResponse> rows = service.list(null, PAGEABLE).getContent();

    assertThat(rows).extracting(StoreStaffResponse::editable).containsExactly(true, true);
  }

  @Test
  @DisplayName("作成: HQ 側ロールの付与は行使者を問わず拒否されること（母集団の維持）")
  void createRejectsHqSideRoleEvenForRoleManageActor() {
    givenHqSideRoles();
    givenHqActor();

    assertThatThrownBy(
            () ->
                service.create(createRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of())))
        .isInstanceOf(InvalidRoleGrantException.class);
    verify(repository, never()).saveAndFlush(ArgumentMatchers.any());
  }

  @Test
  @DisplayName("作成: 委譲権限を含むロールの付与は店長には拒否されること（G1 再委譲の禁止）")
  void createRejectsStaffManageRoleForDelegatedActor() {
    givenHqSideRoles();
    givenStaffManageRoles();
    givenManagerActor(CONTEXT_STORE);

    assertThatThrownBy(
            () ->
                service.create(
                    createRequest(
                        Set.of(MANAGER_ROLE),
                        StoreScopeType.SPECIFIC_STORES,
                        Set.of(CONTEXT_STORE))))
        .isInstanceOf(InvalidRoleGrantException.class);
  }

  @Test
  @DisplayName("作成: 委譲権限を含むロールの付与は ROLE_MANAGE 保持者には通ること")
  void createAllowsStaffManageRoleForRoleManageActor() {
    givenHqSideRoles();
    givenRoleNames();
    givenHqActor();
    when(encoder.encode(RAW_CREDENTIAL)).thenReturn("hashed");
    when(repository.findByEmail("new@kizuna.test")).thenReturn(Optional.empty());
    when(repository.saveAndFlush(userCaptor.capture()))
        .thenAnswer(StoreStaffServiceTest::persisted);

    StoreStaffResponse res =
        service.create(
            createRequest(
                Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(OTHER_STORE)));

    assertThat(userCaptor.getValue().getRoleIds()).containsExactly(MANAGER_ROLE);
    assertThat(res.editable()).as("ROLE_MANAGE 保持者には守衛が課されないこと").isTrue();
  }

  @Test
  @DisplayName("作成: 担当外店舗を含む店舗集合の指定が拒否されること（G2）")
  void createRejectsStoresOutsideActorScope() {
    givenHqSideRoles();
    givenStaffManageRoles();
    givenManagerActor(CONTEXT_STORE);

    assertThatThrownBy(
            () ->
                service.create(
                    createRequest(
                        Set.of(CLERK_ROLE),
                        StoreScopeType.SPECIFIC_STORES,
                        Set.of(CONTEXT_STORE, OTHER_STORE))))
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("作成: 全店舗担当の付与は全店舗担当の行使者だけができること（G2）")
  void createRejectsAllStoresGrantFromSpecificStoresActor() {
    givenHqSideRoles();
    givenStaffManageRoles();
    givenManagerActor(CONTEXT_STORE);

    assertThatThrownBy(
            () ->
                service.create(
                    createRequest(Set.of(CLERK_ROLE), StoreScopeType.ALL_STORES, Set.of())))
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("編集: 委譲権限の実効保持者は店長には編集できないこと（G3）")
  void updateRejectsTargetHoldingStaffManage() {
    when(repository.findById(2L))
        .thenReturn(
            Optional.of(
                staff(
                    2L,
                    "mgr@kizuna.test",
                    Set.of(MANAGER_ROLE),
                    StoreScopeType.SPECIFIC_STORES,
                    Set.of(CONTEXT_STORE))));
    givenHqSideRoles();
    givenStaffManageRoles();
    givenContextStore();
    givenManagerActor(CONTEXT_STORE);

    assertThatThrownBy(
            () ->
                service.update(
                    2L,
                    updateRequest(
                        Set.of(CLERK_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(CONTEXT_STORE))))
        .isInstanceOf(StaffOutOfDelegationScopeException.class);
  }

  @Test
  @DisplayName("編集: 担当外店舗を含む対象は店長には編集できないこと（他人の授権を壊さないための境界）")
  void updateRejectsTargetReachingOutsideActorStores() {
    when(repository.findById(3L))
        .thenReturn(
            Optional.of(
                staff(
                    3L,
                    "cross@kizuna.test",
                    Set.of(CLERK_ROLE),
                    StoreScopeType.SPECIFIC_STORES,
                    Set.of(CONTEXT_STORE, OTHER_STORE))));
    givenHqSideRoles();
    givenStaffManageRoles();
    givenContextStore();
    givenManagerActor(CONTEXT_STORE);

    assertThatThrownBy(
            () ->
                service.update(
                    3L,
                    updateRequest(
                        Set.of(CLERK_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(CONTEXT_STORE))))
        .isInstanceOf(StaffOutOfDelegationScopeException.class);
  }

  @Test
  @DisplayName("取得: HQ 側ロール保持者は在否を漏らさず 404 になること")
  void getHidesHqSideRoleHolder() {
    when(repository.findById(9L))
        .thenReturn(
            Optional.of(
                staff(9L, "hq@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of())));
    givenHqSideRoles();
    givenHqActor();

    assertThatThrownBy(() -> service.get(9L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("取得: 店舗文脈の外にいる対象は 404 になること（一覧と同じ可視性の述語）")
  void getHidesStaffOutsideTheContextStore() {
    when(repository.findById(7L))
        .thenReturn(
            Optional.of(
                staff(
                    7L,
                    "elsewhere@kizuna.test",
                    Set.of(CLERK_ROLE),
                    StoreScopeType.SPECIFIC_STORES,
                    Set.of(OTHER_STORE))));
    givenHqSideRoles();
    givenContextStore();
    givenManagerActor(CONTEXT_STORE, OTHER_STORE);

    assertThatThrownBy(() -> service.get(7L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("編集: 陳腐化した版の提出は 409 になること")
  void updateRejectsStaleVersion() {
    PlatformUser target =
        staff(
            1L,
            "clerk@kizuna.test",
            Set.of(CLERK_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(CONTEXT_STORE));
    ReflectionTestUtils.setField(target, "version", 3L);
    when(repository.findById(1L)).thenReturn(Optional.of(target));
    givenHqSideRoles();
    givenStaffManageRoles();
    givenContextStore();
    givenManagerActor(CONTEXT_STORE);

    assertThatThrownBy(
            () ->
                service.update(
                    1L,
                    updateRequest(
                        Set.of(CLERK_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(CONTEXT_STORE))))
        .isInstanceOf(StaleStaffUpdateException.class);
  }

  @Test
  @DisplayName("編集: 停止は失効イベントを発行すること")
  void updatePublishesStoppedEventOnDisable() {
    PlatformUser target =
        staff(
            1L,
            "clerk@kizuna.test",
            Set.of(CLERK_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(CONTEXT_STORE));
    when(repository.findById(1L)).thenReturn(Optional.of(target));
    when(repository.saveAndFlush(ArgumentMatchers.any()))
        .thenAnswer(StoreStaffServiceTest::persisted);
    givenHqSideRoles();
    givenStaffManageRoles();
    givenRoleNames();
    givenContextStore();
    givenManagerActor(CONTEXT_STORE);

    StoreStaffUpdateRequest req =
        updateRequest(Set.of(CLERK_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(CONTEXT_STORE));
    req.setEnabled(false);
    service.update(1L, req);

    assertThat(target.getEnabled()).isFalse();
    verify(eventPublisher).publishEvent(new PlatformUserStopped("clerk@kizuna.test"));
  }

  @Test
  @DisplayName("可授ロール: 店長には HQ 側ロールと委譲権限を含むロールが現れないこと（G1 の単源）")
  void grantableRolesHideHqSideAndStaffManageFromDelegatedActor() {
    when(roleRepository.findAllSummaries())
        .thenReturn(
            List.of(
                summary(HQ_ROLE, "HQ管理者"),
                summary(MANAGER_ROLE, "店長"),
                summary(CLERK_ROLE, "店舗スタッフ")));
    givenHqSideRoles();
    givenStaffManageRoles();
    givenManagerActor(CONTEXT_STORE);

    assertThat(service.grantableRoles())
        .extracting(RoleSummaryResponse::id)
        .containsExactly(CLERK_ROLE);
  }

  @Test
  @DisplayName("可授ロール: ROLE_MANAGE 保持者には委譲権限を含む店舗側ロールも現れること")
  void grantableRolesKeepStaffManageForRoleManageActor() {
    when(roleRepository.findAllSummaries())
        .thenReturn(
            List.of(
                summary(HQ_ROLE, "HQ管理者"),
                summary(MANAGER_ROLE, "店長"),
                summary(CLERK_ROLE, "店舗スタッフ")));
    givenHqSideRoles();
    givenHqActor();

    assertThat(service.grantableRoles())
        .extracting(RoleSummaryResponse::id)
        .containsExactly(MANAGER_ROLE, CLERK_ROLE);
  }

  /** 永続化を模す（DB が採番する id と、flush で初期化される version 列を埋める）。 */
  private static PlatformUser persisted(org.mockito.invocation.InvocationOnMock invocation) {
    PlatformUser saved = invocation.getArgument(0);
    if (saved.getId() == null) {
      saved.setId(99L);
    }
    if (saved.getVersion() == null) {
      ReflectionTestUtils.setField(saved, "version", 0L);
    }
    return saved;
  }

  private static RoleSummary summary(long id, String name) {
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
        return true;
      }

      @Override
      public long getPermissionCount() {
        return 1L;
      }
    };
  }
}
