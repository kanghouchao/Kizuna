package com.kizuna.order.api;

import com.kizuna.user.domain.PermissionCode;
import org.springframework.security.core.Authentication;

/** 実行者が顧客管理権限を持つか。受注の端点は受注管理権限だけで通るので、顧客の新規登録と連絡先検索の可否はここで判じて渡す。 */
final class CustomerManagementAuthority {

  private CustomerManagementAuthority() {}

  static boolean held(Authentication authentication) {
    String required = PermissionCode.CUSTOMER_MANAGE.authority();
    return authentication.getAuthorities().stream()
        .anyMatch(granted -> required.equals(granted.getAuthority()));
  }
}
