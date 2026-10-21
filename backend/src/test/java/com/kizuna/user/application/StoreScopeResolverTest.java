package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.user.domain.EmergencyElevation;
import com.kizuna.user.domain.EmergencyElevationRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserType;
import java.math.BigInteger;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class StoreScopeResolverTest {
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-21T00:00:00Z");

  private final PlatformUserRepository platformUsers = mock(PlatformUserRepository.class);
  private final EmergencyElevationRepository elevations = mock(EmergencyElevationRepository.class);
  private final StoreScopeResolver resolver =
      new StoreScopeResolver(
          platformUsers, elevations, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

  private static Jwt token(Object elevationId) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token").header("alg", "none").subject("actor@kizuna.test");
    if (elevationId != null) builder.claim("elevationId", elevationId);
    return builder.build();
  }

  private static PlatformUser scope(StoreScopeType type, Set<Long> storeIds) {
    User user =
        User.builder()
            .email("actor@kizuna.test")
            .password("hash")
            .displayName("担当")
            .enabled(true)
            .userType(UserType.PLATFORM_USER)
            .roleIds(Set.of(1L))
            .build();
    user.setId(7L);
    return PlatformUser.builder().user(user).storeScopeType(type).storeIds(storeIds).build();
  }

  @Test
  @DisplayName("店舗作用域の行を持たない主体（CAST / MEMBER）は null（fail-closed の入力）")
  void subjectWithoutScopeResolvesToNull() {
    when(platformUsers.findByUserEmail("actor@kizuna.test")).thenReturn(Optional.empty());

    assertThat(resolver.resolve(token(null))).isNull();
    verifyNoInteractions(elevations);
  }

  @Test
  @DisplayName("通常トークンは本人の行をそのまま作用域にし、昇格記録は読まない")
  void ordinaryTokenResolvesFromTheRow() {
    when(platformUsers.findByUserEmail("actor@kizuna.test"))
        .thenReturn(Optional.of(scope(StoreScopeType.SPECIFIC_STORES, Set.of(1L, 2L))));

    var scope = resolver.resolve(token(null));

    assertThat(scope.allStores()).isFalse();
    assertThat(scope.storeIds()).containsExactlyInAnyOrder(1L, 2L);
    verifyNoInteractions(elevations);
  }

  @Test
  @DisplayName("有効な昇格は本人の個別店舗集合に対象店舗を加えた SPECIFIC_STORES になる")
  void activeElevationAddsTargetStore() {
    when(platformUsers.findByUserEmail("actor@kizuna.test"))
        .thenReturn(Optional.of(scope(StoreScopeType.SPECIFIC_STORES, Set.of(1L))));
    when(elevations.findById(10L))
        .thenReturn(Optional.of(EmergencyElevation.activate(7L, 2L, "理由", NOW.minusMinutes(1))));

    var scope = resolver.resolve(token(10L));

    assertThat(scope.allStores()).isFalse();
    assertThat(scope.storeIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  @DisplayName("全店舗担当者でも昇格中は対象店舗だけに縮まる（昇格は一店舗の店舗操作権限の一時取得）")
  void allStoresActivatorIsNarrowedToTheTargetStore() {
    when(platformUsers.findByUserEmail("actor@kizuna.test"))
        .thenReturn(Optional.of(scope(StoreScopeType.ALL_STORES, Set.of())));
    when(elevations.findById(10L))
        .thenReturn(Optional.of(EmergencyElevation.activate(7L, 2L, "理由", NOW.minusMinutes(1))));

    var scope = resolver.resolve(token(10L));

    assertThat(scope.allStores()).isFalse();
    assertThat(scope.authorizes(2L)).isTrue();
    assertThat(scope.authorizes(3L)).isFalse();
  }

  @Test
  @DisplayName("撤回済み・期限切れ・未来・他人・不在の昇格は基底へ戻さず null（fail-closed）")
  void invalidElevationResolvesToNullInsteadOfTheBaseScope() {
    when(platformUsers.findByUserEmail("actor@kizuna.test"))
        .thenReturn(Optional.of(scope(StoreScopeType.ALL_STORES, Set.of())));
    var revoked = EmergencyElevation.activate(7L, 2L, "理由", NOW.minusMinutes(1));
    revoked.revoke(7L, NOW);
    for (var elevation :
        List.of(
            revoked,
            EmergencyElevation.activate(7L, 2L, "理由", NOW.minusMinutes(60)),
            EmergencyElevation.activate(7L, 2L, "理由", NOW.plusMinutes(1)),
            EmergencyElevation.activate(9L, 2L, "理由", NOW.minusMinutes(1)))) {
      when(elevations.findById(10L)).thenReturn(Optional.of(elevation));
      assertThat(resolver.resolve(token(10L))).isNull();
    }
    when(elevations.findById(10L)).thenReturn(Optional.empty());
    assertThat(resolver.resolve(token(10L))).isNull();
  }

  @Test
  @DisplayName("昇格 claim の形が不正なら記録を読まずに null")
  void malformedElevationClaimResolvesToNull() {
    when(platformUsers.findByUserEmail("actor@kizuna.test"))
        .thenReturn(Optional.of(scope(StoreScopeType.ALL_STORES, Set.of())));
    for (Object malformed : List.of("not-an-id", 0L, -1L, 1.5d, BigInteger.ONE)) {
      assertThat(resolver.resolve(token(malformed))).isNull();
    }
    verifyNoInteractions(elevations);
  }
}
