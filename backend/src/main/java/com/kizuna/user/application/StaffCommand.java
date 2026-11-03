package com.kizuna.user.application;

import com.kizuna.user.domain.StoreScopeType;
import java.util.Set;

/** 従業員の作成・授権変更の命令。行使者は命令に載せず、認証主体から別に受ける。 */
public sealed interface StaffCommand {

  /** 生のパスワードを持つため、記録型が自動生成する toString からは伏せる（ログへの混入を防ぐ）。 */
  record Create(
      String email,
      String password,
      String displayName,
      Set<Long> roleIds,
      StoreScopeType storeScopeType,
      Set<Long> storeIds)
      implements StaffCommand {

    @Override
    public String toString() {
      return "Create[email="
          + email
          + ", password=***, displayName="
          + displayName
          + ", roleIds="
          + roleIds
          + ", storeScopeType="
          + storeScopeType
          + ", storeIds="
          + storeIds
          + "]";
    }
  }

  /** version は応答で往復させた楽観ロックの版。 */
  record UpdateGrants(
      Set<Long> roleIds, StoreScopeType storeScopeType, Set<Long> storeIds, Long version)
      implements StaffCommand {}
}
