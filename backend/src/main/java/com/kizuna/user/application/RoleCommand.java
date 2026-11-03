package com.kizuna.user.application;

import java.util.Set;

/** ロールの作成・編集の命令。permissions は権限コード（PermissionCode enum 名）。 */
public sealed interface RoleCommand {

  record Create(String name, Set<String> permissions) implements RoleCommand {}

  /** version は応答で往復させた楽観ロックの版。 */
  record Update(String name, Set<String> permissions, Long version) implements RoleCommand {}
}
