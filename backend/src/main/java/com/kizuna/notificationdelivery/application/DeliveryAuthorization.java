package com.kizuna.notificationdelivery.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryAuthorization {
  private final ServiceExecutionIdentityService identities;

  /** 店舗の行はサービス ID の店舗集合に入っていることを求める。店舗を持たないグループ級の行は権限だけで送る。 */
  public AuditActor require(Long serviceId, Long storeId) {
    identities.requireService(serviceId, PermissionCode.TASK_EXECUTE);
    var service = identities.requireServiceScope(serviceId, PermissionCode.NOTIFICATION_DELIVER);
    if (storeId != null && !service.stores().authorizes(storeId))
      throw new AccessDeniedException("この店舗の通知を送る権限がありません");
    return service.actor();
  }
}
