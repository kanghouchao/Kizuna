package com.kizuna.user.application;

import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserCredentialsChanged;
import com.kizuna.user.domain.UserType;
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

  public void stop(PlatformUser user) {
    user.stop();
    notifyCredentialsChanged(user);
  }

  public void changePassword(PlatformUser user, String encodedPassword) {
    user.changePassword(encodedPassword);
    notifyCredentialsChanged(user);
  }

  public void invalidateSessions(PlatformUser user) {
    user.invalidateSessions();
    notifyCredentialsChanged(user);
  }

  private void notifyCredentialsChanged(PlatformUser user) {
    // SERVICE は資格情報も対話セッションも持たないため、版だけを進める。
    if (user.getUserType() != UserType.SERVICE) {
      eventPublisher.publishEvent(
          new PlatformUserCredentialsChanged(user.getEmail(), user.getCredentialVersion()));
    }
  }
}
