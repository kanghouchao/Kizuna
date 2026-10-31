package com.kizuna.auth.infrastructure;

import com.kizuna.user.domain.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * DaoAuthenticationProvider から呼び出される認証主体の取得元。User は email を小文字へ正規化して保存するため、
 * 照合の入口がどこであっても引く前にここで同じ正規化をする。
 */
@Service
@RequiredArgsConstructor
public class UserPrincipalService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return userRepository
        .findByEmail(email.toLowerCase(Locale.ROOT))
        .map(UserPrincipal::new)
        .orElseThrow(() -> new UsernameNotFoundException(email));
  }
}
