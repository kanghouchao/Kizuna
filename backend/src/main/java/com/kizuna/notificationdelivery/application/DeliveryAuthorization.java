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

  public AuditActor require(Long serviceId, Long storeId) {
    identities.requireService(serviceId, PermissionCode.TASK_EXECUTE, storeId);
    return identities.requireService(serviceId, PermissionCode.NOTIFICATION_DELIVER, storeId);
  }
}
