package com.kizuna.user.application;

import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserCredentialsChanged;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
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

  public void stop(User user) {
    user.stop();
    notifyCredentialsChanged(user);
  }

  public void changePassword(User user, String encodedPassword) {
    user.changePassword(encodedPassword);
    notifyCredentialsChanged(user);
  }

  /** 授権の再割当。ロール集合が変わると集約が版を進める。停止と同じく、再送で反映を書き直せるよう要求ごとに現在版を通知する。 */
  public void reassignGrants(
      User user, Set<Long> roleIds, StoreScopeType storeScopeType, Set<Long> storeIds) {
    user.reassignGrants(roleIds, storeScopeType, storeIds);
    notifyCredentialsChanged(user);
  }

  public void invalidateSessions(User user) {
    user.invalidateSessions();
    notifyCredentialsChanged(user);
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
