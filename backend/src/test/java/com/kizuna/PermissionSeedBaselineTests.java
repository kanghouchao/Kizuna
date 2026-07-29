package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.SystemRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 凍結された基線播種（v0.1.0 の 01-roles.yaml）が投入した権限目録・既定授与を、コード側宣言（{@link
 * PermissionCode#getDefaultRoles()}）が 失っていないことを機械検証する。
 *
 * <p>目録行と既定授与の供給は v0.5.0 で宣言由来の播種へ移り、既定ロールの授与は宣言の写像として毎回取り直される。つまり宣言から権限やロールを取り下げると、それだけで稼働中の DB
 * からも授権が消える。基線が投入した授権はこの製品が既定として与えると決めたものなので、取り下げは「うっかり」で通ってはならない — 本テストはその 1
 * 段の摩擦であり、意図した取り下げのときだけ前提（基線の一覧）を併せて改める。
 *
 * <p>逆に宣言を増やす変更（新しい権限、既存権限の授与先追加）は播種がそのまま追随できるため、本テストは通す。
 */
class PermissionSeedBaselineTests {

  private static final Path BASELINE_SEED =
      Paths.get("src/main/resources/db/changelog/releases/v0.1.0/seed/01-roles.yaml");

  /** 権限目録行の insert（{@code t_permissions} への code 1 列）。 */
  private static final Pattern CATALOGUE_ROW =
      Pattern.compile(
          "tableName: t_permissions\\s+columns:\\s+- column: \\{ name: code, value: (\\w+) \\}");

  /** 既定ロール行の insert（{@code t_roles} への name 列）。 */
  private static final Pattern ROLE_ROW =
      Pattern.compile(
          "tableName: t_roles\\s+columns:\\s+- column: \\{ name: name, value: \"([^\"]+)\" \\}");

  /** 授与行の insert。role_id → permission_id の順に並ぶ 2 列を 1 組として取る。 */
  private static final Pattern GRANT_ROW =
      Pattern.compile(
          "t_roles WHERE name = '([^']+)'[\\s\\S]*?t_permissions WHERE code = '(\\w+)'");

  @Test
  @DisplayName("基線が播種した権限が全て PermissionCode 成員として実在すること")
  void baselinePermissionsStillDeclared() throws Exception {
    Set<String> baseline = captures(CATALOGUE_ROW);
    Set<String> declared =
        Arrays.stream(PermissionCode.values())
            .map(PermissionCode::name)
            .collect(Collectors.toSet());

    assertThat(baseline).as("基線播種の権限目録行").isNotEmpty();
    assertThat(declared).as("PermissionCode の宣言").containsAll(baseline);
  }

  @Test
  @DisplayName("基線が播種したロール名が全て SystemRole 成員として実在すること")
  void baselineSystemRolesStillDeclared() throws Exception {
    Set<String> baseline = captures(ROLE_ROW);
    Set<String> declared =
        Arrays.stream(SystemRole.values()).map(SystemRole::getRoleName).collect(Collectors.toSet());

    assertThat(baseline).as("基線播種の既定ロール行").isNotEmpty();
    assertThat(declared).as("SystemRole の宣言").containsAll(baseline);
  }

  @Test
  @DisplayName("基線が播種した既定授与が権限ごとにコード側宣言へ含まれること")
  void baselineGrantsStillDeclared() throws Exception {
    Map<String, Set<String>> baseline = baselineGrantsByPermission();

    // 暗黙の no-op 防止: 基線の授与 27 行（HQ管理者 7 / 店長 11 / 店舗スタッフ 9）を実際に読めていること。
    assertThat(baseline.values().stream().mapToInt(Set::size).sum())
        .as("基線播種の授与行の総数")
        .isEqualTo(27);

    baseline.forEach(
        (code, roleNames) ->
            assertThat(declaredRoleNames(code)).as("%s の既定ロール", code).containsAll(roleNames));
  }

  private static Set<String> declaredRoleNames(String code) {
    return PermissionCode.valueOf(code).getDefaultRoles().stream()
        .map(SystemRole::getRoleName)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static Map<String, Set<String>> baselineGrantsByPermission() throws Exception {
    Map<String, Set<String>> byCode = new HashMap<>();
    Matcher matcher = GRANT_ROW.matcher(Files.readString(BASELINE_SEED));
    while (matcher.find()) {
      byCode.computeIfAbsent(matcher.group(2), key -> new TreeSet<>()).add(matcher.group(1));
    }
    return byCode;
  }

  private static Set<String> captures(Pattern pattern) throws Exception {
    Set<String> values = new HashSet<>();
    Matcher matcher = pattern.matcher(Files.readString(BASELINE_SEED));
    while (matcher.find()) {
      values.add(matcher.group(1));
    }
    return values;
  }
}
