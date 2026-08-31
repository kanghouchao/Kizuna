package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.auth.api.dto.EmergencyElevationActivationResponse;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.PlatformUserDetails;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.domain.EmergencyElevation;
import com.kizuna.user.domain.EmergencyElevationRepository;
import com.kizuna.user.domain.EmergencyElevationStatus;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserCredentialsChanged;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class EmergencyElevationServiceTest {

  private static final String REASON = "決済障害の一次対応";

  @Mock private EmergencyElevationRepository elevationRepository;
  @Mock private PlatformUserRepository userRepository;
  @Mock private PlatformAuthService authService;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private Authentication authentication;

  @InjectMocks private EmergencyElevationService service;

  private static PlatformUser user(String email, long id) {
    PlatformUser user =
        PlatformUser.builder()
            .email(email)
            .password("stored-hash")
            .displayName("HQ管理者")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(10L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(id);
    return user;
  }

  private void stubSuccessfulReauthentication(PlatformUser operator) {
    when(authenticationManager.authenticate(any())).thenReturn(authentication);
    when(authentication.getPrincipal()).thenReturn(new PlatformUserDetails(operator));
  }

  private static EmergencyElevation elevationOf(long id, long activatedBy) {
    EmergencyElevation elevation =
        EmergencyElevation.activate(activatedBy, 3L, REASON, OffsetDateTime.now());
    elevation.setId(id);
    return elevation;
  }

  @Test
  @DisplayName("再認証に失敗した発動が記録もトークンも残さないこと")
  void failedReauthenticationLeavesNoRecordAndNoToken() {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("資格情報が不正です"));

    assertThatThrownBy(() -> service.activate("admin@kizuna.test", 3L, REASON, "wrong"))
        .isInstanceOf(BadCredentialsException.class);

    // 書き込みより先に再認証を置いているので、記録は巻き戻し以前に存在しない。
    verify(elevationRepository, never()).saveAndFlush(any());
    verify(elevationRepository, never()).save(any());
    verify(authService, never()).issueElevatedTokenFor(any(), any());
  }

  @Test
  @DisplayName("発動が記録を確定させてから昇格トークンを発行すること")
  void activatePersistsRecordBeforeIssuingToken() {
    PlatformUser operator = user("admin@kizuna.test", 7L);
    stubSuccessfulReauthentication(operator);
    when(elevationRepository.saveAndFlush(any(EmergencyElevation.class)))
        .thenAnswer(
            invocation -> {
              EmergencyElevation saved = invocation.getArgument(0);
              saved.setId(99L);
              return saved;
            });
    when(authService.issueElevatedTokenFor(eq(operator), any(EmergencyElevation.class)))
        .thenReturn(new Token("elevated-token", 1_893_456_000_000L));

    EmergencyElevationActivationResponse res =
        service.activate("admin@kizuna.test", 3L, REASON, "correct");

    assertThat(res.id()).isEqualTo(99L);
    assertThat(res.token()).isEqualTo("elevated-token");
    assertThat(res.expiresAt()).isEqualTo(1_893_456_000_000L);

    // claim に載せる id が要るので、発行は記録の確定より後でなければならない。
    ArgumentCaptor<EmergencyElevation> captor = ArgumentCaptor.forClass(EmergencyElevation.class);
    InOrder inOrder = inOrder(elevationRepository, authService);
    inOrder.verify(elevationRepository).saveAndFlush(captor.capture());
    inOrder.verify(authService).issueElevatedTokenFor(eq(operator), any());
    EmergencyElevation elevation = captor.getValue();
    assertThat(elevation.getActivatedBy()).isEqualTo(7L);
    assertThat(elevation.getTargetStoreId()).isEqualTo(3L);
    assertThat(elevation.getReason()).isEqualTo(REASON);
    assertThat(elevation.getStatus()).isEqualTo(EmergencyElevationStatus.ACTIVE);
  }

  @Test
  @DisplayName("実在しない店舗を宛先にした発動が 404 の例外へ写ること")
  void unknownTargetStoreIsTranslatedToNotFound() {
    stubSuccessfulReauthentication(user("admin@kizuna.test", 7L));
    when(elevationRepository.saveAndFlush(any(EmergencyElevation.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException("insert or update violates foreign key constraint"),
                    "fk_t_emergency_elevations_store")));

    assertThatThrownBy(() -> service.activate("admin@kizuna.test", 999L, REASON, "correct"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("撤回が記録を閉じ、発動者の資格情報の版を進めた後の値を事象で運ぶこと")
  void revokeClosesRecordAndBumpsActivatorCredentialVersion() {
    PlatformUser activator = user("activator@kizuna.test", 7L);
    PlatformUser revoker = user("revoker@kizuna.test", 8L);
    EmergencyElevation elevation = elevationOf(99L, 7L);
    when(elevationRepository.findById(99L)).thenReturn(Optional.of(elevation));
    when(userRepository.findByEmail("revoker@kizuna.test")).thenReturn(Optional.of(revoker));
    when(userRepository.findById(7L)).thenReturn(Optional.of(activator));

    service.revoke(99L, "revoker@kizuna.test");

    assertThat(elevation.getStatus()).isEqualTo(EmergencyElevationStatus.REVOKED);
    assertThat(elevation.getRevokedBy()).isEqualTo(8L);
    verify(elevationRepository).save(elevation);
    // 版を進める宛先は撤回者ではなく発動者 — 失効させたいのは発動者の手にある昇格トークンである。
    assertThat(activator.getCredentialVersion()).isEqualTo(1L);
    assertThat(revoker.getCredentialVersion()).isZero();
    verify(userRepository).save(activator);
    verify(eventPublisher)
        .publishEvent(new PlatformUserCredentialsChanged("activator@kizuna.test", 1L));
  }

  @Test
  @DisplayName("存在しない昇格の撤回が 404 の例外になり、版を進めないこと")
  void revokeOfUnknownElevationIsNotFound() {
    when(elevationRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.revoke(404L, "revoker@kizuna.test"))
        .isInstanceOf(NotFoundException.class);

    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }
}
