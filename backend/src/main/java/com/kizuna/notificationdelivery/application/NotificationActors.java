package com.kizuna.notificationdelivery.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationActors {
  private final UserRepository users;
  private final RoleRepository roles;

  /** 店舗の行は店舗の権限、店舗を持たない会員宛の行は会員宛通知の権限で操作する。 */
  public enum Action {
    VIEW(PermissionCode.NOTIFICATION_VIEW, PermissionCode.MEMBER_NOTIFICATION_VIEW),
    MANAGE(PermissionCode.NOTIFICATION_MANAGE, PermissionCode.MEMBER_NOTIFICATION_MANAGE),
    SEND(PermissionCode.NOTIFICATION_SEND, PermissionCode.MEMBER_NOTIFICATION_SEND);

    private final PermissionCode store;
    private final PermissionCode member;

    Action(PermissionCode store, PermissionCode member) {
      this.store = store;
      this.member = member;
    }

    PermissionCode code(Long storeId) {
      return storeId == null ? member : store;
    }
  }

  /** storeId が null なら会員宛の行で、店舗集合ではなく権限だけで判定する。 */
  public AuditActor require(Long storeId, String email, Action action) {
    var user = users.findByEmail(email).orElseThrow(NotificationActors::denied);
    if (user.getUserType() != UserType.STAFF
        || !Boolean.TRUE.equals(user.getEnabled())
        || (storeId != null && !user.authorizes(storeId))
        || Collections.disjoint(
            user.getRoleIds(), roles.findIdsByPermissionCode(Action.VIEW.code(storeId).name()))
        || Collections.disjoint(
            user.getRoleIds(), roles.findIdsByPermissionCode(action.code(storeId).name())))
      throw denied();
    return new AuditActor(user.getId(), "STAFF", user.getDisplayName());
  }

  private static AccessDeniedException denied() {
    return new AccessDeniedException("通知の操作権限がありません");
  }
}
