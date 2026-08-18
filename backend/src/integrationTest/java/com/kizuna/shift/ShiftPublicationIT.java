package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 * 公開可否（店外への露出可否）が、同一の実データで各面からどう効くかを確かめる統合テスト。
 *
 * <p>同じ店舗・同じ日に公開可と非公開の確定シフトを 1 本ずつ置き、面ごとに「公開可は現れる・非公開は現れない」を対で断言する。
 * 正向対照が無いと、経路が壊れて何も返らない状態でも負向断言だけは通る。
 *
 * <p>絞る面（匿名出勤表・会員の指名候補・会員経由の指名の書き込み検証）と、絞らない面（受注確定の内部検証・店舗側管理画面）の 両方をここで固定する — 後者は ADR 0015
 * の負向不変量で、これが崩れると非公開が状態機械へ漏れ出す。
 */
class ShiftPublicationIT extends CrossStoreTestSupport {

  /** 会員のパスワードは 8 文字以上が要る（基底の {@code login} が使う店舗側の短い種子パスワードは通らない）。 */
  private static final String PASSWORD = "password1234";

  @Autowired private ShiftRepository shiftRepository;
  @Autowired private ConfirmedShiftLookupService confirmedShiftLookupService;

  private LocalDate today;
  private String publishedCastId;
  private String unpublishedCastId;
  private String unpublishedShiftId;

  @BeforeEach
  void seedOnePublishedAndOneUnpublishedShift() {
    today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
    String suffix = UUID.randomUUID().toString();
    publishedCastId = createCast("公開可否_公開_" + suffix);
    unpublishedCastId = createCast("公開可否_非公開_" + suffix);
    seedShift(publishedCastId, LocalTime.of(18, 0), ShiftStatus.CONFIRMED, true);
    unpublishedShiftId =
        seedShift(unpublishedCastId, LocalTime.of(12, 0), ShiftStatus.CONFIRMED, false);
  }

