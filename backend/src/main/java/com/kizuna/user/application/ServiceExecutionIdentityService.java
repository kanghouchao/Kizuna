package com.kizuna.user.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NamedInterface("execution-identity")
@Transactional(readOnly = true)
public class ServiceExecutionIdentityService {
  private final PlatformUserRepository users;
  private final RoleRepository roles;

  public AuditActor requireService(Long id, PermissionCode permission, Long storeId) {
    var user = users.findById(id).orElseThrow(ServiceExecutionIdentityService::denied);
    if (user.getUserType() != UserType.SERVICE
        || !Boolean.TRUE.equals(user.getEnabled())
        || !(storeId == null
            ? user.getStoreScopeType() == StoreScopeType.ALL_STORES
            : user.authorizes(storeId))
        || !hasPermission(user, permission)) {
      throw denied();
    }
    return actor(user);
  }

  public AuditActor requireOperator(String email, PermissionCode permission) {
    var user = users.findByEmail(email).orElseThrow(ServiceExecutionIdentityService::denied);
    if (user.getUserType() != UserType.STAFF
        || !Boolean.TRUE.equals(user.getEnabled())
        || user.getStoreScopeType() != StoreScopeType.ALL_STORES
        || !hasPermission(user, permission)) {
      throw denied();
    }
    return actor(user);
  }

  public record Candidate(Long id, String displayName) {}

  public Page<Candidate> candidates(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new ServiceException("ページ指定が不正です");
    var pageable = PageRequest.of(page, size, Sort.by("displayName", "id"));
    var roleIds = roles.findIdsByPermissionCode(PermissionCode.TASK_EXECUTE.name());
    if (roleIds.isEmpty()) return Page.empty(pageable);
    return users
        .findAll(
            (root, query, cb) -> {
              query.distinct(true);
              return cb.and(
                  cb.equal(root.get("userType"), UserType.SERVICE),
                  cb.isTrue(root.get("enabled")),
                  cb.equal(root.get("storeScopeType"), StoreScopeType.ALL_STORES),
                  root.join("roleIds").in(roleIds));
            },
            pageable)
        .map(user -> new Candidate(user.getId(), user.getDisplayName()));
  }

  private boolean hasPermission(PlatformUser user, PermissionCode permission) {
    return !Collections.disjoint(
        user.getRoleIds(), roles.findIdsByPermissionCode(permission.name()));
  }

  private static AuditActor actor(PlatformUser user) {
    return new AuditActor(user.getId(), user.getUserType().name(), user.getDisplayName());
  }

  private static AccessDeniedException denied() {
    return new AccessDeniedException("実行主体または対象範囲の権限がありません");
  }
}
