package com.kizuna.shared.security;

/**
 * SecurityContext に載せる authority 文字列の符号化規約。 消費側（{@code PermissionCode}・{@code
 * MenuTreeAssembler}・{@code StoreIdInterceptor} 等）はここを経由し、接頭辞を文字列リテラルで再現しないこと。
 */
public final class Authorities {

  private static final String PERMISSION_PREFIX = "PERM_";

  private Authorities() {}

  /** 権限名（例: STORE_MANAGE）を SecurityContext 上の authority 表現（PERM_STORE_MANAGE）へ。 */
  public static String permission(String permissionName) {
    return PERMISSION_PREFIX + permissionName;
  }

  /** authority が権限（PERM_）か。本人種別標識 ROLE_CAST / ROLE_MEMBER は該当しない。 */
  public static boolean isPermission(String authority) {
    return authority != null && authority.startsWith(PERMISSION_PREFIX);
  }
}
