package com.kizuna.user.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NamedInterface("execution-identity")
@Transactional(readOnly = true)
public class ServiceExecutionIdentityService {
  private final UserRepository users;
  private final RoleRepository roles;

  // 呼出側の分離レベルや一次キャッシュに依存せず、確定済みの授権を読み直す。
  @Transactional(
      readOnly = true,
      propagation = Propagation.REQUIRES_NEW,
      isolation = Isolation.READ_COMMITTED)
  public AuditActor requireService(Long id, PermissionCode permission) {
    return requireServiceScope(id, permission).actor();
  }

  /** 実行主体と、店舗級のデータを絞る店舗集合を同じ読みから返す。店舗を持たないグループ級のデータはこの集合で絞らない。 */
  @Transactional(
      readOnly = true,
      propagation = Propagation.REQUIRES_NEW,
      isolation = Isolation.READ_COMMITTED)
  public ServiceScope requireServiceScope(Long id, PermissionCode permission) {
    var user = users.findById(id).orElseThrow(ServiceExecutionIdentityService::denied);
    if (user.getUserType() != UserType.SERVICE
        || !Boolean.TRUE.equals(user.getEnabled())
        || !hasPermission(user, permission)) {
      throw denied();
    }
    return new ServiceScope(actor(user), StoreScopeResolver.scopeOf(user));
  }

  public record ServiceScope(AuditActor actor, StoreScope stores) {}

  /** 有効なサービス ID のいずれかの店舗集合に入っている店舗。停止中のサービス ID は処理を実行しないので数えない。 */
  public StoreScope coveredStores() {
    var services = users.findByUserTypeAndEnabledTrue(UserType.SERVICE);
    if (services.stream().anyMatch(u -> u.getStoreScopeType() == StoreScopeType.ALL_STORES)) {
      return new StoreScope(true, Set.of());
    }
    return new StoreScope(
        false,
        services.stream()
            .flatMap(u -> u.getStoreIds().stream())
            .collect(Collectors.toUnmodifiableSet()));
  }

  public AuditActor requireOperator(Long id, PermissionCode permission) {
    return requireOperator(
        users.findById(id).orElseThrow(ServiceExecutionIdentityService::denied), permission);
  }

  public AuditActor requireOperator(String email, PermissionCode permission) {
    var user = users.findByEmail(email).orElseThrow(ServiceExecutionIdentityService::denied);
    return requireOperator(user, permission);
  }

  private AuditActor requireOperator(User user, PermissionCode permission) {
    if (user.getUserType() != UserType.STAFF
        || !Boolean.TRUE.equals(user.getEnabled())
        || !hasPermission(user, permission)) {
      throw denied();
    }
    return actor(user);
  }

  public record Candidate(Long id, String displayName) {}

  public Page<Candidate> candidates(Pageable pageable, PermissionCode permission) {
    var roleIds = roles.findIdsByPermissionCode(PermissionCode.TASK_EXECUTE.name());
    var taskRoleIds = roles.findIdsByPermissionCode(permission.name());
    if (roleIds.isEmpty() || taskRoleIds.isEmpty()) return Page.empty(pageable);
    return users
        .findAll(
            (root, query, cb) -> {
              query.distinct(true);
              return cb.and(
                  cb.equal(root.get("userType"), UserType.SERVICE),
                  cb.isTrue(root.get("enabled")),
                  root.join("roleIds").in(roleIds),
                  root.join("roleIds").in(taskRoleIds));
            },
            pageable)
        .map(user -> new Candidate(user.getId(), user.getDisplayName()));
  }

  private boolean hasPermission(User user, PermissionCode permission) {
    return !Collections.disjoint(
        user.getRoleIds(), roles.findIdsByPermissionCode(permission.name()));
  }

  private static AuditActor actor(User user) {
    return new AuditActor(user.getId(), user.getUserType().name(), user.getDisplayName());
  }

  private static AccessDeniedException denied() {
    return new AccessDeniedException("実行主体または対象範囲の権限がありません");
  }
}
