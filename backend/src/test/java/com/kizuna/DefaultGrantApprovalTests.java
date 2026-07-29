package com.kizuna;

import static com.kizuna.user.domain.PermissionCode.CAST_FIELD_DEF_MANAGE;
import static com.kizuna.user.domain.PermissionCode.CAST_FIELD_DEF_VIEW;
import static com.kizuna.user.domain.PermissionCode.CAST_INVITE;
import static com.kizuna.user.domain.PermissionCode.CAST_MANAGE;
import static com.kizuna.user.domain.PermissionCode.CUSTOMER_MANAGE;
import static com.kizuna.user.domain.PermissionCode.ORDER_MANAGE;
import static com.kizuna.user.domain.PermissionCode.ORDER_SET_MANAGE;
import static com.kizuna.user.domain.PermissionCode.PLATFORM_ASSET_MANAGE;
import static com.kizuna.user.domain.PermissionCode.PLATFORM_MENU_VIEW;
import static com.kizuna.user.domain.PermissionCode.SHIFT_MANAGE;
import static com.kizuna.user.domain.PermissionCode.STAFF_MANAGE;
import static com.kizuna.user.domain.PermissionCode.STORE_MANAGE;
import static com.kizuna.user.domain.PermissionCode.STORE_MENU_VIEW;
import static com.kizuna.user.domain.PermissionCode.STORE_PROFILE_MANAGE;
import static com.kizuna.user.domain.PermissionCode.STORE_VIEW;
import static com.kizuna.user.domain.PermissionCode.SYSTEM_CONFIG_MANAGE;
import static com.kizuna.user.domain.SystemRole.HQ_ADMIN;
import static com.kizuna.user.domain.SystemRole.STORE_MANAGER;
import static com.kizuna.user.domain.SystemRole.STORE_STAFF;
import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.SystemRole;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 平台既定ロールへ与えると承認済みの授権（既定授権の承認台帳）を、コード側宣言（{@link PermissionCode#getDefaultRoles()}）が満たしていることを機械検証する。
 *
 * <p>既定ロールの授与は宣言の写像として毎回取り直されるため、宣言から権限を取り下げると、それだけで稼働中の DB からも授権が消える。承認済みの授権を落とすのは製品判断なので、
 * 「うっかり」では通らないよう本台帳との照合を 1 段挟む。取り下げが意図したものであれば、宣言と同じ PR で下の台帳を書き換える。
 *
 * <p>台帳をここに置くのは、これが Liquibase の適用済み changeset ではなく本テストの資産だからである。播種の YAML を書き換えて済ませようとすると checksum
 * 検証に落ちて既存 DB が起動しなくなるため、承認の記録は移行履歴から独立させる。
 *
 * <p>逆に宣言を増やす変更（新しい権限、既存権限の授与先追加）は播種がそのまま追随できるため、本テストは通す。
 */
class DefaultGrantApprovalTests {

  /** 既定授権の承認台帳。権限ごとに、その権限を持たせると承認済みの平台既定ロールを並べる。 */
  private static final Map<PermissionCode, Set<SystemRole>> APPROVED_DEFAULT_GRANTS =
      Map.ofEntries(
          Map.entry(STORE_MANAGE, Set.of(HQ_ADMIN)),
          Map.entry(STAFF_MANAGE, Set.of(HQ_ADMIN)),
          Map.entry(SYSTEM_CONFIG_MANAGE, Set.of(HQ_ADMIN)),
          Map.entry(PLATFORM_MENU_VIEW, Set.of(HQ_ADMIN)),
          Map.entry(PLATFORM_ASSET_MANAGE, Set.of(HQ_ADMIN)),
          Map.entry(STORE_VIEW, Set.of(HQ_ADMIN, STORE_MANAGER, STORE_STAFF)),
          Map.entry(ORDER_SET_MANAGE, Set.of(HQ_ADMIN, STORE_MANAGER, STORE_STAFF)),
          Map.entry(ORDER_MANAGE, Set.of(STORE_MANAGER, STORE_STAFF)),
          Map.entry(CUSTOMER_MANAGE, Set.of(STORE_MANAGER, STORE_STAFF)),
          Map.entry(SHIFT_MANAGE, Set.of(STORE_MANAGER, STORE_STAFF)),
          Map.entry(CAST_MANAGE, Set.of(STORE_MANAGER, STORE_STAFF)),
          Map.entry(CAST_INVITE, Set.of(STORE_MANAGER)),
          Map.entry(CAST_FIELD_DEF_VIEW, Set.of(STORE_MANAGER, STORE_STAFF)),
          Map.entry(CAST_FIELD_DEF_MANAGE, Set.of(STORE_MANAGER)),
          Map.entry(STORE_PROFILE_MANAGE, Set.of(STORE_MANAGER, STORE_STAFF)),
          Map.entry(STORE_MENU_VIEW, Set.of(STORE_MANAGER, STORE_STAFF)));

  @Test
  @DisplayName("承認済みの既定授権がコード側宣言に含まれること")
  void approvedDefaultGrantsAreDeclared() {
    APPROVED_DEFAULT_GRANTS.forEach(
        (code, approved) ->
            assertThat(code.getDefaultRoles()).as("%s の既定ロール", code).containsAll(approved));
  }
}
