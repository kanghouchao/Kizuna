package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.InvalidRoleGrantException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.LastRoleManageHolderException;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserResumed;
import com.kizuna.user.domain.PlatformUserStopped;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.SelfStopNotAllowedException;
import com.kizuna.user.domain.StaleStaffUpdateException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlatformStaffServiceTest {

  /** HQ 側ロール（Console.PLATFORM の権限を含む）。管理者管理が扱える唯一の種類。 */
  private static final long HQ_ROLE = 10L;

  /** 店舗側ロール（構成権限がすべて STORE / SHARED）。単独では管理者管理の対象にならない。 */
  private static final long MANAGER_ROLE = 11L;

  /** ROLE_MANAGE を含む HQ 側ロール。不減零（G5）の母集団を作るのに使う。 */
  private static final long ROLE_MANAGE_ROLE = 12L;

  private static final Pageable PAGEABLE = PageRequest.of(0, 20);

  @Mock private PlatformUserRepository repository;

  @Mock private RoleRepository roleRepository;

  @Mock private PasswordEncoder encoder;

  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private PlatformStaffService service;

  @Captor private ArgumentCaptor<PlatformUser> userCaptor;

  private static final String ACTOR = "actor@kizuna.test";

  /** HQ 側ロールの解決を差し込む。既定では HQ_ROLE と ROLE_MANAGE_ROLE だけが HQ 側。 */
  private void givenHqSideRoles() {
    when(roleRepository.findHqRoleIds()).thenReturn(Set.of(HQ_ROLE, ROLE_MANAGE_ROLE));
  }

  /** ROLE_MANAGE を含むロールの解決を差し込む（不減零の母集団判定）。 */
  private void givenRoleManageRoles() {
    when(roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name()))
        .thenReturn(Set.of(ROLE_MANAGE_ROLE));
  }

  private Role role(long id, String name) {
    Role role = Role.builder().name(name).permissionIds(Set.of(1L)).build();
    role.setId(id);
    return role;
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
    // 永続化済みエンティティを模す（DB の version 列は 0 で初期化される）。
    ReflectionTestUtils.setField(user, "version", 0L);
    return user;
  }

  private PlatformUser castUser(long id, String email) {
    PlatformUser user =
        PlatformUser.builder()
            .email(email)
            .password("hash")
            .displayName("キャスト")
            .enabled(true)
            .userType(UserType.CAST)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    user.setId(id);
    return user;
  }

  private PlatformStaffCreateRequest createRequest(
      String email,
      String password,
      Set<Long> roleIds,
      StoreScopeType scopeType,
      Set<Long> storeIds) {
    PlatformStaffCreateRequest req = new PlatformStaffCreateRequest();
    req.setEmail(email);
    req.setPassword(password);
    req.setDisplayName("表示名");
    req.setRoleIds(roleIds);
    req.setStoreScopeType(scopeType);
    req.setStoreIds(storeIds);
    return req;
  }

  private PlatformStaffUpdateRequest updateRequest(
      Set<Long> roleIds, StoreScopeType scopeType, Set<Long> storeIds) {
    PlatformStaffUpdateRequest req = new PlatformStaffUpdateRequest();
    req.setRoleIds(roleIds);
    req.setStoreScopeType(scopeType);
    req.setStoreIds(storeIds);
    // staff() ヘルパの現行 version と一致させる（版の往復）。
    req.setVersion(0L);
    return req;
  }

  @Test
  void list_returnsStaffWithResolvedRoleNames() {
    List<PlatformUser> staff =
        List.of(
            staff(1L, "hq@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of()),
            staff(
                2L,
                "mgr@kizuna.test",
                Set.of(MANAGER_ROLE),
                StoreScopeType.SPECIFIC_STORES,
                Set.of(1L)));
    when(repository.findAll(ArgumentMatchers.<Specification<PlatformUser>>any(), eq(PAGEABLE)))
        .thenReturn(new PageImpl<>(staff, PAGEABLE, staff.size()));
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE, MANAGER_ROLE)))
        .thenReturn(List.of(role(HQ_ROLE, "HQ管理者"), role(MANAGER_ROLE, "店長")));

    Page<PlatformStaffResponse> result = service.list(null, null, PAGEABLE);

    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent().get(0).roles())
        .containsExactly(new PlatformStaffResponse.RoleRef(HQ_ROLE, "HQ管理者"));
    assertThat(result.getContent().get(1).roles())
        .containsExactly(new PlatformStaffResponse.RoleRef(MANAGER_ROLE, "店長"));
    assertThat(result.getContent().get(1).storeIds()).containsExactly(1L);
  }

  @Test
  void create_encodesPasswordAndSavesStaffWithRoles() {
    PlatformStaffCreateRequest req =
        createRequest(
            "new@kizuna.test",
            "rawpass",
            Set.of(HQ_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findByEmail("new@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(userCaptor.capture()))
        .thenAnswer(
            invocation -> {
              PlatformUser saved = invocation.getArgument(0);
              saved.setId(9L);
              ReflectionTestUtils.setField(saved, "version", 0L);
              return saved;
            });

    PlatformStaffResponse res = service.create(req);

    verify(encoder).encode("rawpass");
    PlatformUser saved = userCaptor.getValue();
    assertThat(saved.getEmail()).isEqualTo("new@kizuna.test");
    assertThat(saved.getPassword()).isEqualTo("ENCODED");
    assertThat(saved.getDisplayName()).isEqualTo("表示名");
    assertThat(saved.getEnabled()).isTrue();
    assertThat(saved.getUserType()).isEqualTo(UserType.STAFF);
    assertThat(saved.getRoleIds()).containsExactly(HQ_ROLE);
    assertThat(saved.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(saved.getStoreIds()).containsExactly(1L);
    assertThat(res.id()).isEqualTo(9L);
    assertThat(res.roles()).containsExactly(new PlatformStaffResponse.RoleRef(HQ_ROLE, "HQ管理者"));
    assertThat(res.enabled()).isTrue();
    assertThat(res.version()).as("作成応答も version を持つこと").isZero();
  }

  @Test
  void create_duplicateEmail_throwsAndDoesNotSave() {
    PlatformStaffCreateRequest req =
        createRequest(
            "dup@kizuna.test", "rawpass", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findByEmail("dup@kizuna.test"))
        .thenReturn(
            Optional.of(
                staff(
                    5L, "dup@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of())));

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(DuplicateStaffEmailException.class);

    verify(repository, never()).save(any());
  }

  @Test
  void create_unknownRole_throwsWithoutLookupOrEncode() {
    PlatformStaffCreateRequest req =
        createRequest(
            "new@kizuna.test", "rawpass", Set.of(999L), StoreScopeType.ALL_STORES, Set.of());
    when(roleRepository.findAllById(Set.of(999L))).thenReturn(List.of());

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("ロール");

    verify(repository, never()).findByEmail(any());
    verifyNoInteractions(encoder);
  }

  @Test
  void create_unknownStoreId_convertsToInvalidStoreScope() {
    PlatformStaffCreateRequest req =
        createRequest(
            "fk@kizuna.test",
            "rawpass",
            Set.of(HQ_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(999L));
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findByEmail("fk@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new ConstraintViolationException(
                    "save failed",
                    new SQLException("insert or update violates foreign key constraint"),
                    "fk_t_user_stores_store")));

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  void create_propagatesUnrelatedDataIntegrityViolation() {
    // email 一意・店舗 FK 以外の整合性違反はサービスで握りつぶさず、そのまま伝播すること。
    // 一意違反→409 / それ以外→500 の分類は CommonExceptionHandler が SQLSTATE で行うため、
    // サービス層で 400 に変換すると想定外の実装欠陥まで「店舗が存在しません」に化けてしまう。
    PlatformStaffCreateRequest req =
        createRequest(
            "fk@kizuna.test",
            "rawpass",
            Set.of(HQ_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findByEmail("fk@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException(
            "save failed",
            new ConstraintViolationException(
                "save failed",
                new SQLException("null value violates not-null constraint"),
                "display_name"));
    when(repository.saveAndFlush(any())).thenThrow(violation);

    assertThatThrownBy(() -> service.create(req)).isSameAs(violation);
  }

  @Test
  void create_roleFkViolation_convertsToMissingRole() {
    // requireRoles 通過後にロールが並行削除され、t_user_roles への授与行挿入が FK に当たるレース
    // （RoleService.delete の事前検証は未授与ロールの削除を許すため実在する経路）。
    // 事前検証と同じ 400（ロール不存在）へ分類する。
    PlatformStaffCreateRequest req =
        createRequest(
            "role-race@kizuna.test",
            "rawpass",
            Set.of(HQ_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findByEmail("role-race@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new ConstraintViolationException(
                    "save failed",
                    new SQLException("insert or update violates foreign key constraint"),
                    "fk_t_user_roles_role")));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("ロールが存在しません");
  }

  @Test
  void create_duplicateEmailUniqueViolation_convertsToDuplicateEmail() {
    // 事前 findByEmail を通過した後に DB 一意制約で敗者が弾かれるレース。店舗エラーではなく重複エラーへ分類する。
    PlatformStaffCreateRequest req =
        createRequest(
            "race@kizuna.test",
            "rawpass",
            Set.of(HQ_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findByEmail("race@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new ConstraintViolationException(
                    "save failed",
                    new SQLException("duplicate key value violates unique constraint"),
                    "uq_t_users_email")));

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(DuplicateStaffEmailException.class);
  }

  @Test
  void update_reassignsRolesAndScopesAndSaves() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE, MANAGER_ROLE)))
        .thenReturn(List.of(role(HQ_ROLE, "HQ管理者"), role(MANAGER_ROLE, "店長")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    // saveAndFlush は flush 時に version を増加させる（実挙動の模倣）。
    when(repository.saveAndFlush(existing))
        .thenAnswer(
            i -> {
              ReflectionTestUtils.setField(existing, "version", existing.getVersion() + 1);
              return existing;
            });

    PlatformStaffResponse res =
        service.update(
            3L,
            updateRequest(
                Set.of(HQ_ROLE, MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
            ACTOR);

    assertThat(existing.getRoleIds()).containsExactlyInAnyOrder(HQ_ROLE, MANAGER_ROLE);
    assertThat(existing.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(existing.getStoreIds()).containsExactly(1L);
    assertThat(res.id()).isEqualTo(3L);
    assertThat(res.roles())
        .containsExactly(
            new PlatformStaffResponse.RoleRef(HQ_ROLE, "HQ管理者"),
            new PlatformStaffResponse.RoleRef(MANAGER_ROLE, "店長"));
    assertThat(res.version()).as("応答は保存後の増加した version を返すこと").isEqualTo(1L);
  }

  @Test
  void update_staleVersion_throwsConflictWithoutSaving() {
    // 陳腐化した編集フォームの提出（version 不一致）は reassign 前に 409 系例外で拒否する。
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    ReflectionTestUtils.setField(existing, "version", 5L);
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE, MANAGER_ROLE)))
        .thenReturn(List.of(role(HQ_ROLE, "HQ管理者"), role(MANAGER_ROLE, "店長")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(HQ_ROLE, MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    req.setVersion(4L);

    assertThatThrownBy(() -> service.update(3L, req, ACTOR))
        .isInstanceOf(StaleStaffUpdateException.class)
        .isInstanceOf(ConflictException.class)
        .hasMessage("他の管理者が更新しました。最新の内容を確認してください");

    // 授権は再割当されず、保存も行われない。
    assertThat(existing.getRoleIds()).containsExactly(HQ_ROLE);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void update_unknownId_throwsNotFoundWithoutSaving() {
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.update(
                    404L,
                    updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of()),
                    ACTOR))
        .isInstanceOf(NotFoundException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void update_targetIsNonStaff_throwsNotFoundWithoutSaving() {
    // CAST/MEMBER はスタッフ管理の可視対象外。id を直接指定してもスタッフへ昇格させず、不在と同じ応答にする（本人種別検証）。
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(8L)).thenReturn(Optional.of(castUser(8L, "cast@kizuna.test")));

    assertThatThrownBy(
            () ->
                service.update(
                    8L, updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR))
        .isInstanceOf(NotFoundException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void update_unknownRole_throwsWithoutLookup() {
    when(roleRepository.findAllById(Set.of(999L))).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.update(
                    3L, updateRequest(Set.of(999L), StoreScopeType.ALL_STORES, Set.of()), ACTOR))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("ロール");

    verify(repository, never()).findById(any());
    verify(repository, never()).save(any());
  }

  @Test
  void update_unknownStoreId_convertsToInvalidStoreScope() {
    PlatformUser existing =
        staff(7L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(7L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new ConstraintViolationException(
                    "save failed",
                    new SQLException("insert or update violates foreign key constraint"),
                    "fk_t_user_stores_store")));

    assertThatThrownBy(
            () ->
                service.update(
                    7L,
                    updateRequest(Set.of(HQ_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(999L)),
                    ACTOR))
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  void update_duplicateEmailUniqueViolation_convertsToDuplicateEmail() {
    PlatformUser existing =
        staff(9L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(9L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new ConstraintViolationException(
                    "save failed",
                    new SQLException("duplicate key value violates unique constraint"),
                    "uq_t_users_email")));

    assertThatThrownBy(
            () ->
                service.update(
                    9L,
                    updateRequest(Set.of(HQ_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
                    ACTOR))
        .isInstanceOf(DuplicateStaffEmailException.class);
  }

  @Test
  void update_disabling_stopsUser() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    req.setEnabled(false);

    PlatformStaffResponse res = service.update(3L, req, ACTOR);

    assertThat(existing.getEnabled()).isFalse();
    assertThat(res.enabled()).isFalse();
  }

  @Test
  void update_reEnabling_resumesUser() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    existing.stop();
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    req.setEnabled(true);

    service.update(3L, req, ACTOR);

    assertThat(existing.getEnabled()).isTrue();
  }

  @Test
  void update_disabling_publishesPlatformUserStoppedEvent() {
    // 既に停止済みの対象へ enabled=false を再送しても発行する（結果語義の冪等性 — Redis 書込み失敗後の再送復旧のため）。
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    existing.stop();
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    req.setEnabled(false);

    service.update(3L, req, ACTOR);

    verify(eventPublisher).publishEvent(new PlatformUserStopped("target@kizuna.test"));
  }

  @Test
  void update_enabling_publishesPlatformUserResumedEvent() {
    // 既に enabled=true の対象へ enabled=true を再送しても発行する（結果語義の冪等性）。
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    req.setEnabled(true);

    service.update(3L, req, ACTOR);

    verify(eventPublisher).publishEvent(new PlatformUserResumed("target@kizuna.test"));
  }

  @Test
  void update_enabledNull_publishesNoEvents() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);

    service.update(
        3L, updateRequest(Set.of(HQ_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)), ACTOR);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  void update_selfStop_throwsSelfStopNotAllowedExceptionWithoutSavingOrPublishing() {
    // 実行主体（JWT subject = actorEmail）が対象自身のメールと一致する場合、自己停止として拒否する。
    PlatformUser existing = staff(3L, ACTOR, Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    req.setEnabled(false);

    assertThatThrownBy(() -> service.update(3L, req, ACTOR))
        .isInstanceOf(SelfStopNotAllowedException.class)
        .hasMessage("自分自身を停止することはできません");

    assertThat(existing.getEnabled()).as("ガードは reassignGrants 前で発火し授権も変更されないこと").isTrue();
    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void create_withoutHqSideRole_isRejectedWithoutSaving() {
    PlatformStaffCreateRequest req =
        createRequest(
            "store-only@kizuna.test",
            "rawpass",
            Set.of(MANAGER_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(MANAGER_ROLE)))
        .thenReturn(List.of(role(MANAGER_ROLE, "店長")));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(InvalidRoleGrantException.class)
        .hasMessage("管理者にはプラットフォーム権限を含むロールを 1 つ以上付与してください");

    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(encoder);
  }

  @Test
  void update_toStoreSideRolesOnly_isRejectedWithoutSaving() {
    // 一覧から消えるだけの静黙な降格にしない。店舗側へ降ろす操作は店舗スタッフ管理の領分で、この面では拒否する。
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(MANAGER_ROLE)))
        .thenReturn(List.of(role(MANAGER_ROLE, "店長")));

    assertThatThrownBy(
            () ->
                service.update(
                    3L,
                    updateRequest(Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
                    ACTOR))
        .isInstanceOf(InvalidRoleGrantException.class);

    assertThat(existing.getRoleIds()).containsExactly(HQ_ROLE);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void update_targetHoldsOnlyStoreSideRoles_throwsNotFoundWithoutSaving() {
    // 店舗側ロールのみの利用者は本 API の対象外。id 直指定でも在否を漏らさず不在と同じ応答にする。
    givenHqSideRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(4L))
        .thenReturn(
            Optional.of(
                staff(
                    4L,
                    "store-staff@kizuna.test",
                    Set.of(MANAGER_ROLE),
                    StoreScopeType.SPECIFIC_STORES,
                    Set.of(1L))));

    assertThatThrownBy(
            () ->
                service.update(
                    4L, updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR))
        .isInstanceOf(NotFoundException.class);

    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void update_stoppingLastRoleManageHolder_isRejectedWithoutSavingOrPublishing() {
    PlatformUser existing =
        staff(
            3L,
            "last-admin@kizuna.test",
            Set.of(ROLE_MANAGE_ROLE),
            StoreScopeType.ALL_STORES,
            Set.of());
    givenHqSideRoles();
    givenRoleManageRoles();
    when(roleRepository.findAllById(Set.of(ROLE_MANAGE_ROLE)))
        .thenReturn(List.of(role(ROLE_MANAGE_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.findEnabledRoleHolderIds(Set.of(ROLE_MANAGE_ROLE))).thenReturn(List.of(3L));
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(ROLE_MANAGE_ROLE), StoreScopeType.ALL_STORES, Set.of());
    req.setEnabled(false);

    assertThatThrownBy(() -> service.update(3L, req, ACTOR))
        .isInstanceOf(LastRoleManageHolderException.class)
        .hasMessage("最後の管理権限保持者を停止・降格することはできません");

    assertThat(existing.getEnabled()).isTrue();
    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void update_selfDemotionOfLastRoleManageHolder_isRejected() {
    // 自己降級そのものは許す（G4）が、母集団が 0 になるならこちらの守衛が先に立つ。
    PlatformUser existing =
        staff(3L, ACTOR, Set.of(ROLE_MANAGE_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    givenRoleManageRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.findEnabledRoleHolderIds(Set.of(ROLE_MANAGE_ROLE))).thenReturn(List.of(3L));

    assertThatThrownBy(
            () ->
                service.update(
                    3L, updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR))
        .isInstanceOf(LastRoleManageHolderException.class);

    assertThat(existing.getRoleIds()).containsExactly(ROLE_MANAGE_ROLE);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void update_demotingRoleManageHolderWhileAnotherRemains_isAllowed() {
    // 守衛が「降格そのもの」を塞いでいないことの対照。母集団が残るなら通す。
    PlatformUser existing =
        staff(
            3L, "admin@kizuna.test", Set.of(ROLE_MANAGE_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    givenRoleManageRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.findEnabledRoleHolderIds(Set.of(ROLE_MANAGE_ROLE)))
        .thenReturn(List.of(3L, 99L));
    when(repository.saveAndFlush(existing)).thenReturn(existing);

    service.update(3L, updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR);

    assertThat(existing.getRoleIds()).containsExactly(HQ_ROLE);
  }

  @Test
  void update_thatCannotShrinkTheHolderSet_doesNotSerialize() {
    // 母集団を減らさない更新まで母集団の全行を押さえると、無関係な授権変更同士が待ち合う。
    PlatformUser existing =
        staff(
            3L, "admin@kizuna.test", Set.of(ROLE_MANAGE_ROLE), StoreScopeType.ALL_STORES, Set.of());
    givenHqSideRoles();
    givenRoleManageRoles();
    when(roleRepository.findAllById(Set.of(ROLE_MANAGE_ROLE)))
        .thenReturn(List.of(role(ROLE_MANAGE_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);

    service.update(
        3L,
        updateRequest(Set.of(ROLE_MANAGE_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
        ACTOR);

    verify(repository, never()).lockEnabledRoleHolderIds(any());
  }

  @Test
  void update_countsHoldersAfterLockingNotTheLockResult() {
    // 押さえる問い合わせの結果は待つ前のスナップショットのままで、待っている間に確定した降格を見ない
    // （PlatformStaffManagementIT が実 PostgreSQL で実測）。数えるのは押さえた後に取り直した側でなければ、
    // 最後の 2 人の相互降級で母集団が 0 になる。
    PlatformUser existing =
        staff(
            3L,
            "last-admin@kizuna.test",
            Set.of(ROLE_MANAGE_ROLE),
            StoreScopeType.ALL_STORES,
            Set.of());
    givenHqSideRoles();
    givenRoleManageRoles();
    when(roleRepository.findAllById(Set.of(HQ_ROLE))).thenReturn(List.of(role(HQ_ROLE, "HQ管理者")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    // 押さえた側はまだ相手を保持者だと思っている。取り直した側だけが降格を見ている。
    when(repository.lockEnabledRoleHolderIds(Set.of(ROLE_MANAGE_ROLE)))
        .thenReturn(List.of(3L, 99L));
    when(repository.findEnabledRoleHolderIds(Set.of(ROLE_MANAGE_ROLE))).thenReturn(List.of(3L));

    assertThatThrownBy(
            () ->
                service.update(
                    3L, updateRequest(Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of()), ACTOR))
        .isInstanceOf(LastRoleManageHolderException.class);

    verify(repository, never()).saveAndFlush(any());
  }
}
