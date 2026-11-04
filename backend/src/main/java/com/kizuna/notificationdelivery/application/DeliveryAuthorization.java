package com.kizuna.notificationdelivery.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryAuthorization {
  private final ServiceExecutionIdentityService identities;

  /** 送信はグループ全体の処理なので、サービス ID に全店舗の授権を求める。 */
  public AuditActor require(Long serviceId) {
    identities.requireService(serviceId, PermissionCode.TASK_EXECUTE);
    return identities.requireService(serviceId, PermissionCode.NOTIFICATION_DELIVER);
  }
}
