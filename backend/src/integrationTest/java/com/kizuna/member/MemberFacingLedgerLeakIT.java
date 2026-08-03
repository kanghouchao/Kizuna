package com.kizuna.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
 * 会員側の応答に店舗顧客台帳の内部項目が一切乗らないことを本物の PostgreSQL で検証する統合テスト。
 *
 * <p>紐づけは店舗が会員を自店舗の台帳に結び付ける操作であって、会員に台帳を開くものではない。ランク・区分・NG・ポイント・ 連絡先などの内部評価は店舗の内部情報であり、本人であっても
 * 会員側の経路からは到達できてはならない。
 *
 * <p>断言は 2 段。生ボディに対しては、実データそのもの（カナリア文字列）と<b>引用符付きの項目名</b>（{@code "rank"} 等）の 非混入を見る — 応答には JWT
 * が含まれ、base64url の字母表に {@code "} は無いため、引用符付きなら token
 * 由来の偶然一致で赤くならない。加えて会員ホームは項目名の白名単反復で、想定外の項目が増えたら落ちるようにする。
 */
class MemberFacingLedgerLeakIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** 顧客台帳の内部項目に仕込む、他に一致しようがない実データ。 */
  private static final String CANARY_RANK = "CANARY-RANK-ecb1f0d4-8a2c";

  private static final String CANARY_CLASSIFICATION = "CANARY-CLASSIFICATION-ecb1f0d4-8a2c";
  private static final String CANARY_NG_TYPE = "CANARY-NGTYPE-ecb1f0d4-8a2c";
  private static final String CANARY_NG_CONTENT = "CANARY-NGCONTENT-ecb1f0d4-8a2c";
  private static final String CANARY_PHONE = "CANARY-PHONE-ecb1f0d4-8a2c";
  private static final String CANARY_LINE_ID = "CANARY-LINEID-ecb1f0d4-8a2c";
  private static final String CANARY_ADDRESS = "CANARY-ADDRESS-ecb1f0d4-8a2c";
  private static final String CANARY_USAGE_AREAS = "CANARY-USAGEAREAS-ecb1f0d4-8a2c";
  private static final int CANARY_POINTS = 987654321;

  private static final List<String> CANARY_VALUES =
      List.of(
          CANARY_RANK,
          CANARY_CLASSIFICATION,
          CANARY_NG_TYPE,
          CANARY_NG_CONTENT,
          CANARY_PHONE,
          CANARY_LINE_ID,
          CANARY_ADDRESS,
          CANARY_USAGE_AREAS);

  /** 台帳側の項目名。引用符付きで見ることで JWT の base64url 本体との偶然一致を排除する。 */
  private static final List<String> LEDGER_FIELD_NAMES =
      List.of(
          "\"rank\"",
          "\"classification\"",
          "\"ng_type\"",
          "\"ng_content\"",
          "\"points\"",
          "\"phone_number\"",
          "\"phone_number2\"",
          "\"line_id\"",
          "\"address\"",
          "\"building_name\"",
          "\"usage_areas\"",
          "\"has_pet\"",
          "\"member_linked\"",
          "\"linked_member_code\"");

  /** 会員ホームが返してよい項目名。増えたら落ちる。 */
  private static final List<String> MEMBER_HOME_ALLOWED_FIELDS =
      List.of("member_code", "display_name");

  /** 会員の予約 1 件が返してよい項目名。増えたら落ちる。 */
  private static final List<String> MEMBER_ORDER_ALLOWED_FIELDS =
      List.of(
          "id",
          "store_id",
          "store_name",
          "business_date",
          "arrival_scheduled_start_time",
          "pax",
          "cast_name",
          "status");

  @Autowired private CustomerRepository customerRepository;

  private String memberEmail;
  private String memberCode;
  private String memberToken;
  private String registrationBody;
  private String loginBody;

  @BeforeEach
  void seedLinkedLedgerCustomer() {
    // storeFilter を経由しない直挿しで店舗1 の顧客を用意する（points は更新 API に項目が無いため）。
    // store_id を明示すると StoreScopeStampListener は採番せず尊重する。
    Customer customer =
        Customer.builder()
            .name("台帳漏洩検証顧客")
            .phoneNumber(CANARY_PHONE)
            .phoneNumber2(CANARY_PHONE)
            .address(CANARY_ADDRESS)
            .buildingName(CANARY_ADDRESS)
            .classification(CANARY_CLASSIFICATION)
            .hasPet(true)
            .points(CANARY_POINTS)
            .rank(CANARY_RANK)
            .lineId(CANARY_LINE_ID)
            .usageAreas(CANARY_USAGE_AREAS)
            .ngType(CANARY_NG_TYPE)
            .ngContent(CANARY_NG_CONTENT)
            .build();
    customer.setStoreId(STORE_A);
    String customerId = customerRepository.save(customer).getId();

    memberEmail = "ledger-leak-it-" + System.nanoTime() + "@kizuna.test";
    ResponseEntity<String> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + memberEmail
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"台帳漏洩検証会員\"}",
                jsonHeaders()),
            String.class);
    assertThat(registration.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    registrationBody = registration.getBody();
    memberCode = readMemberCode(registrationBody);

    // 店舗スタッフ（基底クラスの yamada、店舗1 授権）が台帳へ紐づける
    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>("{\"member_code\": \"" + memberCode + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 紐づけが成功すること").isEqualTo(HttpStatus.OK);

    // ログインは紐づけの後に行う。紐づけ前の応答への断言は紐づけ由来の混入を検出しようがないため、
    // 断言対象は必ず紐づけ済み状態で取得する（登録応答だけは会員作成そのものなので紐づけ前が本質）。
    ResponseEntity<String> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + memberEmail + "\", \"password\": \"" + PASSWORD + "\"}",
                jsonHeaders()),
            String.class);
    assertThat(login.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    loginBody = login.getBody();
    memberToken = readToken(loginBody);
  }

  @Test
  @DisplayName("紐づけ後も会員側の全端点の生ボディに台帳の実データと項目名が一切現れないこと")
  void memberFacingEndpointsNeverExposeLedgerFields() {
    // 正向対照: 台帳の実データは店舗側からは確かに読める（断言対象が「漏れうるデータ」であることの証明）
    ResponseEntity<String> storeList =
        rest.exchange(
            "/store/customers?search=" + CANARY_PHONE,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            String.class);
    assertThat(storeList.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storeList.getBody()).contains(CANARY_RANK).contains(CANARY_NG_CONTENT);

    ResponseEntity<String> me =
        rest.exchange(
            "/platform/me", HttpMethod.GET, new HttpEntity<>(bearer(memberToken)), String.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> home =
        rest.exchange(
            "/platform/me/member",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            String.class);
    assertThat(home.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 予約申請は紐づけ済み顧客に結び付くため、台帳側の項目が会員側の一覧へ回り込む余地が最も大きい経路になる。
    requestReservation();
    ResponseEntity<String> reservations =
        rest.exchange(
            "/platform/me/orders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            String.class);
    assertThat(reservations.getStatusCode()).isEqualTo(HttpStatus.OK);

    assertNoLedgerLeak("会員登録", registrationBody);
    assertNoLedgerLeak("平台ログイン", loginBody);
    assertNoLedgerLeak("GET /platform/me", me.getBody());
    assertNoLedgerLeak("GET /platform/me/member", home.getBody());
    assertNoLedgerLeak("GET /platform/me/orders", reservations.getBody());
  }

  @Test
  @DisplayName("会員の予約一覧は申請内容と状態だけを返すこと（項目名の白名単）")
  void memberReservationReturnsOnlyWhitelistedFields() {
    requestReservation();

    ResponseEntity<JsonNode> reservations =
        rest.exchange(
            "/platform/me/orders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            JsonNode.class);

    assertThat(reservations.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode first = reservations.getBody().path("content").path(0);
    assertThat(first.isObject()).as("前提: 申請した予約が一覧に現れること").isTrue();
    List<String> fields = new ArrayList<>();
    first.propertyNames().forEach(fields::add);
    // 値が null の項目は non_null 包含設定で応答から消えるため、白名単は「これ以外が現れないこと」で見る。
    assertThat(fields)
        .as("会員の予約 1 件の応答項目（想定外の項目が増えていないこと）")
        .isSubsetOf(MEMBER_ORDER_ALLOWED_FIELDS);
  }

  /** 紐づけ済み店舗へ会員本人として予約を申請する（指名なし）。 */
  private void requestReservation() {
    HttpHeaders headers = bearer(memberToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> requested =
        rest.postForEntity(
            "/platform/me/orders",
            new HttpEntity<>(
                "{\"store_id\": "
                    + STORE_A
                    + ", \"business_date\": \""
                    + LocalDate.now()
                    + "\", \"pax\": 2}",
                headers),
            JsonNode.class);
    assertThat(requested.getStatusCode()).as("前提: 会員が予約を申請できること").isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("会員ホームは会員コードと表示名だけを返すこと（項目名の白名単）")
  void memberHomeReturnsOnlyWhitelistedFields() {
    ResponseEntity<JsonNode> home =
        rest.exchange(
            "/platform/me/member",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            JsonNode.class);

    assertThat(home.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> fields = new ArrayList<>();
    home.getBody().propertyNames().forEach(fields::add);
    assertThat(fields)
        .as("会員ホームの応答項目（想定外の項目が増えていないこと）")
        .containsExactlyInAnyOrderElementsOf(MEMBER_HOME_ALLOWED_FIELDS);
    assertThat(home.getBody().path("member_code").asString()).isEqualTo(memberCode);
  }

  private static void assertNoLedgerLeak(String endpoint, String body) {
    assertThat(body).as("%s の応答本文", endpoint).isNotBlank();
    for (String canary : CANARY_VALUES) {
      assertThat(body).as("%s に台帳の実データ %s が現れないこと", endpoint, canary).doesNotContain(canary);
    }
    assertThat(body)
        .as("%s に台帳のポイントが現れないこと", endpoint)
        .doesNotContain(String.valueOf(CANARY_POINTS));
    for (String fieldName : LEDGER_FIELD_NAMES) {
      // 引用符付きで見る: JWT の base64url 本体には " が現れないため偶然一致で赤くならない
      assertThat(body).as("%s に台帳の項目名 %s が現れないこと", endpoint, fieldName).doesNotContain(fieldName);
    }
  }

  private static String readMemberCode(String body) {
    String code = body.replaceAll("(?s).*\"member_code\"\\s*:\\s*\"([0-9]{12})\".*", "$1");
    assertThat(code).as("前提: 登録応答から会員コードを読めること").matches("\\d{12}");
    return code;
  }

  private static String readToken(String body) {
    String value = body.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    assertThat(value).as("前提: ログイン応答からトークンを読めること").isNotBlank();
    return value;
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
