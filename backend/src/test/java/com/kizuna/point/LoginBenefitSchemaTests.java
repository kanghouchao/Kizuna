package com.kizuna.point;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ログイン特典の収束を DB に委ねている 2 点を DDL の字面で固定する。
 *
 * <p>同日の並行ログインは生産者の事前判定を二本とも通り抜けるので、高々 1 件に収束させるのは部分一意索引だけである。 述語（受注 ID が無い特典付与）と列の組が変わると、既存の
 * {@code uq_t_point_entries_benefit_firing} と同じく「決して衝突しない索引」が静かに生まれる。
 */
class LoginBenefitSchemaTests {

  private static final Path LEDGER =
      Paths.get("src/main/resources/db/changelog/releases/v0.1.0/platform/10-point-ledger.yaml");
  private static final Path RULES =
      Paths.get("src/main/resources/db/changelog/releases/v0.1.0/platform/12-benefit-rule.yaml");

  @Test
  @DisplayName("ログイン特典の付与日に (規則, 会員, 付与日) の部分一意索引が張られていること")
  void theDailyLoginGrantIsUniquePerRuleMemberAndDay() throws Exception {
    String ddl = normalized(LEDGER);

    assertThat(ddl)
        .contains("name: granted_on type: DATE")
        .contains(
            "CREATE UNIQUE INDEX uq_t_point_entries_login_benefit_day"
                + " ON t_point_entries (benefit_rule_id, member_id, granted_on)"
                + " WHERE benefit_rule_id IS NOT NULL AND order_id IS NULL");
  }

  @Test
  @DisplayName("付与日を持つのは受注 ID の無い特典付与だけであることを CHECK で縛っていること")
  void onlyLoginGrantsCarryAGrantDate() throws Exception {
    assertThat(normalized(LEDGER))
        .contains(
            "ADD CONSTRAINT ck_t_point_entries_granted_on_shape CHECK ("
                + " (entry_type = 'BENEFIT_GRANT' AND order_id IS NULL) = (granted_on IS NOT NULL))");
  }

  @Test
  @DisplayName("ログイン規則の重複可否は一人一回限り・一日一回に限ることを DB も縛っていること")
  void loginRulesNeverTakeEveryTimeAtTheDatabase() throws Exception {
    assertThat(normalized(RULES))
        .contains(
            "ADD CONSTRAINT ck_t_benefit_rules_login_repeat CHECK ("
                + " type <> 'LOGIN' OR repeat_policy IN ('ONCE_PER_MEMBER', 'ONCE_PER_DAY'))");
  }

  /** YAML の折り返しと字下げを 1 空白へ畳み、SQL の字面だけを比べる。 */
  private static String normalized(Path file) throws Exception {
    return Files.readString(file).replaceAll("\\s+", " ");
  }
}
