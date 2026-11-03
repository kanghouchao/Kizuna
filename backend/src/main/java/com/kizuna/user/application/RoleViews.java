package com.kizuna.user.application;

import java.util.Comparator;
import java.util.List;

public final class RoleViews {
  private RoleViews() {}

  /** ロール一覧・付与可能ロールの 1 件。権限は個数だけを持つ。 */
  public record Summary(Long id, String name, boolean system, long permissionCount) {}

  /** ロールの詳細。permissions は権限コードの昇順で、権限行の id は持たない。 */
  public record Detail(
      Long id, String name, boolean system, List<String> permissions, long version) {}

  /** 従業員・サービスIDに付与されたロールへの参照。名称は並行削除で解決できなければ null。 */
  public record Ref(Long id, String name) {

    /** 名称順、未解決（null）は末尾。 */
    static final Comparator<Ref> ORDER =
        Comparator.comparing(Ref::name, Comparator.nullsLast(Comparator.naturalOrder()));
  }
}
