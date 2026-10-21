package com.kizuna.user.application;

import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.user.domain.EmergencyElevation;
import com.kizuna.user.domain.EmergencyElevationRepository;
import com.kizuna.user.domain.EmergencyElevationStatus;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 認証済みトークンの主体から、要求ごとに店舗作用域（{@link StoreScope}）を DB で解決する。トークンには載せないので、店舗集合の再割当と昇格の撤回は
 * 次の要求から効く。解決不能（行を持たない CAST / MEMBER、照合に通らない昇格）は null で、呼び出し側が fail-closed に拒否する。
 *
 * <p>昇格 claim（{@code elevationId}）を持つトークンは発動記録を照合し、通れば本人の個別店舗集合に対象店舗を加えた SPECIFIC_STORES にする —
 * 全店舗担当者でも昇格中は対象店舗だけに縮める（昇格は一店舗の店舗操作権限の一時取得）。照合に通らない昇格 claim は基底の作用域へ 戻さず null にする。
 */
@Service
@RequiredArgsConstructor
public class StoreScopeResolver {

  private final PlatformUserRepository platformUsers;
  private final EmergencyElevationRepository elevations;
  private final Clock clock;

  @Transactional(readOnly = true)
  public StoreScope resolve(Jwt jwt) {
    PlatformUser scope = platformUsers.findByUserEmail(jwt.getSubject()).orElse(null);
    if (scope == null) {
      return null;
    }
    if (!jwt.getClaims().containsKey("elevationId")) {
      return scope.toStoreScope();
    }
    EmergencyElevation elevation = activeElevation(jwt.getClaim("elevationId"), scope);
    if (elevation == null) {
      return null;
    }
    Set<Long> storeIds = new HashSet<>(scope.getStoreIds());
    storeIds.add(elevation.getTargetStoreId());
    return new StoreScope(false, Set.copyOf(storeIds));
  }

  private EmergencyElevation activeElevation(Object claim, PlatformUser scope) {
    if (!(claim instanceof Number id)
        || !(id instanceof Long || id instanceof Integer)
        || id.longValue() <= 0) {
      return null;
    }
    EmergencyElevation elevation = elevations.findById(id.longValue()).orElse(null);
    OffsetDateTime now = OffsetDateTime.now(clock);
    if (elevation == null
        || !scope.getUserId().equals(elevation.getActivatedBy())
        || elevation.getStatus() != EmergencyElevationStatus.ACTIVE
        || now.isBefore(elevation.getActivatedAt())
        || !now.isBefore(elevation.getExpiresAt())) {
      return null;
    }
    return elevation;
  }
}
