package com.kizuna.survey.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.domain.EmergencyElevationRepository;
import com.kizuna.user.domain.EmergencyElevationStatus;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SurveyActors {
  private final UserRepository users;
  private final PlatformUserRepository platformUsers;
  private final RoleRepository roles;
  private final StoreContext store;
  private final EmergencyElevationRepository elevations;
  private final Clock clock;

  public AuditActor require(String email, PermissionCode action) {
    var user = users.findByEmail(email).orElseThrow(SurveyActors::denied);
    if (!store.hasStoreId()
        || user.getUserType() != UserType.PLATFORM_USER
        || !Boolean.TRUE.equals(user.getEnabled())) throw denied();
    if (!elevated(user, action)
        && (!authorizes(user, store.getStoreId())
            || Collections.disjoint(
                user.getRoleIds(), roles.findIdsByPermissionCode(PermissionCode.SURVEY_VIEW.name()))
            || Collections.disjoint(
                user.getRoleIds(), roles.findIdsByPermissionCode(action.name())))) throw denied();
    return new AuditActor(user.getId(), UserType.PLATFORM_USER.name(), user.getDisplayName());
  }

  /** 店舗作用域は本人の行（PlatformUser）から毎回読む。行が無い種別（CAST / MEMBER）は何も授権しない。 */
  private boolean authorizes(User user, Long storeId) {
    return platformUsers
        .findByUserId(user.getId())
        .filter(scope -> scope.authorizes(storeId))
        .isPresent();
  }

  /** 昇格による店舗権限は通常ロールと独立するため、発動記録の主体・店舗・有効区間を毎回照合する。 */
  private boolean elevated(User user, PermissionCode action) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwt)
        || !jwt.getToken().getClaims().containsKey("elevationId")) return false;
    if (!jwt.isAuthenticated()
        || !user.getEmail().equals(jwt.getName())
        || action.getConsole() != PermissionCode.Console.STORE
        || !(jwt.getToken().getClaim("elevationId") instanceof Number id)
        || !(id instanceof Long || id instanceof Integer)
        || id.longValue() <= 0) throw denied();
    var elevation = elevations.findById(id.longValue()).orElseThrow(SurveyActors::denied);
    var now = OffsetDateTime.now(clock);
    if (!user.getId().equals(elevation.getActivatedBy())
        || !store.getStoreId().equals(elevation.getTargetStoreId())
        || elevation.getStatus() != EmergencyElevationStatus.ACTIVE
        || now.isBefore(elevation.getActivatedAt())
        || !now.isBefore(elevation.getExpiresAt())) throw denied();
    return true;
  }

  private static AccessDeniedException denied() {
    return new AccessDeniedException("アンケートの操作権限がありません");
  }
}
