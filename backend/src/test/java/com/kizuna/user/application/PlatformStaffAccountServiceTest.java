package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.api.dto.StaffAccountRoleRef;
import com.kizuna.user.api.dto.StaffAccountSummaryResponse;
import com.kizuna.user.domain.HqPasswordResetNotAllowedException;
import com.kizuna.user.domain.LastRoleManageHolderException;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserPasswordReset;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserResumed;
import com.kizuna.user.domain.PlatformUserStopped;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.SelfStopNotAllowedException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PlatformStaffAccountServiceTest {

  /** ROLE_MANAGE を含むロール。不減零（G5）の母集団を作るのに使う。 */
  private static final long ROLE_MANAGE_ROLE = 12L;

  /** 店舗側ロール。この面では HQ 側と同じく対象になる。 */
  private static final long STORE_SIDE_ROLE = 11L;

  /** HQ 側ロール。パスワード再設定の境界（G6）の母集団を作るのに使う。 */
  private static final long HQ_ROLE = 13L;

  private static final Pageable PAGEABLE = PageRequest.of(0, 20);

  private static final String ACTOR = "actor@kizuna.test";

  @Mock private PlatformUserRepository repository;

  @Mock private RoleRepository roleRepository;

  @Mock private PermissionRepository permissionRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private PlatformStaffAccountService service;

  private Role role(long id, String name) {
    Role role = Role.builder().name(name).permissionIds(Set.of(1L)).build();
    role.setId(id);
    return role;
  }

  private PlatformUser staff(long id, String email, Set<Long> roleIds) {
    PlatformUser user =
        PlatformUser.builder()
            .email(email)
            .password("hash")
            .displayName("表示名")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(roleIds)
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(id);
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

  @Test
  @DisplayName("最後の ROLE_MANAGE 実効保持者の停止は拒否され、押さえる順は 目録行 → 利用者行 であること")
  void suspend_lastRoleManageHolder_isRejectedAndTakesTheMutexBeforeThePopulation() {
    // 停止も併合済み PUT と同じ順で押さえないと待ちが環になる（目録行 1 行が全行使点の起点）。
    PlatformUser existing = staff(3L, "last-admin@kizuna.test", Set.of(ROLE_MANAGE_ROLE));
    when(repository.findStaffEmailById(3L)).thenReturn(Optional.of("last-admin@kizuna.test"));
    when(roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name()))
        .thenReturn(Set.of(ROLE_MANAGE_ROLE));
    when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(existing));
    when(repository.findEnabledRoleHolderIds(Set.of(ROLE_MANAGE_ROLE))).thenReturn(List.of(3L));

    assertThatThrownBy(() -> service.suspend(3L, ACTOR))
        .isInstanceOf(LastRoleManageHolderException.class)
        .hasMessage("最後の管理権限保持者を停止・降格することはできません");

    InOrder inOrder = inOrder(permissionRepository, repository);
    inOrder.verify(permissionRepository).lockIdByCode(PermissionCode.ROLE_MANAGE.name());
    inOrder.verify(repository).lockEnabledRoleHolderIds(Set.of(ROLE_MANAGE_ROLE));
    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("押さえた結果ではなく取り直した顔ぶれで数えること")
  void suspend_countsHoldersAfterLockingNotTheLockResult() {
    // 押さえる問い合わせの結果は待つ前のスナップショットのままで、待っている間に確定した降格を見ない。
    PlatformUser existing = staff(3L, "last-admin@kizuna.test", Set.of(ROLE_MANAGE_ROLE));
    when(repository.findStaffEmailById(3L)).thenReturn(Optional.of("last-admin@kizuna.test"));
    when(roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name()))
        .thenReturn(Set.of(ROLE_MANAGE_ROLE));
    when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(existing));
    when(repository.lockEnabledRoleHolderIds(Set.of(ROLE_MANAGE_ROLE)))
        .thenReturn(List.of(3L, 99L));
    when(repository.findEnabledRoleHolderIds(Set.of(ROLE_MANAGE_ROLE))).thenReturn(List.of(3L));

    assertThatThrownBy(() -> service.suspend(3L, ACTOR))
        .isInstanceOf(LastRoleManageHolderException.class);

    assertThat(existing.getEnabled()).isTrue();
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("他に保持者が残るなら ROLE_MANAGE 保持者の停止は通ること（守衛が停止そのものを塞いでいない対照）")
  void suspend_roleManageHolderWhileAnotherRemains_isAllowed() {
    PlatformUser existing = staff(3L, "admin@kizuna.test", Set.of(ROLE_MANAGE_ROLE));
    when(repository.findStaffEmailById(3L)).thenReturn(Optional.of("admin@kizuna.test"));
    when(roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name()))
        .thenReturn(Set.of(ROLE_MANAGE_ROLE));
    when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(existing));
    when(repository.findEnabledRoleHolderIds(Set.of(ROLE_MANAGE_ROLE)))
        .thenReturn(List.of(3L, 99L));

    service.suspend(3L, ACTOR);

    assertThat(existing.getEnabled()).isFalse();
    verify(repository).saveAndFlush(existing);
    verify(eventPublisher).publishEvent(new PlatformUserStopped("admin@kizuna.test"));
  }

  @Test
  @DisplayName("ROLE_MANAGE を持たない対象でも直列化点は押さえ、母集団の行までは押さえないこと")
  void suspend_nonHolder_takesTheMutexButNotThePopulation() {
    PlatformUser existing = staff(4L, "store-only@kizuna.test", Set.of(STORE_SIDE_ROLE));
    when(repository.findStaffEmailById(4L)).thenReturn(Optional.of("store-only@kizuna.test"));
    when(roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name()))
        .thenReturn(Set.of(ROLE_MANAGE_ROLE));
    when(repository.findByIdForUpdate(4L)).thenReturn(Optional.of(existing));

    service.suspend(4L, ACTOR);

    assertThat(existing.getEnabled()).isFalse();
    verify(permissionRepository).lockIdByCode(PermissionCode.ROLE_MANAGE.name());
    verify(repository, never()).lockEnabledRoleHolderIds(any());
  }

  @Test
  @DisplayName("実行主体が自分自身を停止しようとすると、直列化点を押さえる前に 400 で拒否されること")
  void suspend_self_isRejectedBeforeTakingTheMutex() {
    when(repository.findStaffEmailById(3L)).thenReturn(Optional.of(ACTOR));

    assertThatThrownBy(() -> service.suspend(3L, ACTOR))
        .isInstanceOf(SelfStopNotAllowedException.class)
        .hasMessage("自分自身を停止することはできません");

    verifyNoInteractions(permissionRepository);
    verify(repository, never()).findByIdForUpdate(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("既に停止済みの対象への停止は不減零も stop も通さず、失効イベントだけを再発行すること（冪等）")
  void suspend_alreadyDisabled_republishesTheRevocationOnly() {
    PlatformUser existing = staff(3L, "stopped@kizuna.test", Set.of(ROLE_MANAGE_ROLE));
    existing.stop();
    when(repository.findStaffEmailById(3L)).thenReturn(Optional.of("stopped@kizuna.test"));
    when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(existing));

    service.suspend(3L, ACTOR);

    verify(repository, never()).lockEnabledRoleHolderIds(any());
    verify(repository, never()).saveAndFlush(any());
    verify(eventPublisher).publishEvent(new PlatformUserStopped("stopped@kizuna.test"));
  }

  @Test
  @DisplayName("停止済みの再開は enabled を戻し、解除イベントを発行すること")
  void resume_disabledTarget_resumesAndPublishes() {
    PlatformUser existing = staff(3L, "stopped@kizuna.test", Set.of(STORE_SIDE_ROLE));
    existing.stop();
    when(repository.findById(3L)).thenReturn(Optional.of(existing));

    service.resume(3L);

    assertThat(existing.getEnabled()).isTrue();
    verify(repository).saveAndFlush(existing);
    verify(eventPublisher).publishEvent(new PlatformUserResumed("stopped@kizuna.test"));
  }

  @Test
  @DisplayName("既に有効な対象への再開も解除イベントを発行すること（冪等）")
  void resume_alreadyEnabled_republishesTheReleaseOnly() {
    PlatformUser existing = staff(3L, "enabled@kizuna.test", Set.of(STORE_SIDE_ROLE));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));

    service.resume(3L);

    verify(repository, never()).saveAndFlush(any());
    verify(eventPublisher).publishEvent(new PlatformUserResumed("enabled@kizuna.test"));
  }

  @Test
  @DisplayName("CAST の id を直接指定した再開は 404 になること")
  void resume_targetIsNotStaff_throwsNotFound() {
    when(repository.findById(8L)).thenReturn(Optional.of(castUser(8L, "cast@kizuna.test")));

    assertThatThrownBy(() -> service.resume(8L)).isInstanceOf(NotFoundException.class);

    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("HQ 側ロール保持者へのパスワード再設定は 400 で拒否され、何も書かれないこと（守衛 G6）")
  void resetPassword_hqRoleHolder_isRejectedAndWritesNothing() {
    // 実行主体も必ず HQ 側ロールを持つため、自己再設定もこの判定に吸収される。
    PlatformUser existing = staff(3L, "hq@kizuna.test", Set.of(HQ_ROLE));
    when(repository.findById(3L)).thenReturn(Optional.of(existing));
    when(roleRepository.findHqRoleIds()).thenReturn(Set.of(HQ_ROLE));

    assertThatThrownBy(() -> service.resetPassword(3L))
        .isInstanceOf(HqPasswordResetNotAllowedException.class)
        .hasMessage("HQ 側ロール保持者のパスワードは再設定できません");

    assertThat(existing.getPassword()).as("パスワードは書き換わらないこと").isEqualTo("hash");
    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(passwordEncoder);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("店舗側ロールだけの対象は仮パスワードを符号化して保存し、失効イベントを発行すること")
  void resetPassword_storeSideTarget_encodesAndPublishes() {
    PlatformUser existing = staff(4L, "store-only@kizuna.test", Set.of(STORE_SIDE_ROLE));
    when(repository.findById(4L)).thenReturn(Optional.of(existing));
    when(roleRepository.findHqRoleIds()).thenReturn(Set.of(HQ_ROLE));
    when(passwordEncoder.encode(ArgumentMatchers.anyString())).thenReturn("encoded");

    String temporaryPassword = service.resetPassword(4L);

    // 返した生値そのものが符号化されて保存される（別の値を返す取り違えを排除する）。
    assertThat(temporaryPassword).hasSize(16);
    verify(passwordEncoder).encode(temporaryPassword);
    assertThat(existing.getPassword()).isEqualTo("encoded");
    verify(repository).saveAndFlush(existing);
    verify(eventPublisher).publishEvent(new PlatformUserPasswordReset("store-only@kizuna.test"));
    // 再設定は enabled もロールも動かさないので、不減零の直列化点は押さえない。
    verifyNoInteractions(permissionRepository);
  }

  @Test
  @DisplayName("CAST の id を直接指定した再設定は、ロールを引く前に 404 になること")
  void resetPassword_targetIsNotStaff_throwsNotFoundBeforeRoleLookup() {
    when(repository.findById(8L)).thenReturn(Optional.of(castUser(8L, "cast@kizuna.test")));

    assertThatThrownBy(() -> service.resetPassword(8L)).isInstanceOf(NotFoundException.class);

    verifyNoInteractions(roleRepository);
    verifyNoInteractions(passwordEncoder);
    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("一覧は店舗側ロールしか持たないアカウントも含め、ロール名称を解決して返すこと")
  void list_includesStoreSideOnlyAccountsWithResolvedRoleNames() {
    List<PlatformUser> accounts =
        List.of(
            staff(1L, "hq@kizuna.test", Set.of(ROLE_MANAGE_ROLE)),
            staff(2L, "mgr@kizuna.test", Set.of(STORE_SIDE_ROLE)));
    when(repository.findAll(ArgumentMatchers.<Specification<PlatformUser>>any(), eq(PAGEABLE)))
        .thenReturn(new PageImpl<>(accounts, PAGEABLE, accounts.size()));
    when(roleRepository.findAllById(Set.of(ROLE_MANAGE_ROLE, STORE_SIDE_ROLE)))
        .thenReturn(List.of(role(ROLE_MANAGE_ROLE, "HQ管理者"), role(STORE_SIDE_ROLE, "店長")));

    Page<StaffAccountSummaryResponse> result = service.list(null, null, PAGEABLE);

    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent().get(1).roles())
        .containsExactly(new StaffAccountRoleRef(STORE_SIDE_ROLE, "店長"));
    assertThat(result.getContent().get(1).enabled()).isTrue();
  }

  @Test
  @DisplayName("CAST の id を直接指定した詳細取得は 404 になること（在否を漏らさない）")
  void get_targetIsNotStaff_throwsNotFound() {
    when(repository.findById(8L)).thenReturn(Optional.of(castUser(8L, "cast@kizuna.test")));

    assertThatThrownBy(() -> service.get(8L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("CAST の id を直接指定した停止は、行を押さえる前に 404 になること")
  void suspend_targetIsNotStaff_throwsNotFoundWithoutLocking() {
    when(repository.findStaffEmailById(8L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.suspend(8L, ACTOR)).isInstanceOf(NotFoundException.class);

    verify(repository, never()).findByIdForUpdate(any());
    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(eventPublisher);
  }
}
