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
import com.kizuna.user.domain.InvalidStoreScopeException;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

  private static final long HQ_ROLE = 10L;
  private static final long MANAGER_ROLE = 11L;
  private static final Pageable PAGEABLE = PageRequest.of(0, 20);

  @Mock private PlatformUserRepository repository;

  @Mock private RoleRepository roleRepository;

  @Mock private PasswordEncoder encoder;

  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private PlatformStaffService service;

  @Captor private ArgumentCaptor<PlatformUser> userCaptor;

  private static final String ACTOR = "actor@kizuna.test";

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
    when(roleRepository.findAllById(Set.of(HQ_ROLE, MANAGER_ROLE)))
        .thenReturn(List.of(role(HQ_ROLE, "HQ管理者"), role(MANAGER_ROLE, "店長")));

    Page<PlatformStaffResponse> result = service.list(null, PAGEABLE);

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
            Set.of(MANAGER_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    when(roleRepository.findAllById(Set.of(MANAGER_ROLE)))
        .thenReturn(List.of(role(MANAGER_ROLE, "店長")));
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
    assertThat(saved.getRoleIds()).containsExactly(MANAGER_ROLE);
    assertThat(saved.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(saved.getStoreIds()).containsExactly(1L);
    assertThat(res.id()).isEqualTo(9L);
    assertThat(res.roles()).containsExactly(new PlatformStaffResponse.RoleRef(MANAGER_ROLE, "店長"));
    assertThat(res.enabled()).isTrue();
    assertThat(res.version()).as("作成応答も version を持つこと").isZero();
  }

  @Test
  void create_duplicateEmail_throwsAndDoesNotSave() {
    PlatformStaffCreateRequest req =
        createRequest(
            "dup@kizuna.test", "rawpass", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
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
            Set.of(MANAGER_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(999L));
    when(roleRepository.findAllById(Set.of(MANAGER_ROLE)))
        .thenReturn(List.of(role(MANAGER_ROLE, "店長")));
    when(repository.findByEmail("fk@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("fk violation"));

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  void create_duplicateEmailUniqueViolation_convertsToDuplicateEmail() {
    // 事前 findByEmail を通過した後に DB 一意制約で敗者が弾かれるレース。店舗エラーではなく重複エラーへ分類する。
    PlatformStaffCreateRequest req =
        createRequest(
            "race@kizuna.test",
            "rawpass",
            Set.of(MANAGER_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    when(roleRepository.findAllById(Set.of(MANAGER_ROLE)))
        .thenReturn(List.of(role(MANAGER_ROLE, "店長")));
    when(repository.findByEmail("race@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new RuntimeException(
                    "ERROR: duplicate key value violates unique constraint"
                        + " \"uq_t_users_email\"")));

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(DuplicateStaffEmailException.class);
  }

  @Test
  void update_reassignsRolesAndScopesAndSaves() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    when(roleRepository.findAllById(Set.of(MANAGER_ROLE)))
        .thenReturn(List.of(role(MANAGER_ROLE, "店長")));
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
            updateRequest(Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
            ACTOR);

    assertThat(existing.getRoleIds()).containsExactly(MANAGER_ROLE);
    assertThat(existing.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(existing.getStoreIds()).containsExactly(1L);
    assertThat(res.id()).isEqualTo(3L);
    assertThat(res.roles()).containsExactly(new PlatformStaffResponse.RoleRef(MANAGER_ROLE, "店長"));
    assertThat(res.version()).as("応答は保存後の増加した version を返すこと").isEqualTo(1L);
  }

  @Test
  void update_staleVersion_throwsConflictWithoutSaving() {
    // 陳腐化した編集フォームの提出（version 不一致）は reassign 前に 409 系例外で拒否する。
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    ReflectionTestUtils.setField(existing, "version", 5L);
    when(roleRepository.findAllById(Set.of(MANAGER_ROLE)))
        .thenReturn(List.of(role(MANAGER_ROLE, "店長")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    PlatformStaffUpdateRequest req =
        updateRequest(Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L));
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
    when(roleRepository.findAllById(Set.of(MANAGER_ROLE)))
        .thenReturn(List.of(role(MANAGER_ROLE, "店長")));
    when(repository.findById(7L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing))
        .thenThrow(new DataIntegrityViolationException("fk violation"));

    assertThatThrownBy(
            () ->
                service.update(
                    7L,
                    updateRequest(
                        Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(999L)),
                    ACTOR))
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  void update_duplicateEmailUniqueViolation_convertsToDuplicateEmail() {
    PlatformUser existing =
        staff(9L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
    when(roleRepository.findAllById(Set.of(MANAGER_ROLE)))
        .thenReturn(List.of(role(MANAGER_ROLE, "店長")));
    when(repository.findById(9L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new RuntimeException(
                    "ERROR: duplicate key value violates unique constraint"
                        + " \"uq_t_users_email\"")));

    assertThatThrownBy(
            () ->
                service.update(
                    9L,
                    updateRequest(Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)),
                    ACTOR))
        .isInstanceOf(DuplicateStaffEmailException.class);
  }

  @Test
  void update_disabling_stopsUser() {
    PlatformUser existing =
        staff(3L, "target@kizuna.test", Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
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
    when(roleRepository.findAllById(Set.of(MANAGER_ROLE)))
        .thenReturn(List.of(role(MANAGER_ROLE, "店長")));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);

    service.update(
        3L, updateRequest(Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(1L)), ACTOR);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  void update_selfStop_throwsSelfStopNotAllowedExceptionWithoutSavingOrPublishing() {
    // 実行主体（JWT subject = actorEmail）が対象自身のメールと一致する場合、自己停止として拒否する。
    PlatformUser existing = staff(3L, ACTOR, Set.of(HQ_ROLE), StoreScopeType.ALL_STORES, Set.of());
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
}
