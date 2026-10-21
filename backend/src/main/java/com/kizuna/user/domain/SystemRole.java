package com.kizuna.user.domain;

import lombok.Getter;

/**
 * 既定ロールの目録。{@code t_roles.is_system = true} の行に対応し、名称（自然キー）はここが正本である。
 *
 * <p>既定ロールは改名・削除できない（{@link Role#rename} が拒否する）が権限は編集できる。各 {@link PermissionCode} の {@code
 * defaultRoles} はロール行を初めて作る時の初期授与で、以後の正本は DB 側にある。
 *
 * <p>利用者が自作するロールはこの enum に現れない。増減は DB データとして自由である。
 */
@Getter
public enum SystemRole {

  /** グループ管理者。全群級の権限と跨店参照系・従業員管理を初期授与として持つ。店舗業務の権限は ROLE_MANAGE 保持者として配れる。 */
  HQ_ADMIN("グループ管理者"),

  /** 店長。店舗業務の全権限と跨店参照系・従業員管理を初期授与として持つ。 */
  STORE_MANAGER("店長"),

  /** 店舗スタッフ。店長から確定系・金額・機微情報・出力の操作を除いた日常業務の権限を持つ。 */
  STORE_STAFF("店舗スタッフ");

  /** {@code t_roles.name} に現れる表示名。播種行との対応はこの名称で解決する。 */
  private final String roleName;

  SystemRole(String roleName) {
    this.roleName = roleName;
  }
}
