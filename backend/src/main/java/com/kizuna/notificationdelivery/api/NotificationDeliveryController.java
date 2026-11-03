package com.kizuna.notificationdelivery.api;

import com.kizuna.notificationdelivery.api.dto.DeliveryActionRequest;
import com.kizuna.notificationdelivery.api.dto.DeliveryAttemptResponse;
import com.kizuna.notificationdelivery.api.dto.DeliveryRequest;
import com.kizuna.notificationdelivery.api.dto.DeliveryResponse;
import com.kizuna.notificationdelivery.api.dto.DeliverySummary;
import com.kizuna.notificationdelivery.application.NotificationService;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.shared.web.Replays;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification-deliveries")
@RequiredArgsConstructor
public class NotificationDeliveryController {
  private final NotificationService notifications;

  @PostMapping
  @PreAuthorize(
      "hasAuthority('PERM_NOTIFICATION_VIEW') and hasAuthority('PERM_NOTIFICATION_MANAGE')")
  public ResponseEntity<DeliveryResponse> create(
      @CurrentStore Long storeId,
      Authentication auth,
      @Valid @RequestBody DeliveryRequest request) {
    var content = request.content();
    var created =
        Replays.write(
            DbConstraint.UQ_T_NOTIFICATION_DELIVERIES_KEY,
            () -> notifications.create(storeId, auth.getName(), content),
            () -> notifications.replay(storeId, auth.getName(), content));
    return ResponseEntity.status(created.created() ? 201 : 200)
        .body(DeliveryResponse.of(created.delivery()));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_NOTIFICATION_VIEW')")
  public Page<DeliverySummary> list(
      @CurrentStore Long storeId,
      Authentication auth,
      @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    return notifications.list(storeId, auth.getName(), pageable).map(DeliverySummary::of);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_NOTIFICATION_VIEW')")
  public DeliveryResponse get(
      @CurrentStore Long storeId, Authentication auth, @PathVariable String id) {
    return DeliveryResponse.of(notifications.get(storeId, auth.getName(), id));
  }

  @PostMapping("/{id}/queue")
  @PreAuthorize("hasAuthority('PERM_NOTIFICATION_VIEW') and hasAuthority('PERM_NOTIFICATION_SEND')")
  public DeliveryResponse queue(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @Valid @RequestBody DeliveryActionRequest request) {
    return DeliveryResponse.of(
        notifications.queue(
            storeId, auth.getName(), id, request.version(), request.reason(), false));
  }

  @PostMapping("/{id}/retries")
  @PreAuthorize("hasAuthority('PERM_NOTIFICATION_VIEW') and hasAuthority('PERM_NOTIFICATION_SEND')")
  public ResponseEntity<DeliveryResponse> retry(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @Valid @RequestBody DeliveryActionRequest request) {
    return ResponseEntity.status(201)
        .body(
            DeliveryResponse.of(
                notifications.queue(
                    storeId, auth.getName(), id, request.version(), request.reason(), true)));
  }

  @GetMapping("/{id}/attempts")
  @PreAuthorize("hasAuthority('PERM_NOTIFICATION_VIEW')")
  public Page<DeliveryAttemptResponse> history(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    return notifications
        .history(storeId, auth.getName(), id, pageable)
        .map(DeliveryAttemptResponse::of);
  }
}
