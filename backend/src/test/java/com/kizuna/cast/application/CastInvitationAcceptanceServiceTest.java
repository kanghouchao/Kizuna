package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.api.dto.CastAcceptanceResponse;
import com.kizuna.cast.api.dto.CastInvitationAcceptRequest;
import com.kizuna.cast.api.dto.CastInvitationDetailResponse;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastInvitationStateException;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CastInvitationAcceptanceServiceTest {

  @Mock private CastInvitationRepository castInvitationRepository;
  @Mock private CastRepository castRepository;
  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private CastInvitationAcceptanceService service;

  private CastInvitation invitation(
      String castId, long tenantId, CastInvitation.Status status, OffsetDateTime expiresAt) {
    CastInvitation invitation =
        CastInvitation.builder()
            .castId(castId)
            .token("tok")
            .status(status)
            .expiresAt(expiresAt)
            .build();
    invitation.setTenantId(tenantId);
    return invitation;
  }

  private Cast cast(String id, String name) {
    Cast cast = Cast.builder().name(name).build();
    cast.setId(id);
    return cast;
  }

  private PlatformUser user(long id, PlatformRole role, Set<Long> storeIds) {
    PlatformUser user =
        PlatformUser.builder()
            .email("cast@example.com")
            .password("x")
            .displayName("既存キャスト")
            .enabled(true)
            .role(role)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(storeIds)
            .build();
    user.setId(id);
    return user;
  }

  private PlatformUser allStoresUser(long id) {
    PlatformUser user =
        PlatformUser.builder()
            .email("cast@example.com")
            .password("x")
            .displayName("全店キャスト")
            .enabled(true)
            .role(PlatformRole.CAST)
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(id);
    return user;
  }

  private CastInvitationAcceptRequest acceptRequest(String email) {
    CastInvitationAcceptRequest request = new CastInvitationAcceptRequest();
    request.setEmail(email);
    request.setPassword("password1");
    request.setDisplayName("花子");
    return request;
  }

  private void stubTenant(long tenantId, String name) {
    Tenant tenant = new Tenant(name, "tenant-" + tenantId, null);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
  }

  @Test
  void view_returnsValidForPendingNotExpired() {
    when(castInvitationRepository.findByToken("tok"))
        .thenReturn(
            Optional.of(
                invitation(
                    "c1", 1L, CastInvitation.Status.PENDING, OffsetDateTime.now().plusHours(1))));
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", "花子档案")));
    stubTenant(1L, "店舗A");

    CastInvitationDetailResponse response = service.view("tok");

    assertThat(response.status()).isEqualTo("VALID");
    assertThat(response.storeName()).isEqualTo("店舗A");
    assertThat(response.castName()).isEqualTo("花子档案");
  }

  @Test
  void view_returnsExpiredForPendingExpired() {
    when(castInvitationRepository.findByToken("tok"))
        .thenReturn(
            Optional.of(
                invitation(
                    "c1", 1L, CastInvitation.Status.PENDING, OffsetDateTime.now().minusHours(1))));
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", "花子档案")));
    stubTenant(1L, "店舗A");

    assertThat(service.view("tok").status()).isEqualTo("EXPIRED");
  }

  @Test
  void view_returnsUsedForAccepted() {
    when(castInvitationRepository.findByToken("tok"))
        .thenReturn(
            Optional.of(
                invitation(
                    "c1", 1L, CastInvitation.Status.ACCEPTED, OffsetDateTime.now().plusHours(1))));
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", "花子档案")));
    stubTenant(1L, "店舗A");

    assertThat(service.view("tok").status()).isEqualTo("USED");
  }

  @Test
  void acceptAsNewUser_createsCastIdentityLinksAndAccepts() {
    CastInvitation invitation =
        invitation("c1", 1L, CastInvitation.Status.PENDING, OffsetDateTime.now().plusHours(1));
    Cast cast = cast("c1", "花子档案");
    when(castInvitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast));
    when(platformUserRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(castInvitationRepository.claimPending(
            any(), any(), eq(CastInvitation.Status.PENDING), eq(CastInvitation.Status.ACCEPTED)))
        .thenReturn(1);
    when(passwordEncoder.encode("password1")).thenReturn("encoded");
    when(platformUserRepository.save(any()))
        .thenAnswer(
            call -> {
              PlatformUser saved = call.getArgument(0);
              saved.setId(42L);
              return saved;
            });
    stubTenant(1L, "店舗A");

    CastAcceptanceResponse response =
        service.acceptAsNewUser("tok", acceptRequest("New@Example.com"));

    assertThat(response.storeName()).isEqualTo("店舗A");
    // 招待の状態遷移は条件付き UPDATE（claimPending）が担う。エンティティは受諾後に変更しない。
    verify(castInvitationRepository)
        .claimPending(
            any(), any(), eq(CastInvitation.Status.PENDING), eq(CastInvitation.Status.ACCEPTED));
    assertThat(cast.getPlatformUserId()).isEqualTo(42L);

    ArgumentCaptor<PlatformUser> captor = ArgumentCaptor.forClass(PlatformUser.class);
    verify(platformUserRepository).save(captor.capture());
    PlatformUser saved = captor.getValue();
    assertThat(saved.getRole()).isEqualTo(PlatformRole.CAST);
    assertThat(saved.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(saved.getStoreIds()).containsExactly(1L);
    assertThat(saved.getPassword()).isEqualTo("encoded");
  }

  @Test
  void acceptAsNewUser_rejectsDuplicateEmailWithoutSideEffects() {
    CastInvitation invitation =
        invitation("c1", 1L, CastInvitation.Status.PENDING, OffsetDateTime.now().plusHours(1));
    when(castInvitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", "花子档案")));
    when(platformUserRepository.findByEmail("dup@example.com"))
        .thenReturn(Optional.of(user(9L, PlatformRole.CAST, Set.of(1L))));

    assertThatThrownBy(() -> service.acceptAsNewUser("tok", acceptRequest("Dup@Example.com")))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("既に登録されています");
    assertThat(invitation.getStatus()).isEqualTo(CastInvitation.Status.PENDING);
    verify(platformUserRepository, never()).save(any());
  }

  @Test
  void acceptAsNewUser_rejectsExpiredToken() {
    when(castInvitationRepository.findByToken("tok"))
        .thenReturn(
            Optional.of(
                invitation(
                    "c1", 1L, CastInvitation.Status.PENDING, OffsetDateTime.now().minusHours(1))));
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", "花子档案")));
    when(platformUserRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.acceptAsNewUser("tok", acceptRequest("New@Example.com")))
        .isInstanceOf(CastInvitationStateException.class);
    verify(platformUserRepository, never()).save(any());
  }

  @Test
  void acceptAsNewUser_rejectsWhenInvitationAlreadyClaimed() {
    // 読み込み時点では PENDING・未期限だが、並行受諾で先着に奪われ claimPending が 0 行になる状況を模擬する。
    CastInvitation invitation =
        invitation("c1", 1L, CastInvitation.Status.PENDING, OffsetDateTime.now().plusHours(1));
    when(castInvitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", "花子档案")));
    when(platformUserRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(castInvitationRepository.claimPending(
            any(), any(), eq(CastInvitation.Status.PENDING), eq(CastInvitation.Status.ACCEPTED)))
        .thenReturn(0);

    // クレームが 0 行なら受諾権を確定できず、身分作成の前に弾かれること（二重登録の防止）。
    assertThatThrownBy(() -> service.acceptAsNewUser("tok", acceptRequest("New@Example.com")))
        .isInstanceOf(CastInvitationStateException.class);
    verify(platformUserRepository, never()).save(any());
  }

  @Test
  void acceptAsExistingUser_addsStoreAndLinks() {
    CastInvitation invitation =
        invitation("c2", 2L, CastInvitation.Status.PENDING, OffsetDateTime.now().plusHours(1));
    Cast cast = cast("c2", "花子档案（店舗B）");
    PlatformUser existing = user(7L, PlatformRole.CAST, Set.of(1L));
    when(castInvitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
    when(castRepository.findById("c2")).thenReturn(Optional.of(cast));
    when(platformUserRepository.findByEmail("cast@example.com")).thenReturn(Optional.of(existing));
    when(castInvitationRepository.claimPending(
            any(), any(), eq(CastInvitation.Status.PENDING), eq(CastInvitation.Status.ACCEPTED)))
        .thenReturn(1);
    stubTenant(2L, "店舗B");

    CastAcceptanceResponse response = service.acceptAsExistingUser("tok", "cast@example.com");

    assertThat(response.storeName()).isEqualTo("店舗B");
    assertThat(existing.getStoreIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(existing.getRole()).isEqualTo(PlatformRole.CAST);
    assertThat(cast.getPlatformUserId()).isEqualTo(7L);
    // 招待の状態遷移は条件付き UPDATE（claimPending）が担う。エンティティは受諾後に変更しない。
    verify(castInvitationRepository)
        .claimPending(
            any(), any(), eq(CastInvitation.Status.PENDING), eq(CastInvitation.Status.ACCEPTED));
    verify(platformUserRepository).save(existing);
  }

  @Test
  void acceptAsExistingUser_preservesAllStoresScopeWithoutDowngrade() {
    CastInvitation invitation =
        invitation("c2", 2L, CastInvitation.Status.PENDING, OffsetDateTime.now().plusHours(1));
    Cast cast = cast("c2", "花子档案（全店）");
    PlatformUser existing = allStoresUser(7L);
    when(castInvitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
    when(castRepository.findById("c2")).thenReturn(Optional.of(cast));
    when(platformUserRepository.findByEmail("cast@example.com")).thenReturn(Optional.of(existing));
    when(castInvitationRepository.claimPending(
            any(), any(), eq(CastInvitation.Status.PENDING), eq(CastInvitation.Status.ACCEPTED)))
        .thenReturn(1);
    stubTenant(2L, "店舗B");

    CastAcceptanceResponse response = service.acceptAsExistingUser("tok", "cast@example.com");

    assertThat(response.storeName()).isEqualTo("店舗B");
    // ALL_STORES 授権は招待受諾で SPECIFIC_STORES へ降格させない（全店アクセス権を1店舗に狭めない）。
    assertThat(existing.getStoreScopeType()).isEqualTo(StoreScopeType.ALL_STORES);
    assertThat(existing.getStoreIds()).isEmpty();
    assertThat(cast.getPlatformUserId()).isEqualTo(7L);
    // 授権に変更がないため PlatformUser は永続化しない（档案の紐づけのみ）。
    verify(platformUserRepository, never()).save(any());
  }

  @Test
  void acceptAsExistingUser_rejectsNonCastRole() {
    CastInvitation invitation =
        invitation("c2", 2L, CastInvitation.Status.PENDING, OffsetDateTime.now().plusHours(1));
    when(castInvitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
    when(castRepository.findById("c2")).thenReturn(Optional.of(cast("c2", "花子档案")));
    when(platformUserRepository.findByEmail("staff@example.com"))
        .thenReturn(Optional.of(user(8L, PlatformRole.STORE_STAFF, Set.of(1L))));

    assertThatThrownBy(() -> service.acceptAsExistingUser("tok", "staff@example.com"))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(invitation.getStatus()).isEqualTo(CastInvitation.Status.PENDING);
    verify(platformUserRepository, never()).save(any());
  }
}