  @Test
  @DisplayName("公式サイトの出勤表に非公開の確定シフトが現れないこと")
  void publicShiftTableHidesUnpublished() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/shifts/public",
            HttpMethod.GET,
            new HttpEntity<>(anonymousStoreContext()),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(castIdsOf(res.getBody()))
        .as("公開可は出勤表に載り、非公開は載らないこと")
        .contains(publishedCastId)
        .doesNotContain(unpublishedCastId);
  }

  @Test
  @DisplayName("会員の指名候補に非公開の確定シフトのキャストが現れないこと")
  void memberCandidateListHidesUnpublished() {
    String memberToken = registerAndLoginAsMember();

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/shifts/casts?store_id=" + STORE_A + "&date=" + today,
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(castIdsOf(res.getBody()))
        .as("公開可は候補に出て、非公開は出ないこと")
        .contains(publishedCastId)
        .doesNotContain(unpublishedCastId);
  }

  @Test
  @DisplayName("候補から隠れているキャストは cast_id を直送しても指名できず、応答が出勤なしと区別できないこと")
  void memberNominationRejectsUnpublishedCastIdSentDirectly() {
    String memberToken = registerAndLoginAsMember();
    String castWithNoShift = createCast("公開可否_出勤なし_" + UUID.randomUUID());

    ResponseEntity<JsonNode> accepted = requestReservation(memberToken, publishedCastId);
    assertThat(accepted.getStatusCode()).as("正向対照: 公開可のキャストは指名できること").isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> hidden = requestReservation(memberToken, unpublishedCastId);
    ResponseEntity<JsonNode> absent = requestReservation(memberToken, castWithNoShift);

    assertThat(hidden.getStatusCode())
        .as("非公開のキャストは直送でも指名できないこと")
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(hidden.getBody().path("error").asString())
        .as("非公開を理由に文言を分けると、隠したシフトの存在が会員に読み取れてしまう")
        .isEqualTo(absent.getBody().path("error").asString());
  }

  @Test
  @DisplayName("受注確定の内部検証が公開可否を見ないこと")
  void hasConfirmedShiftIgnoresPublication() {
    assertThat(confirmedShiftLookupService.hasConfirmedShift(STORE_A, publishedCastId, today))
        .as("正向対照: 公開可の確定シフトは数えられること")
        .isTrue();
    assertThat(confirmedShiftLookupService.hasConfirmedShift(STORE_A, unpublishedCastId, today))
        .as("非公開でも店舗の確定操作からは確定シフトとして見えること")
        .isTrue();
  }

  @Test
  @DisplayName("店舗側の管理画面が公開可否で行を絞らず、値は応答に載せること")
  void storeSideListShowsBothAndCarriesTheFlag() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/shifts?from=" + today + "&to=" + today,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(castIdsOf(res.getBody()))
        .as("店舗側は公開可否に関わらず両方見えること")
        .contains(publishedCastId, unpublishedCastId);
    assertThat(publishedFlagOf(res.getBody(), unpublishedCastId))
        .as("操作面が使うため値そのものは載せること")
        .isFalse();
    assertThat(publishedFlagOf(res.getBody(), publishedCastId)).isTrue();
  }

  @Test
  @DisplayName("TENTATIVE は公開可でも店外へ出ないこと")
  void tentativeStaysHiddenEvenWhenPublished() {
    String tentativeCastId = createCast("公開可否_下書き公開可_" + UUID.randomUUID());
    seedShift(tentativeCastId, LocalTime.of(15, 0), ShiftStatus.TENTATIVE, true);

    assertThat(anonymousCastIds())
        .as("露出関門は CONFIRMED ∧ 公開可であって、フラグ単独では通らないこと")
        .contains(publishedCastId)
        .doesNotContain(tentativeCastId);

    // 「店外」は匿名と会員の両方。片面だけ絞ると軸が形骸化するので、候補面も同じ関門で見る。
    ResponseEntity<JsonNode> candidates =
        rest.exchange(
            "/platform/shifts/casts?store_id=" + STORE_A + "&date=" + today,
            HttpMethod.GET,
            new HttpEntity<>(bearer(registerAndLoginAsMember())),
            JsonNode.class);
    assertThat(candidates.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(castIdsOf(candidates.getBody()))
        .as("会員の候補面でもフラグ単独では通らないこと")
        .contains(publishedCastId)
        .doesNotContain(tentativeCastId);
  }

  @Test
  @DisplayName("切替が承認と独立に行え、非公開へ倒した行がその場で出勤表から消えること")
  void toggleTakesEffectWithoutTouchingApproval() {
    ResponseEntity<JsonNode> hidden =
        rest.exchange(
            "/store/shifts/" + shiftIdOf(publishedCastId) + "/publication",
            HttpMethod.PUT,
            new HttpEntity<>("{\"published\": false}", storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(hidden.getBody().path("published").asBoolean()).isFalse();
    assertThat(hidden.getBody().path("status").asString())
        .as("切替は状態機械を動かさないこと")
        .isEqualTo("CONFIRMED");
    assertThat(anonymousCastIds()).doesNotContain(publishedCastId);

    ResponseEntity<JsonNode> shown =
        rest.exchange(
            "/store/shifts/" + unpublishedShiftId + "/publication",
            HttpMethod.PUT,
            new HttpEntity<>("{\"published\": true}", storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(shown.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(anonymousCastIds()).as("公開可へ戻せば同じ行がまた載ること").contains(unpublishedCastId);
  }

  @Test
  @DisplayName("直接作成が非公開を同一トランザクションで指定でき、指定なしは公開可で出生すること")
  void directCreationCanBearUnpublished() {
    String castId = createCast("公開可否_直接作成_" + UUID.randomUUID());

    ResponseEntity<JsonNode> unpublished = createShift(castId, LocalTime.of(9, 0), false);
    assertThat(unpublished.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(unpublished.getBody().path("published").asBoolean()).isFalse();

    ResponseEntity<JsonNode> defaulted = createShift(castId, LocalTime.of(10, 0), null);
    assertThat(defaulted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(defaulted.getBody().path("published").asBoolean()).as("指定なしは公開可で出生すること").isTrue();
  }

  private String createCast(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: キャスト作成が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }

  /** 露出面の絞りだけを主題にするため、シフトはリポジトリ直挿しで置く（作成 API の既定や検証を経由しない）。 */
  private String seedShift(
      String castId, LocalTime startTime, ShiftStatus status, boolean published) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(today)
            .startTime(startTime)
            .endTime(startTime.plusHours(2))
            .status(status)
            .published(published)
            .build();
    shift.setStoreId(STORE_A);
    return shiftRepository.save(shift).getId();
  }

  private ResponseEntity<JsonNode> createShift(
      String castId, LocalTime startTime, Boolean published) {
    String publication = published == null ? "" : ", \"published\": " + published;
    return rest.postForEntity(
        "/store/shifts",
        new HttpEntity<>(
            "{\"cast_id\": \""
                + castId
                + "\", \"work_date\": \""
                + today
                + "\", \"start_time\": \""
                + startTime
                + ":00\", \"end_time\": \""
                + startTime.plusHours(2)
                + ":00\", \"status\": \"CONFIRMED\""
                + publication
                + "}",
            storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> requestReservation(String memberToken, String castId) {
    return rest.postForEntity(
        "/platform/me/orders",
        new HttpEntity<>(
            "{\"store_id\": "
                + STORE_A
                + ", \"business_date\": \""
                + today
                + "\", \"pax\": 2, \"declared_name\": \"公開可否IT\", \"cast_id\": \""
                + castId
                + "\"}",
            bearer(memberToken)),
        JsonNode.class);
  }

  private String shiftIdOf(String castId) {
    return shiftRepository.findByWorkDateBetween(today, today).stream()
        .filter(shift -> castId.equals(shift.getCastId()))
        .map(Shift::getId)
        .findFirst()
        .orElseThrow();
  }

  private List<String> anonymousCastIds() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/shifts/public",
            HttpMethod.GET,
            new HttpEntity<>(anonymousStoreContext()),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return castIdsOf(res.getBody());
  }

  private static HttpHeaders anonymousStoreContext() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(STORE_A));
    return headers;
  }

  private String registerAndLoginAsMember() {
    String email = "shift-publication-it-" + System.nanoTime() + "@kizuna.test";
    HttpHeaders json = new HttpHeaders();
    json.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"公開可否IT会員\"}",
                json),
            JsonNode.class);
    assertThat(registration.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}", json),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    String issued = login.getBody().path("token").asString();
    assertThat(issued).isNotBlank();
    return issued;
  }

  private static List<String> castIdsOf(JsonNode body) {
    List<String> ids = new ArrayList<>();
    for (JsonNode node : body) {
      ids.add(node.path("cast_id").asString());
    }
    return ids;
  }

  private static boolean publishedFlagOf(JsonNode body, String castId) {
    for (JsonNode node : body) {
      if (castId.equals(node.path("cast_id").asString())) {
        return node.path("published").asBoolean();
      }
    }
    throw new AssertionError("応答に該当キャストの行が無い: " + castId);
  }

  private static HttpHeaders bearer(String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
