package com.kizuna;

import static com.kizuna.user.domain.PermissionCode.ADVERTISING_COST_EXPORT;
import static com.kizuna.user.domain.PermissionCode.ADVERTISING_COST_MANAGE;
import static com.kizuna.user.domain.PermissionCode.ADVERTISING_COST_SET_EXPORT;
import static com.kizuna.user.domain.PermissionCode.ADVERTISING_COST_SET_VIEW;
import static com.kizuna.user.domain.PermissionCode.ADVERTISING_COST_VIEW;
import static com.kizuna.user.domain.PermissionCode.APPLICANT_ANONYMIZE;
import static com.kizuna.user.domain.PermissionCode.AUDIT_VIEW;
import static com.kizuna.user.domain.PermissionCode.BENEFIT_MANAGE;
import static com.kizuna.user.domain.PermissionCode.BONUS_AWARD;
import static com.kizuna.user.domain.PermissionCode.CAST_FIELD_DEF_MANAGE;
import static com.kizuna.user.domain.PermissionCode.CAST_FIELD_DEF_VIEW;
import static com.kizuna.user.domain.PermissionCode.CAST_INVITE;
import static com.kizuna.user.domain.PermissionCode.CAST_MANAGE;
import static com.kizuna.user.domain.PermissionCode.CAST_PERSON_VIEW;
import static com.kizuna.user.domain.PermissionCode.CUSTOMER_MANAGE;
import static com.kizuna.user.domain.PermissionCode.CUSTOMER_MERGE;
import static com.kizuna.user.domain.PermissionCode.GUARANTEE_MANAGE;
import static com.kizuna.user.domain.PermissionCode.MEMBER_VIEW;
import static com.kizuna.user.domain.PermissionCode.MEMBER_WITHDRAW;
import static com.kizuna.user.domain.PermissionCode.NOTIFICATION_DELIVER;
import static com.kizuna.user.domain.PermissionCode.NOTIFICATION_MANAGE;
import static com.kizuna.user.domain.PermissionCode.NOTIFICATION_SEND;
import static com.kizuna.user.domain.PermissionCode.NOTIFICATION_VIEW;
import static com.kizuna.user.domain.PermissionCode.OPERATIONAL_REPORT_EXPORT;
import static com.kizuna.user.domain.PermissionCode.OPERATIONAL_REPORT_VIEW;
import static com.kizuna.user.domain.PermissionCode.ORDER_CORRECT;
import static com.kizuna.user.domain.PermissionCode.ORDER_MANAGE;
import static com.kizuna.user.domain.PermissionCode.ORDER_SET_VIEW;
import static com.kizuna.user.domain.PermissionCode.POINT_ADJUST;
import static com.kizuna.user.domain.PermissionCode.POINT_EXPIRE;
import static com.kizuna.user.domain.PermissionCode.RECRUITMENT_ATTACHMENT_MANAGE;
import static com.kizuna.user.domain.PermissionCode.RECRUITMENT_ATTACHMENT_VIEW;
import static com.kizuna.user.domain.PermissionCode.RECRUITMENT_DECIDE;
import static com.kizuna.user.domain.PermissionCode.RECRUITMENT_MANAGE;
import static com.kizuna.user.domain.PermissionCode.RECRUITMENT_VIEW;
import static com.kizuna.user.domain.PermissionCode.REMUNERATION_CORRECT;
import static com.kizuna.user.domain.PermissionCode.REMUNERATION_VIEW;
import static com.kizuna.user.domain.PermissionCode.REVIEW_MANAGE;
import static com.kizuna.user.domain.PermissionCode.REVIEW_MODERATE;
import static com.kizuna.user.domain.PermissionCode.REVIEW_VIEW;
import static com.kizuna.user.domain.PermissionCode.ROLE_MANAGE;
import static com.kizuna.user.domain.PermissionCode.SERVICE_ID_MANAGE;
import static com.kizuna.user.domain.PermissionCode.SERVICE_MANAGE;
import static com.kizuna.user.domain.PermissionCode.SHIFT_MANAGE;
import static com.kizuna.user.domain.PermissionCode.STAFF_CREDENTIAL_MANAGE;
import static com.kizuna.user.domain.PermissionCode.STAFF_MANAGE;
import static com.kizuna.user.domain.PermissionCode.STORE_MANAGE;
import static com.kizuna.user.domain.PermissionCode.STORE_PROFILE_MANAGE;
import static com.kizuna.user.domain.PermissionCode.STORE_VIEW;
import static com.kizuna.user.domain.PermissionCode.SURVEY_MANAGE;
import static com.kizuna.user.domain.PermissionCode.SURVEY_RECORD;
import static com.kizuna.user.domain.PermissionCode.SURVEY_VIEW;
import static com.kizuna.user.domain.PermissionCode.SYSTEM_CONFIG_MANAGE;
import static com.kizuna.user.domain.PermissionCode.TASK_EXECUTE;
import static com.kizuna.user.domain.PermissionCode.TASK_MANAGE;
import static com.kizuna.user.domain.SystemRole.ADMIN;
import static com.kizuna.user.domain.SystemRole.MANAGER;
import static com.kizuna.user.domain.SystemRole.STAFF;
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
 * 既定ロールへ与える授権の承認台帳と、コード側宣言（{@link PermissionCode#getDefaultRoles()}）が一致していることを機械検証する。
 *
 * <p>既定ロールの初期授与は宣言から播種され、基線 seed もこれと一致する（{@code
 * PermissionSeedLiteralTests}）。増減はいずれも製品判断なので、「うっかり」では通らないよう 本台帳との照合を 1
 * 段挟む。授与先を増やす・新しい権限に既定授与を付ける・逆に取り下げる、いずれも宣言と同じ PR で下の台帳と基線を書き換える。
 *
 * <p>包含ではなく完全一致で照合する。台帳に載っていない授与を通してしまうと、後から誤ってそれを落としても台帳に無いので検出できず、守れる範囲が「台帳を書いた時点の授権」に 縮んでしまう。
 *
 * <p>台帳をここに置くのは、承認の記録を移行履歴から独立させるためである。
 */
class DefaultGrantApprovalTests {

  /** 既定授権の承認台帳。権限ごとに、その権限を持たせると承認済みの既定ロールを並べる（既定授与が無い権限は載せない）。 */
  private static final Map<PermissionCode, Set<SystemRole>> APPROVED_DEFAULT_GRANTS =
      Map.ofEntries(
          Map.entry(STORE_MANAGE, Set.of(ADMIN)),
          Map.entry(ROLE_MANAGE, Set.of(ADMIN)),
          Map.entry(STAFF_CREDENTIAL_MANAGE, Set.of(ADMIN, MANAGER)),
          Map.entry(STAFF_MANAGE, Set.of(ADMIN, MANAGER)),
          Map.entry(SYSTEM_CONFIG_MANAGE, Set.of(ADMIN)),
          Map.entry(BENEFIT_MANAGE, Set.of(ADMIN)),
          Map.entry(SERVICE_ID_MANAGE, Set.of(ADMIN)),
          Map.entry(SERVICE_MANAGE, Set.of(MANAGER)),
          Map.entry(STORE_VIEW, Set.of(ADMIN, MANAGER, STAFF)),
          Map.entry(ORDER_SET_VIEW, Set.of(ADMIN, MANAGER, STAFF)),
          Map.entry(ORDER_MANAGE, Set.of(MANAGER, STAFF)),
          Map.entry(CUSTOMER_MANAGE, Set.of(MANAGER, STAFF)),
          Map.entry(CUSTOMER_MERGE, Set.of(MANAGER)),
          Map.entry(ORDER_CORRECT, Set.of(MANAGER)),
          Map.entry(SHIFT_MANAGE, Set.of(MANAGER, STAFF)),
          Map.entry(CAST_PERSON_VIEW, Set.of(ADMIN)),
          Map.entry(MEMBER_VIEW, Set.of(ADMIN)),
          Map.entry(MEMBER_WITHDRAW, Set.of(ADMIN)),
          Map.entry(CAST_MANAGE, Set.of(MANAGER, STAFF)),
          Map.entry(CAST_INVITE, Set.of(MANAGER)),
          Map.entry(POINT_ADJUST, Set.of(MANAGER)),
          Map.entry(CAST_FIELD_DEF_VIEW, Set.of(MANAGER, STAFF)),
          Map.entry(CAST_FIELD_DEF_MANAGE, Set.of(MANAGER)),
          Map.entry(STORE_PROFILE_MANAGE, Set.of(MANAGER, STAFF)),
          Map.entry(ADVERTISING_COST_VIEW, Set.of(MANAGER)),
          Map.entry(ADVERTISING_COST_MANAGE, Set.of(MANAGER)),
          Map.entry(ADVERTISING_COST_EXPORT, Set.of(MANAGER)),
          Map.entry(ADVERTISING_COST_SET_VIEW, Set.of(ADMIN)),
          Map.entry(ADVERTISING_COST_SET_EXPORT, Set.of(ADMIN)),
          Map.entry(REVIEW_VIEW, Set.of(MANAGER, STAFF)),
          Map.entry(REVIEW_MANAGE, Set.of(MANAGER, STAFF)),
          Map.entry(REVIEW_MODERATE, Set.of(MANAGER)),
          Map.entry(SURVEY_VIEW, Set.of(MANAGER, STAFF)),
          Map.entry(SURVEY_MANAGE, Set.of(MANAGER)),
          Map.entry(SURVEY_RECORD, Set.of(MANAGER, STAFF)),
          Map.entry(NOTIFICATION_VIEW, Set.of(MANAGER, STAFF)),
          Map.entry(NOTIFICATION_MANAGE, Set.of(MANAGER)),
          Map.entry(NOTIFICATION_SEND, Set.of(MANAGER)),
          Map.entry(OPERATIONAL_REPORT_VIEW, Set.of(ADMIN, MANAGER)),
          Map.entry(OPERATIONAL_REPORT_EXPORT, Set.of(ADMIN, MANAGER)),
          Map.entry(TASK_MANAGE, Set.of(ADMIN)),
          Map.entry(NOTIFICATION_DELIVER, Set.of(ADMIN)),
          Map.entry(POINT_EXPIRE, Set.of(ADMIN)),
          Map.entry(AUDIT_VIEW, Set.of(ADMIN)),
          Map.entry(RECRUITMENT_VIEW, Set.of(MANAGER)),
          Map.entry(RECRUITMENT_MANAGE, Set.of(MANAGER)),
          Map.entry(RECRUITMENT_DECIDE, Set.of(MANAGER)),
          Map.entry(RECRUITMENT_ATTACHMENT_VIEW, Set.of(MANAGER)),
          Map.entry(RECRUITMENT_ATTACHMENT_MANAGE, Set.of(MANAGER)),
          Map.entry(REMUNERATION_VIEW, Set.of(ADMIN, MANAGER)),
          Map.entry(GUARANTEE_MANAGE, Set.of(MANAGER)),
          Map.entry(BONUS_AWARD, Set.of(MANAGER)),
          Map.entry(REMUNERATION_CORRECT, Set.of(MANAGER)));

  /** どの既定ロールにも与えないと承認済みの権限。サービス ID 専用か、取り消せない処理のため管理者が明示授与するもの。 */
  private static final Set<PermissionCode> APPROVED_WITHOUT_DEFAULT_GRANT =
      Set.of(TASK_EXECUTE, APPLICANT_ANONYMIZE);

  @Test
  @DisplayName("既定授与の無い権限は承認済みのものに限ること")
  void permissionsWithoutDefaultGrantsAreApproved() {
    // defaultRoles の省略は実行時 fail-closed だが、上の台帳照合は空の宣言を見ない。新しい権限が
    // 誰にも授与されないまま出荷されるのを単体段階の失敗に変える — 授与するか本集合へ載せるかを迫る。
    assertThat(PermissionCode.values())
        .filteredOn(code -> code.getDefaultRoles().isEmpty())
        .as("既定授与の無い権限")
        .containsExactlyInAnyOrderElementsOf(APPROVED_WITHOUT_DEFAULT_GRANT);
  }

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
  @DisplayName("全ての既定ロールが少なくとも 1 つの権限から宣言されていること")
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
