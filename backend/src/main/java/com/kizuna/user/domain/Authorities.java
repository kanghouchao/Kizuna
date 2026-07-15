package com.kizuna.user.domain;

/**
 * SecurityContext に載せる authority 文字列の符号化規約。 消費側（{@link
 * com.kizuna.menu.application.MenuTreeAssembler} 等）はここを経由し、接頭辞を文字列リテラルで再現しないこと。
 */
public final class Authorities {

  private static final String PERMISSION_PREFIX = "PERM_";

  private Authorities() {}

  /** 権限名（例: TENANT_MANAGE）を SecurityContext 上の authority 表現（PERM_TENANT_MANAGE）へ。 */
  public static String permission(String permissionName) {
    return PERMISSION_PREFIX + permissionName;
  }
}
