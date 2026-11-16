package com.kizuna.user.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 入口が渡すトークンの利用者 ID を記録に残す実行者へ解決し、実行者不明の操作が記録されることを防ぐ。 */
@Service
@RequiredArgsConstructor
@NamedInterface("actor-identity")
public class ActorIdentityService {

  private final UserRepository userRepository;

  /** 授権は入口が判定済みなので、ここでは本人の行から ID・種別・表示名を読むだけにする。 */
  @Transactional(readOnly = true)
  public AuditActor requireActor(long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
    return new AuditActor(user.getId(), user.getUserType().name(), user.getDisplayName());
  }
}
