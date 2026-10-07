package com.kizuna.notificationdelivery.api.store;

import com.kizuna.notificationdelivery.api.dto.DeliveryActionRequest;
import com.kizuna.notificationdelivery.api.dto.DeliveryAttemptResponse;
import com.kizuna.notificationdelivery.api.dto.DeliveryRequest;
import com.kizuna.notificationdelivery.api.dto.DeliveryResponse;
import com.kizuna.notificationdelivery.api.dto.DeliverySummary;
import com.kizuna.notificationdelivery.application.NotificationService;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/notification-deliveries")
@RequiredArgsConstructor
public class NotificationDeliveryController {
  private final NotificationService notifications;

  @PostMapping
  @PreAuthorize(
      "hasAuthority('PERM_NOTIFICATION_VIEW') and hasAuthority('PERM_NOTIFICATION_MANAGE')")
  public ResponseEntity<DeliveryResponse> create(
      Authentication auth, @Valid @RequestBody DeliveryRequest request) {
    var content = request.content();
    NotificationService.Created created;
    try {
      created = notifications.create(auth.getName(), content);
    } catch (DataIntegrityViolationException conflict) {
      if (!IntegrityViolations.violates(conflict, DbConstraint.UQ_T_NOTIFICATION_DELIVERIES_KEY))
        throw conflict;
      created = notifications.replay(auth.getName(), content);
    }
    return ResponseEntity.status(created.created() ? 201 : 200).body(created.delivery());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_NOTIFICATION_VIEW')")
  public CursorPage<DeliverySummary> list(
      Authentication auth,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size) {
    return notifications.list(auth.getName(), cursor, size);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_NOTIFICATION_VIEW')")
  public DeliveryResponse get(Authentication auth, @PathVariable String id) {
    return notifications.get(auth.getName(), id);
  }

  @PostMapping("/{id}/queue")
  @PreAuthorize("hasAuthority('PERM_NOTIFICATION_VIEW') and hasAuthority('PERM_NOTIFICATION_SEND')")
  public DeliveryResponse queue(
      Authentication auth,
      @PathVariable String id,
      @Valid @RequestBody DeliveryActionRequest request) {
    return notifications.queue(auth.getName(), id, request.version(), request.reason(), false);
  }

  @PostMapping("/{id}/retries")
  @PreAuthorize("hasAuthority('PERM_NOTIFICATION_VIEW') and hasAuthority('PERM_NOTIFICATION_SEND')")
  public ResponseEntity<DeliveryResponse> retry(
      Authentication auth,
      @PathVariable String id,
      @Valid @RequestBody DeliveryActionRequest request) {
    return ResponseEntity.status(201)
        .body(notifications.queue(auth.getName(), id, request.version(), request.reason(), true));
  }

  @GetMapping("/{id}/attempts")
  @PreAuthorize("hasAuthority('PERM_NOTIFICATION_VIEW')")
  public CursorPage<DeliveryAttemptResponse> history(
      Authentication auth,
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size) {
    return notifications.history(auth.getName(), id, cursor, size);
  }
}
