package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.domain.Authorities;
import com.kizuna.user.domain.Capability;
import com.kizuna.user.domain.UserType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code @PreAuthorize} の字面と授権モデル（{@link Capability} / {@link UserType}）の整合を機械検証する（#430）。
 *
 * <p>{@code @PreAuthorize("hasAuthority('PERM_…')")} の字面は多数の Controller に手書きされており、{@code
 * Capability} enum とのコンパイル期関連が一切ない。typo は「到達不能な権限」を暗黙に生み、機能 IT が偶然踏まない限り検出されない。{@link
 * StoreIsolationTests}（#216 由来）と同型の fitness test でこの欠陥類を閉じる。
 *
 * <p>許可リスト fail-loud が本テストの核心である。想定外の SpEL 形状（{@code hasRole} 等）や解析不能な字面（定数参照・連結・複数行）は暗黙にスキップせず必ず
 * fail させる。逆方向の検査（全 {@code Capability} が必ず Controller から参照される）は行わない — menu データ面のみで消費される能力があり誤報するため。
 */
class PreAuthorizeCapabilityTests {

  /** SecurityContext 上の権限（Capability）authority の接頭辞。知識は {@link Authorities} の唯一の継ぎ目に集約する。 */
  private static final String PERMISSION_PREFIX = Authorities.permission("");

  /** buildAuthorities（PlatformAuthService）が CAST / MEMBER に発行する role authority の接頭辞。 */
  private static final String ROLE_PREFIX = "ROLE_";

  /** {@code @PreAuthorize(} の位置を数える広いマッチャー。字面の抽出は別途厳密に行う。 */
  private static final Pattern ANNOTATION = Pattern.compile("@PreAuthorize\\s*\\(");

  private static final Pattern HAS_AUTHORITY =
      Pattern.compile("hasAuthority\\(\\s*'([^']*)'\\s*\\)");
  private static final Pattern HAS_ANY_AUTHORITY = Pattern.compile("hasAnyAuthority\\((.*)\\)");
  private static final Pattern QUOTED_TOKEN = Pattern.compile("'([^']*)'");

  private int occurrences = 0;
  private int permissionTokens = 0;
  private final List<String> offenders = new ArrayList<>();

