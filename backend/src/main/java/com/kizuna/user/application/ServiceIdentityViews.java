package com.kizuna.user.application;

import java.util.List;
import java.util.Set;

public final class ServiceIdentityViews {
  private ServiceIdentityViews() {}

  /** 一覧の 1 件。楽観ロックの版は詳細だけが持つ。 */
  public record Summary(
      Long id,
      String displayName,
      boolean enabled,
      List<RoleViews.Ref> roles,
      Set<Long> storeIds) {}

  public record Detail(
      Long id,
      String displayName,
      boolean enabled,
      List<RoleViews.Ref> roles,
      Set<Long> storeIds,
      long version) {}
}
