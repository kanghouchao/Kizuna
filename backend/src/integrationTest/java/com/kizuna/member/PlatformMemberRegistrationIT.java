package com.kizuna.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 会員の自助登録とポータルホーム API を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>登録が MEMBER 身分（ロールなし・紐づけ店舗なし）と一意の会員コードを単一トランザクションで作成すること、会員が本人データのみ閲覧でき コンソール系端点・店舗文脈へ到達できないことを
 * HTTP 境界で固定する。
 */
class PlatformMemberRegistrationIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private MemberRepository memberRepository;

  @Test
  @DisplayName("登録で MEMBER 身分（ロールなし・紐づけ店舗なし）と数字 12 桁の会員コードが発行されること")
  void registrationCreatesMemberIdentityAndCode() {
    String email = uniqueEmail("member-new");

    ResponseEntity<JsonNode> res = register(email, PASSWORD, "会員花子");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String memberCode = res.getBody().path("member_code").asString();
    assertThat(memberCode).matches("\\d{12}");
    PlatformUser user = platformUserRepository.findByEmail(email).orElseThrow();
    assertThat(user.getUserType()).isEqualTo(UserType.MEMBER);
    assertThat(user.getRoleIds()).isEmpty();
    assertThat(user.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(user.getStoreIds()).isEmpty();
    Member member = memberRepository.findByPlatformUserId(user.getId()).orElseThrow();
    assertThat(member.getMemberCode()).isEqualTo(memberCode);
  }

  @Test
  @DisplayName("登録直後にログインしてホームで本人の会員コードと表示名が取得できること")
  void memberHomeReturnsOwnCodeRightAfterRegistration() {
    String email = uniqueEmail("member-home");
    String memberCode = register(email, PASSWORD, "ホーム花子").getBody().path("member_code").asString();

    ResponseEntity<JsonNode> home = memberHome(platformToken(email, PASSWORD));

    assertThat(home.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(home.getBody().path("member_code").asString()).isEqualTo(memberCode);
    assertThat(home.getBody().path("display_name").asString()).isEqualTo("ホーム花子");
  }

  @Test
  @DisplayName("ホームは本人の会員コードのみを返すこと（別会員のコードが混入しない）")
  void memberHomeNeverReturnsAnotherMembersCode() {
    String emailA = uniqueEmail("member-a");
    String emailB = uniqueEmail("member-b");
    String codeA = register(emailA, PASSWORD, "会員A").getBody().path("member_code").asString();
    String codeB = register(emailB, PASSWORD, "会員B").getBody().path("member_code").asString();
    assertThat(codeA).isNotEqualTo(codeB);

    ResponseEntity<JsonNode> homeA = memberHome(platformToken(emailA, PASSWORD));
    ResponseEntity<JsonNode> homeB = memberHome(platformToken(emailB, PASSWORD));

    assertThat(homeA.getBody().path("member_code").asString()).isEqualTo(codeA);
    assertThat(homeB.getBody().path("member_code").asString()).isEqualTo(codeB);
  }

  @Test
  @DisplayName("既に登録済みのメールでの登録は 4xx で拒否され、会員行が作成されないこと")
  void duplicateEmailRegistrationIsRejectedWithoutSideEffects() {
    String email = uniqueEmail("member-dup");
    register(email, PASSWORD, "先着花子");
    long memberCountBefore = memberRepository.count();

    ResponseEntity<JsonNode> res = register(email, PASSWORD, "後着次郎");

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    assertThat(memberRepository.count()).isEqualTo(memberCountBefore);
  }

  @Test
  @DisplayName("短すぎるパスワードでの登録は 400 で拒否され、副作用がないこと")
  void tooShortPasswordRegistrationIsRejected() {
    String email = uniqueEmail("member-shortpw");

    ResponseEntity<JsonNode> res = register(email, "a", "花子");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(platformUserRepository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("列長(150)を超える表示名での登録は 400 で拒否され、副作用がないこと")
  void tooLongDisplayNameRegistrationIsRejected() {
    String email = uniqueEmail("member-longname");

    ResponseEntity<JsonNode> res = register(email, PASSWORD, "あ".repeat(151));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(platformUserRepository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("陳腐な Bearer を付けたまま登録を叩いても匿名で通ること（統一ログイン画面は既存 token cookie を持ちうる）")
  void registrationWithBrokenBearerStillSucceedsAnonymously() {
    String email = uniqueEmail("member-broken-bearer");
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth("stale-or-broken-token-value");

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(registrationBody(email, PASSWORD, "陳腐Bearer花子"), headers),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(platformUserRepository.findByEmail(email)).isPresent();
  }

  @Test
  @DisplayName("会員トークンではコンソール系端点（スタッフ管理）に到達できないこと（403）")
  void memberCannotAccessConsoleEndpoints() {
    String email = uniqueEmail("member-console");
    register(email, PASSWORD, "コンソール拒否花子");
    String memberToken = platformToken(email, PASSWORD);

    ResponseEntity<String> res =
        rest.exchange(
            "/platform/staff", HttpMethod.GET, new HttpEntity<>(bearer(memberToken)), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("会員トークンでは店舗文脈（X-Store-ID）を確立できないこと（403）")
  void memberCannotClaimStoreContext() {
    String email = uniqueEmail("member-store");
    register(email, PASSWORD, "店舗文脈拒否花子");
    String memberToken = platformToken(email, PASSWORD);
    HttpHeaders headers = bearer(memberToken);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(STORE_A));

    ResponseEntity<String> res =
        rest.exchange("/store/customers", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("MEMBER 以外のトークンではホームに到達できないこと（403）")
  void nonMemberCannotAccessMemberHome() {
    // 基底クラスの token は STAFF（店舗スタッフ）。
    ResponseEntity<JsonNode> res = memberHome(token);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("未認証ではホームに到達できないこと（401/403）")
  void unauthenticatedCannotAccessMemberHome() {
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/me/member", HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);

    assertThat(res.getStatusCode().value()).isIn(401, 403);
  }

  private static String uniqueEmail(String prefix) {
    return prefix + "-it-" + System.nanoTime() + "@kizuna.test";
  }

  private ResponseEntity<JsonNode> register(String email, String password, String displayName) {
    return rest.postForEntity(
        "/platform/members",
        new HttpEntity<>(registrationBody(email, password, displayName), jsonHeaders()),
        JsonNode.class);
  }

  private static String registrationBody(String email, String password, String displayName) {
    return String.format(
        "{\"email\": \"%s\", \"password\": \"%s\", \"display_name\": \"%s\"}",
        email, password, displayName);
  }

  private ResponseEntity<JsonNode> memberHome(String bearerToken) {
    return rest.exchange(
        "/platform/me/member",
        HttpMethod.GET,
        new HttpEntity<>(bearer(bearerToken)),
        JsonNode.class);
  }

  private String platformToken(String email, String password) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password),
                jsonHeaders()),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    String t = res.getBody().path("token").asString();
    assertThat(t).isNotBlank();
    return t;
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders bearer(String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
