package com.kizuna.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.review.application.ReviewActors;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.domain.EmergencyElevation;
import com.kizuna.user.domain.EmergencyElevationRepository;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.UserType;
import java.math.BigInteger;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ReviewActorsTest {
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T00:00:00Z");
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final StoreContext store = mock(StoreContext.class);
  private final EmergencyElevationRepository elevations = mock(EmergencyElevationRepository.class);
  private final PlatformUser user = mock(PlatformUser.class);
  private final ReviewActors actors =
      new ReviewActors(
          users, roles, store, elevations, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

  @BeforeEach
  void setup() {
    when(users.findByEmail("actor")).thenReturn(Optional.of(user));
    when(user.getId()).thenReturn(1L);
    when(user.getEmail()).thenReturn("actor");
    when(user.getDisplayName()).thenReturn("担当者");
    when(user.getUserType()).thenReturn(UserType.STAFF);
    when(user.getEnabled()).thenReturn(true);
    when(store.hasStoreId()).thenReturn(true);
    when(store.getStoreId()).thenReturn(2L);
    when(user.getRoleIds()).thenReturn(Set.of(3L));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void ordinaryAccessRequiresCurrentStoreViewAndOperationGrants() {
    denied();
    when(user.authorizes(2L)).thenReturn(true);
    denied();
    when(roles.findIdsByPermissionCode("REVIEW_VIEW")).thenReturn(Set.of(3L));
    denied();
    when(roles.findIdsByPermissionCode("REVIEW_MANAGE")).thenReturn(Set.of(3L));
    assertThat(actors.require("actor", PermissionCode.REVIEW_MANAGE).id()).isEqualTo(1L);
  }

  @Test
  void activeElevationGrantsStoreOperationsOnlyToItsEnabledStaffActor() {
    token(10L, "actor");
    when(elevations.findById(10L))
        .thenReturn(Optional.of(EmergencyElevation.activate(1L, 2L, "理由", NOW.minusMinutes(1))));
    for (var permission :
        List.of(
            PermissionCode.REVIEW_VIEW,
            PermissionCode.REVIEW_MANAGE,
            PermissionCode.REVIEW_MODERATE,
            PermissionCode.ORDER_MANAGE))
      assertThat(actors.require("actor", permission).id()).isEqualTo(1L);
    assertThatThrownBy(() -> actors.require("actor", PermissionCode.AUDIT_VIEW))
        .isInstanceOf(AccessDeniedException.class);
    when(user.getEnabled()).thenReturn(false);
    denied();
    when(user.getEnabled()).thenReturn(true);
    when(user.getUserType()).thenReturn(UserType.SERVICE);
    denied();
    when(user.getUserType()).thenReturn(UserType.STAFF);
    when(store.hasStoreId()).thenReturn(false);
    denied();
  }

  @Test
  void expiredRevokedFutureAndForeignElevationsFailBeforeNormalGrantFallback() {
    when(user.authorizes(2L)).thenReturn(true);
    when(roles.findIdsByPermissionCode("REVIEW_VIEW")).thenReturn(Set.of(3L));
    when(roles.findIdsByPermissionCode("REVIEW_MANAGE")).thenReturn(Set.of(3L));
    token(10L, "actor");
    var revoked = EmergencyElevation.activate(1L, 2L, "理由", NOW.minusMinutes(1));
    revoked.revoke(1L, NOW);
    for (var elevation :
        List.of(
            revoked,
            EmergencyElevation.activate(1L, 2L, "理由", NOW.minusMinutes(60)),
            EmergencyElevation.activate(1L, 2L, "理由", NOW.plusMinutes(1)),
            EmergencyElevation.activate(9L, 2L, "理由", NOW.minusMinutes(1)),
            EmergencyElevation.activate(1L, 9L, "理由", NOW.minusMinutes(1)))) {
      when(elevations.findById(10L)).thenReturn(Optional.of(elevation));
      denied();
    }
    when(elevations.findById(10L)).thenReturn(Optional.empty());
    denied();
    when(elevations.findById(10L))
        .thenReturn(Optional.of(EmergencyElevation.activate(1L, 2L, "理由", NOW.minusMinutes(1))));
    for (var malformed :
        List.of(
            "not-an-id",
            10.5,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            BigInteger.ONE.shiftLeft(64).add(BigInteger.TEN),
            0L,
            -1L)) {
      token(malformed, "actor");
      denied();
    }
    token(10, "actor");
    assertThat(actors.require("actor", PermissionCode.REVIEW_MANAGE).id()).isEqualTo(1L);
    token(10L, "another-actor");
    denied();
  }

  private void token(Object elevation, String subject) {
    var token =
        Jwt.withTokenValue("isolated-test")
            .header("alg", "none")
            .subject(subject)
            .claim("elevationId", elevation)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(token, List.of()));
  }

  private void denied() {
    assertThatThrownBy(() -> actors.require("actor", PermissionCode.REVIEW_MANAGE))
        .isInstanceOf(AccessDeniedException.class);
  }
}
