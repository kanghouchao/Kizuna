package com.kizuna.user.domain;

import lombok.Getter;

/**
 * 平台既定ロールの目録。{@code t_roles.is_system = true} の行に対応し、名称（自然キー）はここが正本である。
 *
 * <p>既定ロールは API から改廃できない（{@link Role#rename}／{@link Role#replacePermissions} が拒否する）ため、含む権限もコード側の宣言
 * — 各 {@link PermissionCode} の {@code defaultRoles} — が正本になる。播種はその写像を投入するだけで、逆向き（DB → コード）の流れは無い。
 *
 * <p>利用者が自作するロールはこの enum に現れない。増減は DB データとして自由である。
 */
@Getter
public enum SystemRole {

  /** HQ（プラットフォーム）管理者。プラットフォーム側の全権限と跨店参照系を持つ。 */
  HQ_ADMIN("HQ管理者"),

  /** 店長。店舗側の全権限と跨店参照系を持つ。 */
  STORE_MANAGER("店長"),

  /** 店舗スタッフ。店長から店長専用の操作（招待発行・カスタム項目定義の改廃）を除いたもの。 */
  STORE_STAFF("店舗スタッフ");

  /** {@code t_roles.name} に現れる表示名。播種行との対応はこの名称で解決する。 */
  private final String roleName;

  SystemRole(String roleName) {
    this.roleName = roleName;
  }
}
