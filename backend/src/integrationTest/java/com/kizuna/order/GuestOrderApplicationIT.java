package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 公開店面からのゲスト予約申請（匿名 POST）の統合テスト。公開端点の四点セットのうち、正常系では緑のまま隠れる 「壊れた Bearer 付きの匿名リクエスト」と「店舗文脈なしの
 * fail-closed」をここで固定する。
 *
 * <p>店舗の定位はヘッダ（{@code X-Role} / {@code X-Store-ID}）だけで、本番では店面 middleware が訪問された域名から解決して載せる。
 */
class GuestOrderApplicationIT extends CrossStoreTestSupport {

  private static final String PATH = "/store/order-applications/public";

  /** 匿名の来訪者。店舗文脈だけを名乗り、Authorization を持たない。 */
  private static HttpHeaders anonymousStoreHeaders(long storeId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    return headers;
  }

  /** 前端が付けうる陳腐な token cookie を模した、解読不能な Authorization ヘッダ。 */
  private static HttpHeaders storeHeadersWithBrokenBearer(long storeId) {
    HttpHeaders headers = anonymousStoreHeaders(storeId);
    headers.setBearerAuth("stale-or-broken-token-value");
    return headers;
  }

  /** 流量制限の窓は「店舗 × 発信元」で数えるため、テストごとに別の連絡先を使っても窓は共有される点に注意。 */
  private static String guestBody(String contactName) {
    return "{\"business_date\": \""
        + LocalDate.now().plusDays(1)
        + "\", \"pax\": 2, \"contact_name\": \""
        + contactName
        + "\", \"contact_phone_number\": \"09000000000\"}";
  }

  private ResponseEntity<JsonNode> post(HttpHeaders headers, String body) {
    return rest.postForEntity(PATH, new HttpEntity<>(body, headers), JsonNode.class);
  }

  @Test
  @DisplayName("陳腐な Bearer を付けたままでもゲスト予約申請が匿名で通ること")
  void guestRequestWithBrokenBearerStillSucceedsAnonymously() {
    // 公開店面の来訪者は会員・キャストの陳腐な token cookie を持ちうる。Bearer 免除が欠けると
    // @PermitAll の判定に届く前に 401 で弾かれ、申請そのものが成立しなくなる
    ResponseEntity<JsonNode> res =
        post(storeHeadersWithBrokenBearer(STORE_A), guestBody("壊れBearer" + UUID.randomUUID()));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(res.getBody().path("id").asString()).isNotBlank();
  }

  @Test
  @DisplayName("完全匿名（CSRF トークン無し）のゲスト予約申請が通ること")
  void fullyAnonymousGuestRequestSucceeds() {
    // 匿名 POST は「Bearer 付き」の一括 CSRF 免除に当たらないため、個別免除が無いと 403 になる
    ResponseEntity<JsonNode> res =
        post(anonymousStoreHeaders(STORE_A), guestBody("匿名" + UUID.randomUUID()));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("店舗文脈の無いゲスト予約申請が 403 で拒否されること")
  void guestRequestWithoutStoreContextIsRefused() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    assertThat(post(headers, guestBody("文脈無し")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("連絡先を欠いたゲスト予約申請が 400 で撥ねられること")
  void guestRequestWithoutContactIsRejected() {
    String body = "{\"business_date\": \"" + LocalDate.now().plusDays(1) + "\", \"pax\": 2}";

    assertThat(post(anonymousStoreHeaders(STORE_A), body).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("過去日のゲスト予約申請が拒否されること（匿名の入口が会員の入口より緩くならないこと）")
  void guestRequestForAPastDateIsRejected() {
    String body =
        "{\"business_date\": \""
            + LocalDate.now().minusDays(1)
            + "\", \"pax\": 2, \"contact_name\": \"過去日\", \"contact_phone_number\": \"09000000000\"}";

    assertThat(post(anonymousStoreHeaders(STORE_A), body).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("窓の上限を超えたゲスト予約申請が 429 で撥ねられること")
  void guestRequestsOverTheRateLimitAreRefused() {
    // 匿名 POST の灌水に対する唯一の兜底。窓は「店舗 × 発信元」で数えるので、別店舗（STORE_B）へ
    // 送って窓を汚さない
    HttpHeaders headers = anonymousStoreHeaders(STORE_B);
    HttpStatus last = null;
    for (int i = 0; i < 8; i++) {
      last = (HttpStatus) post(headers, guestBody("連投" + i)).getStatusCode();
      if (last == HttpStatus.TOO_MANY_REQUESTS) {
        break;
      }
    }

    assertThat(last).as("連投が上限で撥ねられること").isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  @DisplayName("ゲスト申請の確定が受付経路 GUEST_WEB の受注を生むこと")
  void confirmingAGuestApplicationRecordsGuestWeb() {
    ResponseEntity<JsonNode> created =
        post(anonymousStoreHeaders(STORE_A), guestBody("確定対象" + UUID.randomUUID()));
    assertThat(created.getStatusCode()).as("前提: ゲスト申請が受理されること").isEqualTo(HttpStatus.CREATED);
    String applicationId = created.getBody().path("id").asString();

    ResponseEntity<JsonNode> confirmed =
        rest.postForEntity(
            "/store/order-applications/" + applicationId + "/confirmation",
            new HttpEntity<>(
                "{\"business_date\": \"" + LocalDate.now().plusDays(1) + "\", \"pax\": 2}",
                storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(confirmed.getBody().path("reception_route").asString()).isEqualTo("GUEST_WEB");
  }
}
