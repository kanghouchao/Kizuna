package com.kizuna.review.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.UserType;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewActors {
  private final PlatformUserRepository users;
  private final RoleRepository roles;
  private final StoreContext store;

  public AuditActor require(String email, PermissionCode action) {
    var user = users.findByEmail(email).orElseThrow(ReviewActors::denied);
    if (!store.hasStoreId()
        || user.getUserType() != UserType.STAFF
        || !Boolean.TRUE.equals(user.getEnabled())
        || !user.authorizes(store.getStoreId())
        || Collections.disjoint(
            user.getRoleIds(), roles.findIdsByPermissionCode(PermissionCode.REVIEW_VIEW.name()))
        || Collections.disjoint(user.getRoleIds(), roles.findIdsByPermissionCode(action.name())))
      throw denied();
    return new AuditActor(user.getId(), "STAFF", user.getDisplayName());
  }

  private static AccessDeniedException denied() {
    return new AccessDeniedException("口コミの操作権限がありません");
  }
}
