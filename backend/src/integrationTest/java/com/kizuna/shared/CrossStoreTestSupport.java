package com.kizuna.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.cast.domain.CastPublicationStatus;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.service.domain.ServiceRevisionRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * クロス店舗統合テストの共通土台。
 *
 * <p>シードユーザー yamada.jiro@kizuna.test/pass（STORE_STAFF・授権店舗 = 店舗1）で平台ログインして JWT を保持し、 認証 = JWT /
 * 店舗文脈 = X-Store-ID ヘッダという本番構造どおりのリクエストヘッダを組み立てる （Bearer ヘッダ付きリクエストは CSRF 免除）。
 *
 * <p>準金銭的な確定操作（ポイントの手動調整、誤帰属の訂正とその一段目の無効化）は店長限定のため、店員の身分では 403 になる。 それらを叩くテストは {@link
 * #managerHeaders(long)} を使う（ADR 0012）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public abstract class CrossStoreTestSupport {

  protected ResponseEntity<JsonNode> releaseMemberLink(String customerId, HttpHeaders headers) {
    String path = "/store/customers/" + customerId + "/member-link";
    var current = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    String expectedId =
        current.getStatusCode().is2xxSuccessful()
            ? current.getBody().path("id").asString()
            : "absent";
    return rest.exchange(
        path + "/releases",
        HttpMethod.POST,
        new HttpEntity<>(
            Map.of("expected_link_id", expectedId, "operation_reason", "本人依頼"), headers),
        JsonNode.class);
  }

  protected static final long STORE_A = 1L;
  protected static final long STORE_B = 2L;

  @Autowired protected TestRestTemplate rest;

  @Autowired protected CastProfileRepository fixtureProfiles;
  @Autowired private CastRepository fixturePeople;
  @Autowired private CastEnrollmentRepository fixtureEnrollments;

  protected Long personIdForUser(Long userId) {
    return fixturePeople
        .findByPlatformUserId(userId)
        .orElseGet(() -> fixturePeople.save(Cast.builder().platformUserId(userId).build()))
        .getId();
  }

  protected Long platformUserIdForEnrollment(String id) {
    Long personId = fixtureEnrollments.findById(id).orElseThrow().getCastId();
    return personId == null
        ? null
        : fixturePeople.findById(personId).orElseThrow().getPlatformUserId();
  }

  protected void publishCastFixture(String id, long storeId) {
    var response =
        rest.exchange(
            "/store/casts/" + id + "/publication",
            HttpMethod.PATCH,
            new HttpEntity<>("{\"publication_status\":\"PUBLISHED\"}", storeHeaders(storeId)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  protected CastEnrollment saveEnrollmentFixture(
      CastEnrollment enrollment, String name, Long userId) {
    if (userId != null) {
      var person =
          fixturePeople
              .findByPlatformUserId(userId)
              .orElseGet(() -> fixturePeople.save(Cast.builder().platformUserId(userId).build()));
      enrollment.linkCast(person.getId());
    }
    var saved = fixtureEnrollments.save(enrollment);
    var profile =
        CastProfile.builder()
            .enrollmentId(saved.getId())
            .name(name)
            .publicationStatus(CastPublicationStatus.PUBLISHED)
            .build();
    profile.setStoreId(saved.getStoreId());
    fixtureProfiles.save(profile);
    return saved;
  }

  protected String token;

  /** 店長（tanaka.hanako シード）の JWT。要るテストだけが払うよう、初めて使うときに取得する。 */
  private String managerToken;

  @BeforeEach
  void loginAsSeedUser() {
    token = login("yamada.jiro@kizuna.test");
  }

  protected HttpHeaders storeHeaders(long storeId) {
    return headersFor(storeId, token);
  }

  /**
   * 店長の身分での店舗文脈ヘッダ。{@code POINT_ADJUST} を要する端点にはこちらを使う。
   *
   * <p>demo シード（seed/05-demo.yaml）の田中花子（STORE_MANAGER・授権店舗 = 店舗1）。店員の {@link #storeHeaders(long)}
   * と使い分けること自体が、 権限の線がどこに引かれているかの記録になる。
   */
  protected HttpHeaders managerHeaders(long storeId) {
    if (managerToken == null) {
      managerToken = login("tanaka.hanako@kizuna.test");
    }
    return headersFor(storeId, managerToken);
  }

  /** 他業務の検証用に、実在するコースを選択・確認した要求を作る。 */
  protected HttpEntity<String> orderFixtureRequest(String body, HttpHeaders headers) {
    return orderFixtureRequest(body, headers, 100);
  }

  protected HttpEntity<String> orderFixtureRequest(String body, HttpHeaders headers, int price) {
    var course = courseFixture(Long.parseLong(headers.getFirst("X-Store-ID")), price);
    var input = (ObjectNode) fixtureJson.readTree(body);
    input.put("course_id", course.serviceId());
    return confirmedRequest("/store/orders/preview", input.toString(), headers);
  }

  @Autowired private ServiceRevisionRepository fixtureRevisions;

  protected OrderCourse courseFixture(long storeId, int price) {
    return courseFixture(storeId, "試験用コース", 60, price);
  }

  protected OrderCourse courseFixture(long storeId, String name, int minutes, int price) {
    var created =
        rest.postForEntity(
            "/store/services",
            new HttpEntity<>(
                Map.of(
                    "kind",
                    "COURSE",
                    "name",
                    name,
                    "duration_minutes",
                    minutes,
                    "price",
                    price,
                    "remuneration",
                    0),
                managerHeaders(storeId)),
            JsonNode.class);
    assertThat(created.getStatusCode())
        .as("前提: コース作成 %s", created.getBody())
        .isEqualTo(HttpStatus.CREATED);
    var terms =
        fixtureRevisions
            .findSelection(ServiceKind.COURSE, created.getBody().path("id").asString(), 1)
            .orElseThrow();
    return new OrderCourse(
        terms.serviceId(),
        terms.revisionId(),
        terms.revisionNumber(),
        terms.name(),
        terms.durationMinutes(),
        terms.price(),
        terms.remuneration(),
        "CURRENT_SETTING",
        OffsetDateTime.now());
  }

  protected HttpEntity<String> mergeFixtureRequest(
      String survivingId, String mergedId, HttpHeaders headers) {
    var input = fixtureJson.createObjectNode().put("merged_customer_id", mergedId);
    var preview =
        rest.postForEntity(
            "/store/customers/" + survivingId + "/merge-preview",
            new HttpEntity<>(input.toString(), headers),
            JsonNode.class);
    input.put("preview_token", preview.getBody().path("preview_token").asString("A".repeat(43)));
    if (preview.getBody().has("preferred_contacts"))
      input.set("preferred_contacts", preview.getBody().get("preferred_contacts"));
    else input.putObject("preferred_contacts").putNull("phone").putNull("email").putNull("line");
    input.put("warnings_acknowledged", true).put("operation_reason", "重複を確認したため");
    return new HttpEntity<>(input.toString(), headers);
  }

  @Autowired private ObjectMapper fixtureJson;

  protected HttpEntity<String> confirmedRequest(
      String previewPath, String body, HttpHeaders headers) {
    if (previewPath.equals("/store/orders/preview")) {
      var input = (ObjectNode) fixtureJson.readTree(body);
      if (!input.has("customer_selection"))
        input.putObject("customer_selection").put("mode", "NONE");
      body = input.toString();
    }
    var preview = rest.postForEntity(previewPath, new HttpEntity<>(body, headers), JsonNode.class);
    assertThat(preview.getStatusCode())
        .as("前提: 入力を試算できること %s", preview.getBody())
        .isEqualTo(HttpStatus.OK);
    var input = (ObjectNode) fixtureJson.readTree(body);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    return new HttpEntity<>(input.toString(), headers);
  }

  protected String completionFixtureBody(
      String orderId, int totalFee, Integer usePoints, HttpHeaders headers) {
    var detail =
        rest.exchange(
                "/store/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class)
            .getBody();
    int extra = totalFee - detail.path("course").path("price").asInt();
    var body = fixtureJson.createObjectNode();
    body.put("expected_version", detail.path("version").asLong());
    var line = body.putArray("fee_lines").addObject();
    line.put("kind", extra < 0 ? "DISCOUNT" : "CREDIT_SURCHARGE");
    line.put("name", "会計");
    line.put("amount", Math.abs(extra));
    if (usePoints != null) body.put("use_points", usePoints);
    return body.toString();
  }

  protected HttpEntity<String> completionFixtureRequest(
      String orderId, int totalFee, Integer usePoints, HttpHeaders headers) {
    return confirmedRequest(
        "/store/orders/" + orderId + "/completion-preview",
        completionFixtureBody(orderId, totalFee, usePoints, headers),
        headers);
  }

  /** 試算で拒否された入力は保存せず、確認できた要求だけを送る利用者の操作。 */
  protected ResponseEntity<JsonNode> submitPreviewed(
      String path, HttpMethod method, String previewPath, String body, HttpHeaders headers) {
    if (previewPath.equals("/store/orders/preview")) {
      var input = (ObjectNode) fixtureJson.readTree(body);
      if (!input.has("customer_selection"))
        input.putObject("customer_selection").put("mode", "NONE");
      body = input.toString();
    }
    var preview = rest.postForEntity(previewPath, new HttpEntity<>(body, headers), JsonNode.class);
    if (!preview.getStatusCode().is2xxSuccessful()) return preview;
    var input = (ObjectNode) fixtureJson.readTree(body);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    return rest.exchange(path, method, new HttpEntity<>(input.toString(), headers), JsonNode.class);
  }

  protected String withCourseFixture(String body, HttpHeaders headers) {
    var course = courseFixture(Long.parseLong(headers.getFirst("X-Store-ID")), 100);
    var input = (ObjectNode) fixtureJson.readTree(body);
    input.put("course_id", course.serviceId());
    return input.toString();
  }

  protected long orderVersion(HttpHeaders headers, String orderId) {
    return rest.exchange(
            "/store/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
        .getBody()
        .path("version")
        .asLong();
  }

  /** 新規作成の要求が通る合言葉。要求側の最小 8 文字を満たす必要があり、種子の合言葉は使えない。 */
  protected static final String NEW_ACCOUNT_PASSWORD = "pass1234";

  /** 種子ユーザーとして平台ログインする。実行者の身分そのものが主題のテストが使う（HQ 管理者など）。 */
  protected String login(String email) {
    return loginWithPassword(email, "pass");
  }

  /** テスト内で作成したアカウントとして平台ログインする。種子の合言葉を使えない場合に使う。 */
  protected String loginWithPassword(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: %s でのログインが成功すること", email).isEqualTo(HttpStatus.OK);
    String issued = res.getBody().path("token").asString();
    assertThat(issued).isNotBlank();
    return issued;
  }

  private static HttpHeaders headersFor(long storeId, String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
