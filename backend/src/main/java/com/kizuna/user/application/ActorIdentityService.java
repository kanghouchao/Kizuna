package com.kizuna.user.application;

import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 認証主体を必須のユーザー ID に解決し、実行者不明の操作が記録されることを防ぐ。 */
@Service
@RequiredArgsConstructor
@NamedInterface("actor-identity")
public class ActorIdentityService {

  private final PlatformUserRepository platformUserRepository;

  @Transactional(readOnly = true)
  public Long requireUserId(String email) {
    return platformUserRepository
        .findByEmail(email)
        .map(PlatformUser::getId)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
  }
}
