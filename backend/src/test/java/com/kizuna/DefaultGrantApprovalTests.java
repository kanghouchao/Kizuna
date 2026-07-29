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
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 平台既定ロールへ与える授権の承認台帳と、コード側宣言（{@link PermissionCode#getDefaultRoles()}）が一致していることを機械検証する。
 *
 * <p>既定ロールの授与は宣言の写像として毎回取り直されるため、宣言をいじるだけで稼働中の DB の授権が増減する。増減はいずれも製品判断なので、「うっかり」では通らないよう 本台帳との照合を 1
 * 段挟む。授与先を増やす・新しい権限に既定授与を付ける・逆に取り下げる、いずれも宣言と同じ PR で下の台帳を書き換える。
 *
 * <p>包含ではなく完全一致で照合する。台帳に載っていない授与を通してしまうと、後から誤ってそれを落としても台帳に無いので検出できず、守れる範囲が「台帳を書いた時点の授権」に 縮んでしまう。
 *
 * <p>台帳をここに置くのは、これが Liquibase の適用済み changeset ではなく本テストの資産だからである。播種の YAML を書き換えて済ませようとすると checksum
 * 検証に落ちて既存 DB が起動しなくなるため、承認の記録は移行履歴から独立させる。
 */
class DefaultGrantApprovalTests {

  /** 既定授権の承認台帳。権限ごとに、その権限を持たせると承認済みの平台既定ロールを並べる（既定授与が無い権限は載せない）。 */
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
  @DisplayName("コード側宣言の既定授与が承認台帳と完全に一致すること")
  void declaredDefaultGrantsMatchApprovalLedger() {
    Map<PermissionCode, Set<SystemRole>> declared =
        Arrays.stream(PermissionCode.values())
            .filter(code -> !code.getDefaultRoles().isEmpty())
            .collect(Collectors.toMap(code -> code, PermissionCode::getDefaultRoles));

    assertThat(declared).as("権限ごとの既定ロール").isEqualTo(APPROVED_DEFAULT_GRANTS);
  }

  @Test
  @DisplayName("全ての平台既定ロールが少なくとも 1 つの権限から宣言されていること")
  void everySystemRoleHasAtLeastOnePermission() {
    // 播種は宣言どおりロール行を作るため、権限を 1 つも宣言されていない既定ロールは「authority が
    // 空のロール」として利用者に見え、それだけを付与された staff は権限ゼロのトークンになる。
    // ロールの追加と授与の宣言は別の場所（SystemRole と各 PermissionCode）にあり書き忘れが起きうるので、
    // DB へ届く前の単体段階で塞ぐ — 宣言だけで判定できる性質を起動時まで持ち越す理由は無い。
    for (SystemRole systemRole : SystemRole.values()) {
      assertThat(PermissionCode.values())
          .as("%s を既定ロールに含む権限", systemRole.getRoleName())
          .anyMatch(code -> code.getDefaultRoles().contains(systemRole));
    }
  }
}
