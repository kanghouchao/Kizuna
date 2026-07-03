package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.domain.CentralUser;
import com.kizuna.user.domain.CentralUserRepository;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CentralAuthService {

  private final AuthenticationManager authenticationManager;
  private final JwtUtil jwtUtil;
  private final CentralUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public Token login(String username, String password) {
    Authentication auth =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(username, password));

    return jwtUtil.generateToken(
        Objects.requireNonNull(auth.getName()),
        JwtUtil.ISSUER_CENTRAL,
        Map.of("authorities", auth.getAuthorities().stream().map(a -> a.getAuthority()).toList()));
  }

  @Transactional
  public void changePassword(String username, String currentPassword, String newPassword) {
    CentralUser user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new ServiceException("ユーザーが見つかりません"));
    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new ServiceException("現在のパスワードが正しくありません");
    }
    user.changePassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }
}
