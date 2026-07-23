package com.kizuna.auth.infrastructure;

import com.kizuna.user.domain.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** DaoAuthenticationProvider から呼び出される認証主体の取得元。email は呼び出し側で正規化済みの値を受け取る。 */
@Service
@RequiredArgsConstructor
public class PlatformUserDetailsService implements UserDetailsService {

  private final PlatformUserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return userRepository
        .findByEmail(email)
        .map(PlatformUserDetails::new)
        .orElseThrow(() -> new UsernameNotFoundException(email));
  }
}
