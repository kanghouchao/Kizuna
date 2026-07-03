package com.kizuna.user.application;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantScoped;
import com.kizuna.user.api.dto.StoreUserMeResponse;
import com.kizuna.user.domain.StoreUser;
import com.kizuna.user.domain.StoreUserRepository;
import com.kizuna.user.domain.TenantRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreUserService {

  private final StoreUserRepository userRepository;

  @TenantScoped
  @Transactional(readOnly = true)
  public StoreUserMeResponse me(String email) {
    return toResponse(findByEmail(email));
  }

  @TenantScoped
  @Transactional
  public StoreUserMeResponse updateProfile(String email, String nickname) {
    StoreUser user = findByEmail(email);
    user.rename(nickname);
    return toResponse(userRepository.save(user));
  }

  private StoreUser findByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new ServiceException("ユーザーが見つかりません"));
  }

  private static StoreUserMeResponse toResponse(StoreUser user) {
    String role = user.getRoles().stream().findFirst().map(TenantRole::getName).orElse("");
    return new StoreUserMeResponse(user.getId(), user.getNickname(), user.getEmail(), role);
  }
}
