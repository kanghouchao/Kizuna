package com.kizuna.user.domain;

import java.util.Set;

/** プラットフォームユーザーのロール。授権は「ロール×店舗集合」で表す（1 ユーザー = 1 ロール）。 エリアマネージャ等の追加値は後続チケットで拡張する（#320/#321）。 */
public enum PlatformRole {
  HQ_ADMIN,
  STORE_MANAGER,
  STORE_STAFF,
  CAST,
  MEMBER;

  /** 過橋期（#324〜#326）に旧 central/store 業務端点で有効な権限文字列。contract（#326）で旧権限モデルごと再編する。 */
  public Set<String> grantedPermissions() {
    return switch (this) {
      case HQ_ADMIN -> Set.of("TENANT_MANAGE", "SYSTEM_CONFIG");
      case STORE_MANAGER, STORE_STAFF ->
          Set.of("ORDER_MANAGE", "CAST_MANAGE", "CUSTOMER_MANAGE", "TENANT_CONFIG");
      case CAST, MEMBER -> Set.of();
    };
  }
}