  @Test
  @DisplayName("全 @PreAuthorize の SpEL token が Capability / UserType へ写像でき、許可リスト外の形状が存在しないこと")
  void allPreAuthorizeTokensMapToAuthorizationModel() throws Exception {
    Path root = Paths.get("src/main/java");
    List<Path> javaFiles;
    try (Stream<Path> paths = Files.walk(root)) {
      javaFiles =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".java"))
              .toList();
    }

    for (Path file : javaFiles) {
      scan(Files.readString(file), root.relativize(file).toString());
    }

    // 暗黙の no-op 防止: 走査が実際に対象を捉えていることを担保する。
    assertThat(javaFiles).as("src/main/java 配下の .java ソース").isNotEmpty();
    assertThat(occurrences).as("走査した @PreAuthorize の総数").isGreaterThan(0);
    assertThat(permissionTokens).as("抽出した PERM_ token の総数").isGreaterThan(0);

    assertThat(offenders).as("Capability / UserType へ写像できない、または許可リスト外の @PreAuthorize").isEmpty();
  }

  /** 1 ソース中の全 {@code @PreAuthorize} 出現を走査し、字面を抽出して評価する。 */
  private void scan(String content, String relativePath) {
    Matcher matcher = ANNOTATION.matcher(content);
    while (matcher.find()) {
      occurrences++;
      // マッチャーは末尾が '(' なので、その '(' は matcher.end() - 1 にある。
      String literal = extractLiteral(content, matcher.end() - 1);
      if (literal == null) {
        offenders.add(relativePath + ": @PreAuthorize の値が単一行の文字列リテラルとして解析できない（定数参照・連結・複数行など）");
        continue;
      }
      evaluate(literal, relativePath);
    }
  }

  /**
   * {@code @PreAuthorize(} の直後から、単一行・単一の文字列リテラルを厳密に取り出す。定数参照・文字列連結・追加引数・複数行・テキストブロック等、
   * 確信を持って解析できない形状は {@code null} を返して fail-loud させる。
   */
  private static String extractLiteral(String content, int openParen) {
    int i = openParen + 1;
    // 開き括弧の直後は、空白を挟んで文字列リテラルの開始 '"' のみを許す。
    while (i < content.length() && content.charAt(i) != '"') {
      if (!Character.isWhitespace(content.charAt(i))) {
        return null;
      }
      i++;
    }
    if (i >= content.length()) {
      return null;
    }
    int start = i + 1;
    int end = content.indexOf('"', start);
    if (end < 0) {
      return null;
    }
    String literal = content.substring(start, end);
    if (literal.indexOf('\n') >= 0 || literal.indexOf('\r') >= 0) {
      return null;
    }
    // 閉じ '"' の後は、空白を挟んで閉じ括弧 ')' のみを許す（連結や追加引数を弾く）。
    int j = end + 1;
    while (j < content.length() && Character.isWhitespace(content.charAt(j))) {
      j++;
    }
    if (j >= content.length() || content.charAt(j) != ')') {
      return null;
    }
    return literal;
  }

  /** SpEL を最上位の {@code or} / {@code and} で項に分け、各項を許可リストで評価する。 */
  private void evaluate(String literal, String relativePath) {
    for (String rawTerm : splitTopLevel(literal)) {
      String term = rawTerm.trim();
      Matcher one = HAS_AUTHORITY.matcher(term);
      Matcher any = HAS_ANY_AUTHORITY.matcher(term);
      if (one.matches()) {
        validateToken(one.group(1), literal, relativePath);
      } else if (any.matches()) {
        String args = any.group(1).trim();
        if (args.isEmpty()) {
          offenders.add(relativePath + ": hasAnyAuthority に token が無い — \"" + literal + "\"");
        } else {
          // 引数はすべて引用リテラルでなければならない。T(...) 等の非リテラルが混じっても
          // 暗黙に無視されないよう、カンマ区切りの各引数を厳密照合する。
          for (String rawArg : args.split(",")) {
            String arg = rawArg.trim();
            Matcher quoted = QUOTED_TOKEN.matcher(arg);
            if (quoted.matches()) {
              validateToken(quoted.group(1), literal, relativePath);
            } else {
              offenders.add(
                  relativePath
                      + ": hasAnyAuthority の引数が引用リテラルでない ["
                      + arg
                      + "] — \""
                      + literal
                      + "\"");
            }
          }
        }
      } else if (term.equals("isAuthenticated()")) {
        // 認証済み判定のみ。token 寄与なし。
      } else if (term.equals("@storeBridge.check(authentication)")) {
        // PR #432 の守衛式を許可する既知の項。それ以外の bean 参照形は意識的な許可リスト拡張を
        // 強制するため不許可（fail-loud）。token 寄与なし。
      } else {
        offenders.add(relativePath + ": 許可リスト外の SpEL 項 [" + term + "] — \"" + literal + "\"");
      }
    }
  }

  /**
   * SpEL を最上位（括弧の外・引用符の外）の {@code " or "} / {@code " and "}
   * でのみ分割する。括弧で括られたグループはここでは分割されず、単一項として許可リスト照合で 弾かれる（確信を持って解析できないグループは fail-loud）。
   */
  private static List<String> splitTopLevel(String spel) {
    List<String> terms = new ArrayList<>();
    int depth = 0;
    boolean inQuote = false;
    int termStart = 0;
    int i = 0;
    while (i < spel.length()) {
      char c = spel.charAt(i);
      if (inQuote) {
        if (c == '\'') {
          inQuote = false;
        }
        i++;
      } else if (c == '\'') {
        inQuote = true;
        i++;
      } else if (c == '(') {
        depth++;
        i++;
      } else if (c == ')') {
        depth--;
        i++;
      } else if (depth == 0 && spel.startsWith(" or ", i)) {
        terms.add(spel.substring(termStart, i));
        i += 4;
        termStart = i;
      } else if (depth == 0 && spel.startsWith(" and ", i)) {
        terms.add(spel.substring(termStart, i));
        i += 5;
        termStart = i;
      } else {
        i++;
      }
    }
    terms.add(spel.substring(termStart));
    return terms;
  }

  /**
   * token を接頭辞で分類して検証する。{@code PERM_<X>} は {@link Capability} 成員、{@code ROLE_<X>} は STAFF 以外の
   * {@link UserType} 成員でなければならない。それ以外の接頭辞は fail-loud。
   */
  private void validateToken(String token, String literal, String relativePath) {
    if (token.startsWith(PERMISSION_PREFIX)) {
      permissionTokens++;
      String name = token.substring(PERMISSION_PREFIX.length());
      boolean valid;
      try {
        Capability capability = Capability.valueOf(name);
        // PERM_ 接頭辞の知識は Authorities に集約し、round-trip 一致で写像を担保する。
        valid = Authorities.permission(capability.name()).equals(token);
      } catch (IllegalArgumentException e) {
        valid = false;
      }
      if (!valid) {
        offenders.add(
            relativePath
                + ": PERM_ token が Capability に写像できない ["
                + token
                + "] — \""
                + literal
                + "\"");
      }
    } else if (token.startsWith(ROLE_PREFIX)) {
      String name = token.substring(ROLE_PREFIX.length());
      boolean valid;
      try {
        // STAFF は PERM_ 授権のみで ROLE_ authority を発行しない（PlatformAuthService.buildAuthorities）。
        valid = UserType.valueOf(name) != UserType.STAFF;
      } catch (IllegalArgumentException e) {
        valid = false;
      }
      if (!valid) {
        offenders.add(
            relativePath
                + ": ROLE_ token が UserType（STAFF 以外）に写像できない ["
                + token
                + "] — \""
                + literal
                + "\"");
      }
    } else {
      offenders.add(relativePath + ": 未知の接頭辞を持つ token [" + token + "] — \"" + literal + "\"");
    }
  }
}
