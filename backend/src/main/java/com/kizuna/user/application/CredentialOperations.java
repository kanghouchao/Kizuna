package com.kizuna.user.application;

import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserCredentialsChanged;
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

  public void stop(User user) {
    user.stop();
    notifyCredentialsChanged(user);
  }

  public void changePassword(User user, String encodedPassword) {
    user.changePassword(encodedPassword);
    notifyCredentialsChanged(user);
  }

  public void invalidateSessions(User user) {
    user.invalidateSessions();
    notifyCredentialsChanged(user);
  }

  private void notifyCredentialsChanged(User user) {
    // SERVICE は資格情報も対話セッションも持たないため、版だけを進める。
    if (user.getUserType() != UserType.SERVICE) {
      eventPublisher.publishEvent(
          new UserCredentialsChanged(user.getEmail(), user.getCredentialVersion()));
    }
  }
}
