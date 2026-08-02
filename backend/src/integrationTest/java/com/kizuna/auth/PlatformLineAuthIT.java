package com.kizuna.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * LINE を認証手段とするログイン・登録・連携の統合テスト。実チャネルは存在しないため、LINE プラットフォーム API を ループバックのスタブへ差し替える（{@code
 * app.line.api-base-url}）。
 *
 * <p>スタブはクラスロード時（Spring の文脈生成より前）に起動し、確定したポートを {@code @DynamicPropertySource} が読む — {@code
 * AppProperties} は文脈初期化時に束縛されるため、後から決まる値は使えない。
 *
 * <p>本テストが固定する受入条件のうち中核は「なりすまし不能」: LINE 側が検証したメールアドレスが既存アカウントと一致しても、 未知の LINE ユーザー ID
 * は必ず登録チケット経路へ落ち、既存アカウントのトークンは決して発行されない。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PlatformLineAuthIT {

  private static final String MEMBER_PASSWORD = "password1234";

  /** スタブが返す検証済み同一性。各テストが自分の筋書きに合わせて差し替える。 */
  private static volatile String stubSub = "U-line-default";

  private static volatile String stubName = "LINE太郎";

  private static volatile String stubEmail = "line-default@kizuna.test";

  private static final HttpServer LINE_STUB = startLineStub();

  @Autowired private TestRestTemplate rest;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private MemberRepository memberRepository;

  private static HttpServer startLineStub() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/oauth2/v2.1/token", exchange -> respond(exchange, tokenBody()));
      server.createContext("/oauth2/v2.1/verify", exchange -> respond(exchange, verifyBody()));
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String tokenBody() {
    return "{\"id_token\": \"stub-id-token\"}";
  }

  private static String verifyBody() {
    return String.format(
        "{\"sub\": \"%s\", \"name\": \"%s\", \"email\": \"%s\"}", stubSub, stubName, stubEmail);
  }

  private static void respond(HttpExchange exchange, String body) throws IOException {
    try (InputStream in = exchange.getRequestBody()) {
      in.readAllBytes();
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  @DynamicPropertySource
  static void lineProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "app.line.api-base-url", () -> "http://127.0.0.1:" + LINE_STUB.getAddress().getPort());
    registry.add("app.line.channel-id", () -> "it-channel-id");
    registry.add("app.line.channel-secret", () -> "it-channel-secret");
  }

  private static String uniqueSub(String prefix) {
    return "U-" + prefix + "-" + System.nanoTime();
  }

  private static String uniqueEmail(String prefix) {
    return prefix + "-line-it-" + System.nanoTime() + "@kizuna.test";
  }

  private void stubIdentity(String sub, String name, String email) {
    stubSub = sub;
    stubName = name;
    stubEmail = email;
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private static String authorizationBody() {
    return "{\"code\": \"stub-auth-code\", \"redirect_uri\": \"https://app.test/line/callback\","
        + " \"code_verifier\": \"stub-code-verifier\"}";
  }

  private ResponseEntity<JsonNode> lineLogin() {
    return rest.postForEntity(
        "/platform/line/login",
        new HttpEntity<>(authorizationBody(), jsonHeaders()),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> lineRegister(String ticket, String displayName, String email) {
    String body =
        String.format(
            "{\"registration_ticket\": \"%s\", \"display_name\": \"%s\", \"email\": \"%s\"}",
            ticket, displayName, email);
    return rest.postForEntity(
        "/platform/line/register", new HttpEntity<>(body, jsonHeaders()), JsonNode.class);
  }

  private ResponseEntity<JsonNode> linkLine(HttpHeaders headers) {
    return rest.exchange(
        "/platform/me/line",
        HttpMethod.POST,
        new HttpEntity<>(authorizationBody(), headers),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> getMe(String token) {
    return rest.exchange(
        "/platform/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
  }

  /** メール + パスワードで会員を登録し、そのトークンを返す（連携の前提となる既存身分の用意）。 */
  private String registerMemberAndLogin(String email, String displayName) {
    String body =
        String.format(
            "{\"email\": \"%s\", \"password\": \"%s\", \"display_name\": \"%s\"}",
            email, MEMBER_PASSWORD, displayName);
    ResponseEntity<JsonNode> registered =
        rest.postForEntity(
            "/platform/members", new HttpEntity<>(body, jsonHeaders()), JsonNode.class);
    assertThat(registered.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, MEMBER_PASSWORD),
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    return login.getBody().path("token").asString();
  }

  /** 未登録の LINE アカウントでログインし、登録チケットを取り出す。 */
  private String ticketForUnknownSub(String sub, String name, String email) {
    stubIdentity(sub, name, email);
    ResponseEntity<JsonNode> login = lineLogin();
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login.getBody().path("registered").asBoolean()).isFalse();
    String ticket = login.getBody().path("registration_ticket").asString();
    assertThat(ticket).isNotBlank();
    return ticket;
  }

  @Test
  @DisplayName("チャネル設定済みなら config は enabled=true とチャネル ID を返す（前端が認可要求を組み立てられる）")
  void configExposesChannelId() {
    ResponseEntity<JsonNode> res = rest.getForEntity("/platform/line/config", JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("enabled").asBoolean()).isTrue();
    assertThat(res.getBody().path("channel_id").asString()).isEqualTo("it-channel-id");
  }

  @Test
  @DisplayName("未登録 LINE アカウントは登録チケット経由で MEMBER 身分と会員コードを得て、そのままログイン状態になる")
  void unknownLineUserRegistersAndLogsIn() {
    String sub = uniqueSub("new");
    String email = uniqueEmail("line-new");
    String ticket = ticketForUnknownSub(sub, "LINE花子", email);

    ResponseEntity<JsonNode> registered = lineRegister(ticket, "会員花子", email);

    assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String token = registered.getBody().path("token").asString();
    assertThat(token).isNotBlank();
    assertThat(registered.getBody().path("expires_at").asLong()).isPositive();

    PlatformUser user = platformUserRepository.findByEmail(email).orElseThrow();
    assertThat(user.getUserType()).isEqualTo(UserType.MEMBER);
    assertThat(user.getLineUserId()).isEqualTo(sub);
    assertThat(user.getRoleIds()).isEmpty();
    assertThat(user.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(user.getStoreIds()).isEmpty();
    Member member = memberRepository.findByPlatformUserId(user.getId()).orElseThrow();
    assertThat(member.getMemberCode()).matches("\\d{12}");

    // 発行トークンは会員端点で通用する（パスワードログインと同じ claim が載っている）。
    ResponseEntity<JsonNode> home =
        rest.exchange(
            "/platform/me/member", HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
    assertThat(home.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(home.getBody().path("member_code").asString()).isEqualTo(member.getMemberCode());
  }

  @Test
  @DisplayName("連携済み LINE アカウントのログインは登録を挟まずトークンを返す")
  void knownLineUserLogsInDirectly() {
    String sub = uniqueSub("known");
    String email = uniqueEmail("line-known");
    lineRegister(ticketForUnknownSub(sub, "LINE次郎", email), "会員次郎", email);

    ResponseEntity<JsonNode> login = lineLogin();

    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login.getBody().path("registered").asBoolean()).isTrue();
    assertThat(login.getBody().has("registration_ticket")).isFalse();
    String token = login.getBody().path("token").asString();
    ResponseEntity<JsonNode> me = getMe(token);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody().path("email").asString()).isEqualTo(email);
    assertThat(me.getBody().path("user_type").asString()).isEqualTo("MEMBER");
    assertThat(me.getBody().path("line_linked").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("LINE 側のメールが既存アカウントと一致しても、未知の LINE ID では既存アカウントのトークンを発行しない（なりすまし防止）")
  void matchingEmailNeverAutoLinksExistingAccount() {
    String existingEmail = uniqueEmail("line-impersonation");
    registerMemberAndLogin(existingEmail, "既存花子");
    PlatformUser before = platformUserRepository.findByEmail(existingEmail).orElseThrow();
    assertThat(before.getLineUserId()).as("前提: 既存アカウントは未連携").isNull();

    // LINE 側は「この既存アカウントと同じメール」を検証済みとして返すが、LINE ユーザー ID は未知。
    stubIdentity(uniqueSub("impersonator"), "なりすまし花子", existingEmail);
    ResponseEntity<JsonNode> login = lineLogin();

    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login.getBody().path("registered").asBoolean()).isFalse();
    assertThat(login.getBody().has("token")).as("既存アカウントのトークンを返さないこと").isFalse();
    assertThat(login.getBody().path("registration_ticket").asString()).isNotBlank();

    // 登録確定でも既存メールは奪えず、既存アカウントは未連携のまま。
    ResponseEntity<JsonNode> registered =
        lineRegister(
            login.getBody().path("registration_ticket").asString(), "なりすまし花子", existingEmail);

    assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    PlatformUser after = platformUserRepository.findByEmail(existingEmail).orElseThrow();
    assertThat(after.getLineUserId()).isNull();
    assertThat(after.getId()).isEqualTo(before.getId());
  }

  @Test
  @DisplayName("認証済み本人への連携は 204 で、以後その LINE アカウントで本人としてログインできる")
  void linkAttachesLineAccountToAuthenticatedUser() {
    String email = uniqueEmail("line-link");
    String token = registerMemberAndLogin(email, "連携花子");
    String sub = uniqueSub("link");
    stubIdentity(sub, "LINE花子", uniqueEmail("line-other"));

    assertThat(linkLine(bearer(token)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(getMe(token).getBody().path("line_linked").asBoolean()).isTrue();
    ResponseEntity<JsonNode> login = lineLogin();
    assertThat(login.getBody().path("registered").asBoolean()).isTrue();
    assertThat(getMe(login.getBody().path("token").asString()).getBody().path("email").asString())
        .isEqualTo(email);
  }

  @Test
  @DisplayName("Bearer なしの連携要求は 401（匿名で他人のアカウントへ結び付けられない）")
  void linkWithoutBearerIsUnauthorized() {
    stubIdentity(uniqueSub("anon"), "匿名花子", uniqueEmail("line-anon"));

    ResponseEntity<JsonNode> res = linkLine(jsonHeaders());

    assertThat(res.getStatusCode().value()).isIn(401, 403);
  }

  @Test
  @DisplayName("他の身分が連携済みの LINE アカウントへの連携は 409 で、要求者は未連携のまま")
  void linkWithLineAccountBoundToAnotherUserIsConflict() {
    String ownerEmail = uniqueEmail("line-owner");
    String ownerToken = registerMemberAndLogin(ownerEmail, "先着花子");
    String sub = uniqueSub("taken");
    stubIdentity(sub, "LINE花子", uniqueEmail("line-taken"));
    assertThat(linkLine(bearer(ownerToken)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    String otherEmail = uniqueEmail("line-other-user");
    String otherToken = registerMemberAndLogin(otherEmail, "後着次郎");
    ResponseEntity<JsonNode> res = linkLine(bearer(otherToken));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(platformUserRepository.findByEmail(otherEmail).orElseThrow().getLineUserId())
        .isNull();
    assertThat(platformUserRepository.findByEmail(ownerEmail).orElseThrow().getLineUserId())
        .isEqualTo(sub);
  }

  @Test
  @DisplayName("連携済みアカウントの再連携は 409（付け替えを提供しない）")
  void relinkingAlreadyLinkedAccountIsConflict() {
    String email = uniqueEmail("line-relink");
    String token = registerMemberAndLogin(email, "再連携花子");
    String firstSub = uniqueSub("relink-first");
    stubIdentity(firstSub, "LINE花子", uniqueEmail("line-relink-1"));
    assertThat(linkLine(bearer(token)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    stubIdentity(uniqueSub("relink-second"), "LINE花子", uniqueEmail("line-relink-2"));
    ResponseEntity<JsonNode> res = linkLine(bearer(token));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(platformUserRepository.findByEmail(email).orElseThrow().getLineUserId())
        .isEqualTo(firstSub);
  }

  @Test
  @DisplayName("登録チケットは 1 度きり（2 度目の登録確定は 4xx で拒否され、身分を作らない）")
  void registrationTicketIsSingleUse() {
    String sub = uniqueSub("single-use");
    String firstEmail = uniqueEmail("line-ticket-1");
    String ticket = ticketForUnknownSub(sub, "LINE花子", firstEmail);
    assertThat(lineRegister(ticket, "会員花子", firstEmail).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    String secondEmail = uniqueEmail("line-ticket-2");
    ResponseEntity<JsonNode> res = lineRegister(ticket, "会員次郎", secondEmail);

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    assertThat(platformUserRepository.findByEmail(secondEmail)).isEmpty();
  }

  @Test
  @DisplayName("登録がメール重複の 409 でもチケットは残り、入力を直して同じチケットで登録し直せる")
  void registrationTicketSurvivesDuplicateEmailConflict() {
    String takenEmail = uniqueEmail("taken");
    registerMemberAndLogin(takenEmail, "既存会員");
    String sub = uniqueSub("retry");
    String ticket = ticketForUnknownSub(sub, "LINE次郎", uniqueEmail("line-retry"));

    ResponseEntity<JsonNode> conflict = lineRegister(ticket, "会員次郎", takenEmail);
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    String freshEmail = uniqueEmail("line-retry-ok");
    ResponseEntity<JsonNode> retried = lineRegister(ticket, "会員次郎", freshEmail);
    assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(platformUserRepository.findByEmail(freshEmail).orElseThrow().getLineUserId())
        .isEqualTo(sub);
  }

  @Test
  @DisplayName("未知の登録チケットは 4xx で拒否される")
  void unknownRegistrationTicketIsRejected() {
    String email = uniqueEmail("line-unknown-ticket");

    ResponseEntity<JsonNode> res = lineRegister("no-such-ticket", "会員花子", email);

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    assertThat(platformUserRepository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("陳腐な Bearer を付けたままでも LINE の公開端点（設定・ログイン・登録）は匿名で通ること")
  void publicLineEndpointsIgnoreBrokenBearer() {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth("stale-or-broken-token-value");

    ResponseEntity<JsonNode> config =
        rest.exchange(
            "/platform/line/config", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    assertThat(config.getStatusCode()).isEqualTo(HttpStatus.OK);

    String sub = uniqueSub("broken-bearer");
    String email = uniqueEmail("line-broken-bearer");
    stubIdentity(sub, "LINE花子", email);
    ResponseEntity<JsonNode> login =
        rest.exchange(
            "/platform/line/login",
            HttpMethod.POST,
            new HttpEntity<>(authorizationBody(), headers),
            JsonNode.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

    String body =
        String.format(
            "{\"registration_ticket\": \"%s\", \"display_name\": \"陳腐Bearer花子\", \"email\": \"%s\"}",
            login.getBody().path("registration_ticket").asString(), email);
    ResponseEntity<JsonNode> registered =
        rest.exchange(
            "/platform/line/register",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            JsonNode.class);
    assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(platformUserRepository.findByEmail(email)).isPresent();
  }
}
