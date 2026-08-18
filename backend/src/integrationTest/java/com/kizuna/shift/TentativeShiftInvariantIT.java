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
 * TENTATIVE（店舗内の下書き）が店外へ出ないことを、同一の実データで各露出面から確かめる統合テスト。
 *
 * <p>同じ店舗・同じ日に確定シフトと下書きシフトを 1 本ずつ置き、面ごとに「確定は現れる・下書きは現れない」を対で断言する。
 * 正向対照が無いと、経路が壊れて何も返らない状態でも負向断言だけは通る。
 *
 * <p>負向不変量 3 条のうち残る「変更申請の対象にならない」は提出時の拒否として {@link ShiftRequestScopeIT} が本人トークンの土台ごと持つ。
 */
class TentativeShiftInvariantIT extends CrossStoreTestSupport {

  /** 会員のパスワードは 8 文字以上が要る（基底の {@code login} が使う店舗側の短い種子パスワードは通らない）。 */
  private static final String PASSWORD = "password1234";

  @Autowired private ShiftRepository shiftRepository;
  @Autowired private ConfirmedShiftLookupService confirmedShiftLookupService;

  private LocalDate today;
  private String confirmedCastId;
  private String tentativeCastId;

  @BeforeEach
  void seedOneConfirmedAndOneTentativeShift() {
    today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
    String suffix = UUID.randomUUID().toString();
    confirmedCastId = createCast("下書き不変量_確定_" + suffix);
    tentativeCastId = createCast("下書き不変量_下書き_" + suffix);
    seedShift(confirmedCastId, LocalTime.of(18, 0), ShiftStatus.CONFIRMED);
    seedShift(tentativeCastId, LocalTime.of(12, 0), ShiftStatus.TENTATIVE);
  }

  @Test
  @DisplayName("公式サイトの出勤表に下書きシフトが現れないこと")
  void publicShiftTableHidesTentative() {
    HttpHeaders anonymousStoreContext = new HttpHeaders();
    anonymousStoreContext.set("X-Role", "store");
    anonymousStoreContext.set("X-Store-ID", String.valueOf(STORE_A));

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/shifts/public",
            HttpMethod.GET,
            new HttpEntity<>(anonymousStoreContext),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(castIdsOf(res.getBody()))
        .as("確定は公開され、下書きは公開されないこと")
        .contains(confirmedCastId)
        .doesNotContain(tentativeCastId);
  }

  @Test
  @DisplayName("会員の指名候補に下書きシフトのキャストが現れないこと")
  void memberCandidateListHidesTentative() {
    String memberToken = registerAndLoginAsMember();

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/shifts/casts?store_id=" + STORE_A + "&date=" + today,
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(castIdsOf(res.getBody()))
        .as("確定は候補に出て、下書きは出ないこと")
        .contains(confirmedCastId)
        .doesNotContain(tentativeCastId);
  }

  @Test
  @DisplayName("受注確定の内部検証が下書きシフトを確定シフトとして数えないこと")
  void hasConfirmedShiftDoesNotCountTentative() {
    assertThat(confirmedShiftLookupService.hasConfirmedShift(STORE_A, confirmedCastId, today))
        .as("正向対照: 確定シフトは数えられること")
        .isTrue();
    assertThat(confirmedShiftLookupService.hasConfirmedShift(STORE_A, tentativeCastId, today))
        .as("下書きシフトは数えないこと")
        .isFalse();
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
  private void seedShift(String castId, LocalTime startTime, ShiftStatus status) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(today)
            .startTime(startTime)
            .endTime(startTime.plusHours(2))
            .status(status)
            .build();
    shift.setStoreId(STORE_A);
    shiftRepository.save(shift);
  }

  private String registerAndLoginAsMember() {
    String email = "tentative-invariant-it-" + System.nanoTime() + "@kizuna.test";
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
                    + "\", \"display_name\": \"下書き不変量IT会員\"}",
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

  private static HttpHeaders bearer(String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
