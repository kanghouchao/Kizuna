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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 基線 seed（{@code seed/01-roles.yaml}）の権限目録行と既定ロールへの授与行が、コード側の宣言（{@link PermissionCode} と {@code
 * defaultRoles}）と完全一致することを機械検証する。
 *
 * <p>reconcile は既定ロール行を初めて作る時にしか授与を播種せず、基線は常に先に走ってロール行を作る。したがって新しい DB の既定授与は基線だけが決め、
 * 宣言と基線がずれると「enum には書いたが誰も持たない権限」が静默に生まれる。目録行の方向も同じで、基線に無い権限は reconcile が目録へ足すが授与は付かない。
 */
class PermissionSeedLiteralTests {

  private static final Path ROLES_SEED =
      Paths.get("src/main/resources/db/changelog/releases/v0.1.0/seed/01-roles.yaml");

  private static final Pattern PERMISSION_ROW =
      Pattern.compile(
          "tableName: t_permissions\\s*\\n\\s*columns:\\s*\\n\\s*- column: \\{ name: code, value: (\\w+) \\}");

  private static final Pattern GRANT_ROW =
      Pattern.compile(
          "WHERE name = '([^']+)'\\)\"\\s*\\}\\s*\\n\\s*- column: \\{ name: permission_id,"
              + " valueComputed: \"\\(SELECT id FROM t_permissions WHERE code = '(\\w+)'\\)\" \\}");

  @Test
  @DisplayName("基線 seed の権限目録行は PermissionCode の全件と一致すること")
  void seedCatalogueMatchesEnum() throws Exception {
    String yaml = Files.readString(ROLES_SEED);
    Set<String> seeded = new HashSet<>();
    Matcher matcher = PERMISSION_ROW.matcher(yaml);
    while (matcher.find()) {
      seeded.add(matcher.group(1));
    }
    Set<String> declared =
        Arrays.stream(PermissionCode.values())
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toSet());

    assertThat(seeded).as("seed/01-roles.yaml の t_permissions 行").isEqualTo(declared);
  }

  @Test
  @DisplayName("基線 seed の既定ロールへの授与行は PermissionCode.defaultRoles と一致すること")
  void seedGrantsMatchDeclaredDefaultRoles() throws Exception {
    String yaml = Files.readString(ROLES_SEED);
    Map<String, Set<String>> seeded = new HashMap<>();
    Matcher matcher = GRANT_ROW.matcher(yaml);
    while (matcher.find()) {
      seeded.computeIfAbsent(matcher.group(1), key -> new HashSet<>()).add(matcher.group(2));
    }
    Map<String, Set<String>> declared = new HashMap<>();
    for (SystemRole role : SystemRole.values()) {
      declared.put(role.getRoleName(), new HashSet<>());
    }
    for (PermissionCode code : PermissionCode.values()) {
      for (SystemRole role : code.getDefaultRoles()) {
        declared.get(role.getRoleName()).add(code.name());
      }
    }

    // 暗黙の no-op 防止: 走査が実際に授与行を捉えていることを担保する。
    assertThat(seeded.values().stream().mapToInt(Set::size).sum()).isGreaterThan(0);
    assertThat(seeded)
        .as("seed/01-roles.yaml の t_role_permissions 行（ロール名 → 権限）")
        .isEqualTo(declared);
  }
}
