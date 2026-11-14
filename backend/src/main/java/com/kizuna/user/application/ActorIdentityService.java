package com.kizuna.user.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 認証主体を必須のユーザー ID に解決し、実行者不明の操作が記録されることを防ぐ。 */
@Service
@RequiredArgsConstructor
@NamedInterface("actor-identity")
public class ActorIdentityService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public Long requireUserId(String email) {
    return require(email).getId();
  }

  /** 記録に残す実行者。授権は入口が判定済みなので、ここでは本人の行から ID・種別・表示名を読むだけにする。 */
  @Transactional(readOnly = true)
  public AuditActor requireActor(String email) {
    User user = require(email);
    return new AuditActor(user.getId(), user.getUserType().name(), user.getDisplayName());
  }

  @Transactional(readOnly = true)
  public AuditActor requireActor(long userId) {
    User user = userRepository.findById(userId).orElseThrow(ActorIdentityService::missing);
    return new AuditActor(user.getId(), user.getUserType().name(), user.getDisplayName());
  }

  private User require(String email) {
    return userRepository.findByEmail(email).orElseThrow(ActorIdentityService::missing);
  }

  private static StaleSessionException missing() {
    return new StaleSessionException("認証セッションの主体が存在しません");
  }
}
