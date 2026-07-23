package com.kizuna.auth.infrastructure;

import com.kizuna.user.domain.PlatformUser;
import java.util.Collection;
import java.util.Collections;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * DaoAuthenticationProvider が扱う認証主体。JWT claims（authorities / storeBridge 等）は認証成功後に呼び出し側が {@link
 * PlatformUser} から算出するため、ここでの authorities は空のまま返す。
 */
@Getter
public class PlatformUserDetails implements UserDetails {

  private final PlatformUser platformUser;

  public PlatformUserDetails(PlatformUser platformUser) {
    this.platformUser = platformUser;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.emptyList();
  }

  @Override
  public String getPassword() {
    return platformUser.getPassword();
  }

  @Override
  public String getUsername() {
    return platformUser.getEmail();
  }

  @Override
  public boolean isEnabled() {
    return platformUser.getEnabled();
  }
}
