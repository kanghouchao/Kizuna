package com.kizuna.user.application;

import java.util.Set;

/** サービスIDの作成・授権変更の命令。資格情報は持たない（サービスIDは対話ログインできない）。 */
public sealed interface ServiceIdentityCommand {

  record Create(String displayName, Set<Long> roleIds, Set<Long> storeIds)
      implements ServiceIdentityCommand {}

  /** version は応答で往復させた楽観ロックの版。 */
  record UpdateGrants(Set<Long> roleIds, Set<Long> storeIds, Long version)
      implements ServiceIdentityCommand {}
}
