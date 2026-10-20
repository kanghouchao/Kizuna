package com.kizuna.auth.infrastructure;

import com.kizuna.user.domain.User;
import java.util.Collection;
import java.util.Collections;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * DaoAuthenticationProvider が扱う認証主体。JWT claims（authorities / storeBridge 等）は認証成功後に呼び出し側が {@link
 * User} から算出するため、ここでの authorities は空のまま返す。
 */
@Getter
public class UserPrincipal implements UserDetails {

  private final User user;

  public UserPrincipal(User user) {
    this.user = user;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.emptyList();
  }

  @Override
  public String getPassword() {
    return user.getPassword();
  }

  @Override
  public String getUsername() {
    return user.getEmail();
  }

  @Override
  public boolean isEnabled() {
    return user.getEnabled();
  }
}
