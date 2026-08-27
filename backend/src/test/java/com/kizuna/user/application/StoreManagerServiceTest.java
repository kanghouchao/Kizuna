package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.user.api.dto.StoreManagerAppointRequest;
import com.kizuna.user.api.dto.StoreManagerResponse;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.SystemRole;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 店長設定の語義を固定する。任命はロール付与＋店舗追加、解任は店舗除去だけで、不変条件と衝突する解任は自動降格へ倒さず撥ねる（ADR 0020）。
 *
 * <p>導出の述語そのもの（Specification が実際に絞る集合）は本物の DB でしか確かめられないので統合テストが持つ。ここは母集団の判定と 授権の書き換えの形を撃つ。
 */
@ExtendWith(MockitoExtension.class)
class StoreManagerServiceTest {

  private static final long STORE = 1L;
  private static final long OTHER_STORE = 2L;

  /** 店長ロール（STORE_MANAGER）。任命が付与する唯一のロール。 */
  private static final long MANAGER_ROLE = 11L;

  /** 委譲権限を含まない店舗側ロール。任命候補が店長ロール以外に持ちうるもの。 */
  private static final long CLERK_ROLE = 12L;

  /** 店舗スタッフロール（STORE_STAFF）。降格の受け皿。 */
  private static final long STAFF_ROLE = 14L;

  /** HQ 側ロール（Console.PLATFORM の権限を含む）。任命の母集団から外れる。 */
  private static final long HQ_ROLE = 13L;

  /** 作成要求へ載せる素の値。符号化して保存する経路を通すためだけの固定値で、意味は持たない。 */
  private static final String RAW_CREDENTIAL = "rawpass";

  @Mock private PlatformUserRepository repository;
  @Mock private RoleRepository roleRepository;
  @Mock private PasswordEncoder encoder;
  @Mock private StoreExistenceCheck storeExistenceCheck;

  @InjectMocks private StoreManagerService service;

  @Captor private ArgumentCaptor<PlatformUser> userCaptor;

  private void givenStore() {
    when(storeExistenceCheck.exists(STORE)).thenReturn(true);
  }

  private void givenManagerRole() {
    Role role =
        Role.builder()
            .name(SystemRole.STORE_MANAGER.getRoleName())
            .permissionIds(Set.of(1L))
            .build();
    role.setId(MANAGER_ROLE);
    when(roleRepository.findByName(SystemRole.STORE_MANAGER.getRoleName()))
        .thenReturn(Optional.of(role));
  }

  private void givenStaffRole() {
    Role role =
        Role.builder().name(SystemRole.STORE_STAFF.getRoleName()).permissionIds(Set.of(2L)).build();
    role.setId(STAFF_ROLE);
    when(roleRepository.findByName(SystemRole.STORE_STAFF.getRoleName()))
        .thenReturn(Optional.of(role));
  }

  /** HQ 側ロールの解決を差し込む。既定では HQ_ROLE だけが HQ 側。 */
  private void givenHqSideRoles() {
    when(roleRepository.findHqRoleIds()).thenReturn(Set.of(HQ_ROLE));
  }

  private PlatformUser staff(
      long id, Set<Long> roleIds, StoreScopeType scopeType, Set<Long> storeIds, boolean enabled) {
    PlatformUser user =
        PlatformUser.builder()
            .email("target@kizuna.test")
            .password("hash")
            .displayName("表示名")
            .enabled(enabled)
            .userType(UserType.STAFF)
            .roleIds(roleIds)
            .storeScopeType(scopeType)
            .storeIds(storeIds)
            .build();
    user.setId(id);
    ReflectionTestUtils.setField(user, "version", 0L);
    return user;
  }

