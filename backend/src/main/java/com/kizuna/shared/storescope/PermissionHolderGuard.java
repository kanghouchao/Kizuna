package com.kizuna.shared.storescope;

import com.kizuna.shared.security.Authorities;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 認証主体が権限（{@code PERM_}）を 1 つ以上持つかを @PreAuthorize から判定するガード（{@code
 * @permissionHolder.check(authentication)}）。CAST / MEMBER は本人種別標識しか持たないため false になる。
 * 個別の権限を名指ししない端点（授権店舗一覧など、ロールで授権される全員に開く読み口）が使う。
 */
@Component("permissionHolder")
public class PermissionHolderGuard {

  public boolean check(Authentication authentication) {
    return holdsPermission(authentication);
  }

  static boolean holdsPermission(Authentication authentication) {
    return authentication != null
        && authentication.isAuthenticated()
        && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(Authorities::isPermission);
  }
}
