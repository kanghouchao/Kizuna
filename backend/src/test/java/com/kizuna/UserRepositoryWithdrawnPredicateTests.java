package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code UserRepository} の電子メール・LINE ユーザー ID による問い合わせがすべて未退会条件を持つことの適応度テスト（ADR 0029）。
 *
 * <p>退会した行は識別子を保持したまま残るので、条件を落とした派生クエリは同じ電子メールの新しい身分と並んで非一意になり、 認証の候補に退会者が混ざる。Spring Data
 * の派生クエリは宣言だけで条件を持てないため、識別子で引く方法は {@code @Query} に 限り、その JPQL が {@code withdrawnAt is null}
 * を含むことを字面で検査する。
 */
class UserRepositoryWithdrawnPredicateTests {

  private static final Path REPOSITORY =
      Paths.get("src/main/java/com/kizuna/user/domain/UserRepository.java");

  /** 宣言は {@code ;} で区切られ、直前の注釈列（{@code @Query} の JPQL を含む）は同じ塊に入る。 */
  private static final Pattern METHOD_DECLARATION =
      Pattern.compile("(\\w+)\\s*\\((?:[^()]|\\([^()]*\\))*\\)\\s*$");

  private static final Pattern IDENTIFIER_QUERY = Pattern.compile("(?i)email|lineuserid");

  @Test
  @DisplayName("email / lineUserId で引く問い合わせは @Query で未退会条件を持つこと")
  void identifierLookupsOnlyMatchActiveUsers() throws Exception {
    String source = Files.readString(REPOSITORY);
    String activeByEmail = constant(source, "ACTIVE_BY_EMAIL");
    List<String> identifierMethods = new ArrayList<>();
    List<String> offenders = new ArrayList<>();
    for (String declaration : source.split(";")) {
      Matcher matcher = METHOD_DECLARATION.matcher(declaration);
      if (!matcher.find() || !IDENTIFIER_QUERY.matcher(matcher.group(1)).find()) {
        continue;
      }
      String name = matcher.group(1);
      identifierMethods.add(name);
      String expanded = declaration.replace("ACTIVE_BY_EMAIL", activeByEmail);
      if (!expanded.contains("@Query") || !expanded.contains("withdrawnAt is null")) {
        offenders.add(name);
      }
    }
    assertThat(identifierMethods).as("識別子で引く問い合わせを 1 つも捉えていない").isNotEmpty();
    assertThat(offenders).as("未退会条件を持たない識別子の問い合わせ").isEmpty();
  }

  private static String constant(String source, String name) {
    Matcher matcher =
        Pattern.compile("String\\s+" + name + "\\s*=\\s*\"([^\"]*)\"").matcher(source);
    assertThat(matcher.find()).as(name + " の定義").isTrue();
    return matcher.group(1);
  }
}
