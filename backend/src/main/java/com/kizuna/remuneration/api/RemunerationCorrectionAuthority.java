package com.kizuna.remuneration.api;

import com.kizuna.user.domain.PermissionCode;
import org.springframework.security.core.Authentication;

/** 実行者が報酬の訂正権限を持つか。保証条件の登録は過去日のときだけ訂正権限を要し、過去日かは営業日を読むまで決まらないので、ここで判じて渡す。 */
final class RemunerationCorrectionAuthority {

  private RemunerationCorrectionAuthority() {}

  static boolean held(Authentication authentication) {
    String required = PermissionCode.REMUNERATION_CORRECT.authority();
    return authentication.getAuthorities().stream()
        .anyMatch(granted -> required.equals(granted.getAuthority()));
  }
}