  private void givenTarget(PlatformUser user) {
    when(repository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
  }

  private void givenSaveEchoes() {
    when(repository.saveAndFlush(ArgumentMatchers.any(PlatformUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static StoreManagerAppointRequest appointExisting(long userId) {
    StoreManagerAppointRequest req = new StoreManagerAppointRequest();
    req.setUserId(userId);
    return req;
  }

  private static StoreManagerAppointRequest appointNew() {
    StoreManagerAppointRequest req = new StoreManagerAppointRequest();
    req.setEmail("new-manager@kizuna.test");
    req.setPassword(RAW_CREDENTIAL);
    req.setDisplayName("新店長");
    return req;
  }

  @Test
  @DisplayName("一覧: 停止中の店長も状態付きで返ること（未設店長の判定を前端が有効な行だけで行えること）")
  void listReturnsStoppedManagersWithTheirState() {
    givenStore();
    givenManagerRole();
    when(repository.findAll(
            ArgumentMatchers.<Specification<PlatformUser>>any(), ArgumentMatchers.any(Sort.class)))
        .thenReturn(
            List.of(
                staff(
                    1L, Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(STORE), true),
                staff(
                    2L,
                    Set.of(MANAGER_ROLE),
                    StoreScopeType.SPECIFIC_STORES,
                    Set.of(STORE),
                    false)));

    List<StoreManagerResponse> managers = service.list(STORE);

    assertThat(managers).extracting(StoreManagerResponse::enabled).containsExactly(true, false);
  }

  @Test
  @DisplayName("一覧: 実在しない店舗は空一覧でなく 404 になること")
  void listRejectsUnknownStore() {
    when(storeExistenceCheck.exists(STORE)).thenReturn(false);

    assertThatThrownBy(() -> service.list(STORE)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("任命(既存): 店長ロールの付与と担当店舗の追加が和で行われること")
  void appointingExistingStaffUnionsRoleAndStore() {
    givenStore();
    givenManagerRole();
    givenHqSideRoles();
    givenTarget(
        staff(5L, Set.of(CLERK_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(OTHER_STORE), true));
    givenSaveEchoes();

    service.appoint(STORE, appointExisting(5L));

    verify(repository).saveAndFlush(userCaptor.capture());
    assertThat(userCaptor.getValue().getRoleIds())
        .as("既存のロールは残したまま店長ロールを足すこと")
        .containsExactlyInAnyOrder(CLERK_ROLE, MANAGER_ROLE);
    assertThat(userCaptor.getValue().getStoreIds())
        .as("既存の担当店舗は残したまま当該店舗を足すこと")
        .containsExactlyInAnyOrder(STORE, OTHER_STORE);
  }

  @Test
  @DisplayName("任命(既存): 他店の店長は候補のまま任命でき、当該店舗が担当へ加わること")
  void appointingManagerOfAnotherStoreAddsThisStore() {
    givenStore();
    givenManagerRole();
    givenHqSideRoles();
    givenTarget(
        staff(5L, Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(OTHER_STORE), true));
    givenSaveEchoes();

    service.appoint(STORE, appointExisting(5L));

    verify(repository).saveAndFlush(userCaptor.capture());
    assertThat(userCaptor.getValue().getStoreIds()).containsExactlyInAnyOrder(STORE, OTHER_STORE);
  }

  @Test
  @DisplayName("任命(既存): 既にこの店舗の店長なら撥ねること（静默冪等にしない）")
  void appointingCurrentManagerIsRejected() {
    givenStore();
    givenManagerRole();
    givenTarget(
        staff(5L, Set.of(MANAGER_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(STORE), true));

    assertThatThrownBy(() -> service.appoint(STORE, appointExisting(5L)))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("既にこの店舗の店長");
    verify(repository, never()).saveAndFlush(ArgumentMatchers.any());
  }

  @Test
  @DisplayName("任命(既存): HQ 側ロール保持者は母集団外として撥ねること")
  void appointingHqSideRoleHolderIsRejected() {
    givenStore();
    givenManagerRole();
    givenHqSideRoles();
    givenTarget(
        staff(5L, Set.of(HQ_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(OTHER_STORE), true));

    assertThatThrownBy(() -> service.appoint(STORE, appointExisting(5L)))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("任命できません");
    verify(repository, never()).saveAndFlush(ArgumentMatchers.any());
  }

  // ALL_STORES を任命すると、担当店舗集合から当該店舗を引く形が存在せず解任できない店長ができる。
  @Test
  @DisplayName("任命(既存): 全店舗担当のアカウントは撥ねること")
  void appointingAllStoresAccountIsRejected() {
    givenStore();
    givenManagerRole();
    givenHqSideRoles();
    givenTarget(staff(5L, Set.of(CLERK_ROLE), StoreScopeType.ALL_STORES, Set.of(), true));

    assertThatThrownBy(() -> service.appoint(STORE, appointExisting(5L)))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("任命できません");
    verify(repository, never()).saveAndFlush(ArgumentMatchers.any());
  }

  @Test
  @DisplayName("任命(既存): 停止中のアカウントは撥ねること")
  void appointingStoppedAccountIsRejected() {
    givenStore();
    givenManagerRole();
    givenHqSideRoles();
    givenTarget(
        staff(5L, Set.of(CLERK_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(OTHER_STORE), false));

    assertThatThrownBy(() -> service.appoint(STORE, appointExisting(5L)))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("任命できません");
  }

  // 母集団外と不在で応答を分けると、この面から他人のアカウントの在否を引き当てられる。
  @Test
  @DisplayName("任命(既存): 不在の user_id は 404 でなく母集団外と同じ 400 になること")
  void appointingUnknownUserIsIndistinguishableFromIneligible() {
    givenStore();
    givenManagerRole();
    when(repository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.appoint(STORE, appointExisting(5L)))
        .isInstanceOf(ServiceException.class)
        .isNotInstanceOf(NotFoundException.class)
        .hasMessageContaining("任命できません");
  }

  @Test
  @DisplayName("任命: user_id と新規作成の項目を同時に送ると撥ねること")
  void appointingWithBothShapesIsRejected() {
    givenStore();
    givenManagerRole();
    StoreManagerAppointRequest req = appointNew();
    req.setUserId(5L);

    assertThatThrownBy(() -> service.appoint(STORE, req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("同時に指定できません");
  }

  @Test
  @DisplayName("任命: 対象をひとつも指定しないと撥ねること")
  void appointingWithNeitherShapeIsRejected() {
    givenStore();
    givenManagerRole();

    assertThatThrownBy(() -> service.appoint(STORE, new StoreManagerAppointRequest()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("任命するアカウントを指定してください");
  }

  @Test
  @DisplayName("任命(新規): 店長ロール・当該店舗のみを担当する有効なスタッフが作られること")
  void creatingAndAppointingBuildsStoreScopedManager() {
    givenStore();
    givenManagerRole();
    when(repository.findByEmail("new-manager@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode(RAW_CREDENTIAL)).thenReturn("hash");
    givenSaveEchoes();

    service.appoint(STORE, appointNew());

    verify(repository).saveAndFlush(userCaptor.capture());
    PlatformUser created = userCaptor.getValue();
    assertThat(created.getUserType()).isEqualTo(UserType.STAFF);
    assertThat(created.getRoleIds()).containsExactly(MANAGER_ROLE);
    assertThat(created.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(created.getStoreIds()).containsExactly(STORE);
    assertThat(created.getEnabled()).isTrue();
    assertThat(created.getPassword()).as("素のパスワードを保存しないこと").isEqualTo("hash");
  }

  @Test
  @DisplayName("任命(新規): 既存のメールアドレスは重複として撥ねること")
  void creatingWithDuplicateEmailIsRejected() {
    givenStore();
    givenManagerRole();
    when(repository.findByEmail("new-manager@kizuna.test"))
        .thenReturn(
            Optional.of(
                staff(
                    9L, Set.of(CLERK_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(STORE), true)));

    assertThatThrownBy(() -> service.appoint(STORE, appointNew()))
        .isInstanceOf(DuplicateStaffEmailException.class);
  }

  @Test
  @DisplayName("任命(新規): 3 項目のいずれかが欠けていれば撥ねること")
  void creatingWithIncompleteAccountFieldsIsRejected() {
    givenStore();
    givenManagerRole();
    StoreManagerAppointRequest req = appointNew();
    req.setPassword(null);

    assertThatThrownBy(() -> service.appoint(STORE, req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("いずれも必須です");
  }

  @Test
  @DisplayName("解任: 当該店舗だけが担当から外れ、店長ロールは残ること")
  void dismissingRemovesOnlyTheStore() {
    givenStore();
    givenManagerRole();
    givenTarget(
        staff(
            5L,
            Set.of(MANAGER_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(STORE, OTHER_STORE),
            true));
    givenSaveEchoes();

    service.dismiss(STORE, 5L);

    verify(repository).saveAndFlush(userCaptor.capture());
    assertThat(userCaptor.getValue().getStoreIds()).containsExactly(OTHER_STORE);
    assertThat(userCaptor.getValue().getRoleIds())
        .as("外積のためロールを剥がすと他店の店長職まで消える — 解任は店舗集合しか触らないこと")
        .containsExactly(MANAGER_ROLE);
  }

  @Test
  @DisplayName("解任: 最後の担当店舗からの解任は自動降格へ倒さず撥ね、実在する出口へ誘導すること")
  void dismissingFromTheLastStoreIsRejectedWithGuidance() {
    givenStore();
    givenManagerRole();
    givenTarget(
        staff(
            5L,
            Set.of(MANAGER_ROLE, CLERK_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(STORE),
            true));

    assertThatThrownBy(() -> service.dismiss(STORE, 5L))
        .isInstanceOf(InvalidStoreScopeException.class)
        .hasMessageContaining("降格")
        .hasMessageContaining("アカウント管理");
    verify(repository, never()).saveAndFlush(ArgumentMatchers.any());
  }

  @Test
  @DisplayName("解任: 全店舗担当の店長は除去の形が無いため撥ね、実在する出口へ誘導すること")
  void dismissingAllStoresManagerIsRejectedWithGuidance() {
    givenStore();
    givenManagerRole();
    givenTarget(staff(5L, Set.of(MANAGER_ROLE), StoreScopeType.ALL_STORES, Set.of(), true));

    assertThatThrownBy(() -> service.dismiss(STORE, 5L))
        .isInstanceOf(InvalidStoreScopeException.class)
        .hasMessageContaining("降格")
        .hasMessageContaining("アカウント管理");
    verify(repository, never()).saveAndFlush(ArgumentMatchers.any());
  }

  @Test
  @DisplayName("降格: 店長ロールが店舗スタッフロールへ入れ替わり、他ロールと担当店舗はそのまま残ること")
  void demotingSwapsOnlyTheManagerRole() {
    givenStore();
    givenManagerRole();
    givenStaffRole();
    givenTarget(
        staff(
            5L,
            Set.of(MANAGER_ROLE, CLERK_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(STORE),
            true));
    givenSaveEchoes();

    service.demote(STORE, 5L);

    verify(repository).saveAndFlush(userCaptor.capture());
    PlatformUser demoted = userCaptor.getValue();
    assertThat(demoted.getRoleIds())
        .as("店長ロールだけを店舗スタッフロールへ入れ替え、他のロールは保持すること")
        .containsExactlyInAnyOrder(STAFF_ROLE, CLERK_ROLE);
    assertThat(demoted.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(demoted.getStoreIds()).as("担当店舗集合には触れないこと").containsExactly(STORE);
  }

  @Test
  @DisplayName("降格: 全店舗担当の店長も担当範囲を保ったまま店舗スタッフになること")
  void demotingAllStoresManagerKeepsTheScope() {
    givenStore();
    givenManagerRole();
    givenStaffRole();
    givenTarget(staff(5L, Set.of(MANAGER_ROLE), StoreScopeType.ALL_STORES, Set.of(), true));
    givenSaveEchoes();

    service.demote(STORE, 5L);

    verify(repository).saveAndFlush(userCaptor.capture());
    PlatformUser demoted = userCaptor.getValue();
    assertThat(demoted.getRoleIds()).containsExactly(STAFF_ROLE);
    assertThat(demoted.getStoreScopeType()).isEqualTo(StoreScopeType.ALL_STORES);
    assertThat(demoted.getStoreIds()).isEmpty();
  }

  // 降格は担当店舗を一切動かさないので、複数店を担当する店長を降ろすと無関係な店舗の職位まで落ちる。
  @Test
  @DisplayName("降格: 複数店舗を担当する店長は撥ね、解任へ誘導すること")
  void demotingMultiStoreManagerIsRejected() {
    givenStore();
    givenManagerRole();
    givenTarget(
        staff(
            5L,
            Set.of(MANAGER_ROLE),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(STORE, OTHER_STORE),
            true));

    assertThatThrownBy(() -> service.demote(STORE, 5L))
        .isInstanceOf(InvalidStoreScopeException.class)
        .hasMessageContaining("解任");
    verify(repository, never()).saveAndFlush(ArgumentMatchers.any());
  }

  @Test
  @DisplayName("降格: この店舗の店長でない対象は解任と同一の 404 になること")
  void demotingNonManagerIsNotFoundWithTheSameMessageAsDismissal() {
    givenStore();
    givenManagerRole();
    givenTarget(staff(5L, Set.of(CLERK_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(STORE), true));

    assertThatThrownBy(() -> service.demote(STORE, 5L))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("この店舗の店長が見つかりません: 5");
  }

  @Test
  @DisplayName("降格: 実在しない店舗は 404 になること")
  void demotingUnderUnknownStoreIsNotFound() {
    when(storeExistenceCheck.exists(STORE)).thenReturn(false);

    assertThatThrownBy(() -> service.demote(STORE, 5L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("解任: この店舗の店長でない対象は 404 になること")
  void dismissingNonManagerIsNotFound() {
    givenStore();
    givenManagerRole();
    givenTarget(staff(5L, Set.of(CLERK_ROLE), StoreScopeType.SPECIFIC_STORES, Set.of(STORE), true));

    assertThatThrownBy(() -> service.dismiss(STORE, 5L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("解任: 実在しない店舗は 404 になること")
  void dismissingUnderUnknownStoreIsNotFound() {
    when(storeExistenceCheck.exists(STORE)).thenReturn(false);

    assertThatThrownBy(() -> service.dismiss(STORE, 5L)).isInstanceOf(NotFoundException.class);
  }
}
