package com.kizuna.user.application;

import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserCredentialsChanged;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NamedInterface(name = "credential-operations", propagate = false)
@Transactional(propagation = Propagation.MANDATORY)
public class CredentialOperations {
  private final ApplicationEventPublisher eventPublisher;
  private final RoleManageHolderGuard roleManageHolderGuard;
  private final UserRepository userRepository;
  private final EntityManager entityManager;

  public void stop(User user) {
    user.stop();
    notifyCredentialsChanged(user);
  }

  public void changePassword(User user, String encodedPassword) {
    user.changePassword(encodedPassword);
    notifyCredentialsChanged(user);
  }

  /** 授権の再割当。ロール集合が変わると集約が版を進める。停止と同じく、再送で反映を書き直せるよう要求ごとに現在版を通知する。 */
  public void reassignGrants(User user, Set<Long> roleIds, Set<Long> storeIds) {
    user.reassignGrants(roleIds, storeIds);
    notifyCredentialsChanged(user);
  }

  public void invalidateSessions(User user) {
    user.invalidateSessions();
    notifyCredentialsChanged(user);
  }

  /**
   * ロールの権限集合を置き換え、保持者全員の版を進める（操作者本人も含む）— トークンの権限は発行時にロールから写したものだから。
   * ロール行は呼出元が先に書き込みロックで押さえていること（押さえていなければ例外）。保持者の確定と保存をロール行の保存より先に行い、集合が変わらない編集は保持者に触れない。
   */
  public void replaceRolePermissions(Role role, Set<Long> permissionIds) {
    var lock = entityManager.getLockMode(role);
    if (lock != LockModeType.PESSIMISTIC_WRITE && lock != LockModeType.PESSIMISTIC_FORCE_INCREMENT)
      throw new IllegalStateException("ロール行を書き込みロックで押さえてから権限集合を置き換えてください");
    if (!role.getPermissionIds().equals(permissionIds)) {
      List<User> holders = roleManageHolderGuard.lockRoleHolders(role.getId());
      holders.forEach(this::invalidateSessions);
      userRepository.saveAllAndFlush(holders);
    }
    role.replacePermissions(permissionIds);
  }

  /** 会員退会。停止で旧トークンを即時失効させ、退会済みにして識別子を解放する（ADR 0029）。 */
  public void withdraw(User user, OffsetDateTime at) {
    user.stop();
    user.markWithdrawn(at);
    notifyCredentialsChanged(user);
  }

  private void notifyCredentialsChanged(User user) {
    // SERVICE は資格情報も対話セッションも持たないため、版だけを進める。
    if (user.getUserType() != UserType.SERVICE) {
      eventPublisher.publishEvent(
          new UserCredentialsChanged(user.getId(), user.getCredentialVersion()));
    }
  }
}
