package com.kizuna.user.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditChange;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NamedInterface("business-audit")
@Transactional(propagation = Propagation.MANDATORY)
public class BusinessAudit {
  private final UserRepository users;
  private final AuditWriter writer;

  public void recordCurrent(
      Long storeId,
      String action,
      String targetType,
      String targetId,
      Map<String, String> before,
      Map<String, String> after) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) throw missingActor();
    record(auth.getName(), storeId, action, targetType, targetId, null, null, before, after);
  }

  public void record(
      String actorEmail,
      Long storeId,
      String action,
      String targetType,
      String targetId,
      String sourceType,
      String sourceId,
      Map<String, String> before,
      Map<String, String> after) {
    var actor = users.findByEmail(actorEmail).orElseThrow(BusinessAudit::missingActor);
    append(actor, storeId, action, targetType, targetId, sourceType, sourceId, before, after);
  }

  public void recordById(
      Long actorId,
      Long storeId,
      String action,
      String targetType,
      String targetId,
      String sourceType,
      String sourceId,
      Map<String, String> before,
      Map<String, String> after) {
    var actor = users.findById(actorId).orElseThrow(BusinessAudit::missingActor);
    append(actor, storeId, action, targetType, targetId, sourceType, sourceId, before, after);
  }

  private void append(
      User actor,
      Long storeId,
      String action,
      String targetType,
      String targetId,
      String sourceType,
      String sourceId,
      Map<String, String> before,
      Map<String, String> after) {
    writer.append(
        new AuditChange(
            new AuditActor(actor.getId(), actor.getUserType().name(), actor.getDisplayName()),
            storeId,
            action,
            targetType,
            targetId,
            sourceType,
            sourceId,
            before,
            after));
  }

  public static Map<String, String> grants(User user) {
    return Map.of(
        "user_type",
        user.getUserType().name(),
        "enabled",
        String.valueOf(user.getEnabled()),
        "role_ids",
        ids(user.getRoleIds()),
        "store_scope_type",
        user.getStoreScopeType().name(),
        "store_ids",
        ids(user.getStoreIds()));
  }

  public static String ids(Set<Long> ids) {
    return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
  }

  private static StaleSessionException missingActor() {
    return new StaleSessionException("監査の操作主体を確認できません");
  }
}
