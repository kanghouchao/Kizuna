package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.settings.api.dto.SystemConfigUpdateRequest;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestStatus;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * 日付変更時刻より前の深夜帯で、「現在日」を見る各面が揃って前営業日を指し続けることを本物の PostgreSQL で確かめる統合テスト。
 *
 * <p>時計を深夜 02:30 に固定し、日付変更時刻を 05:00 に設定する。この瞬間の営業日は前の暦日であり、暦日で判じる実装なら
 * 出勤表からは前営業日の出勤が消え、前営業日を指す申請・照会は「過去の日付」として撥ねられる — 片面だけの切替はこの帯で面ごとの不一致を生むので、四面を同じ断面で対にして固定する。
 *
 * <p>初期値 00:00 での既存挙動は本 IT の対象外で、{@link ShiftPublicationIT} など「実時刻の当日」で組まれた 既存テストが素通りすることが証拠になる。
 */
class BusinessDateIT extends CrossStoreTestSupport {

  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
  private static final LocalDateTime NIGHT = LocalDateTime.of(2026, 8, 18, 2, 30);
  private static final LocalDate CALENDAR_DATE = NIGHT.toLocalDate();
  private static final LocalDate BUSINESS_DATE = CALENDAR_DATE.minusDays(1);
  private static final String DATE_CHANGE_TIME_KEY = "business_date_change_time";
  private static final String CAST_EMAIL = "business-date-it-cast@kizuna.test";
  private static final String CAST_PASSWORD = "pass";
  private static final String MEMBER_PASSWORD = "password1234";

  /** 候補照会・利用日検証が共有する先読み上限。境界は営業日から数えるので、暦日で数える実装とはここでずれる。 */
  private static final int MAX_LOOKAHEAD_DAYS = 90;

  /** 深夜帯に固定した時計。営業日の判定だけがこの豆を読み、発行済みトークンの有効期限や行の created_at は 実時刻のままなので、固定しても土台のログインは通る。 */
  @TestConfiguration
  static class NightClockConfiguration {
    @Bean
    @Primary
    Clock nightClock() {
      return Clock.fixed(NIGHT.atZone(ZONE).toInstant(), ZONE);
    }
  }

  @Autowired private ShiftRepository shiftRepository;
  @Autowired private ShiftRequestRepository shiftRequestRepository;
  @Autowired private CastRepository castRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private SystemConfigService systemConfigService;

  private String previousBusinessDateCastId;
  private String calendarDateCastId;

  @BeforeEach
  void setDateChangeTimeAndSeedShifts() {
    setDateChangeTime("05:00");
    String suffix = UUID.randomUUID().toString();
    previousBusinessDateCastId = createCast("営業日IT_前営業日_" + suffix);
    calendarDateCastId = createCast("営業日IT_暦日_" + suffix);
    seedConfirmedShift(previousBusinessDateCastId, BUSINESS_DATE);
    seedConfirmedShift(calendarDateCastId, CALENDAR_DATE);
  }

  /** 日付変更時刻はプラットフォーム全体の設定なので、他の統合テストへ持ち越さないよう既定値へ戻す。 */
  @AfterEach
  void restoreDateChangeTime() {
    setDateChangeTime("00:00");
  }

  @Test
  @DisplayName("深夜帯の匿名出勤表が前営業日の出勤を返し続けること")
  void anonymousShiftTableStaysOnThePreviousBusinessDate() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/shifts/public",
            HttpMethod.GET,
            new HttpEntity<>(anonymousStoreContext()),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(castIdsOf(res.getBody()))
        .as("暦日ではなく営業日で引くこと（深夜帯に前営業日の出勤が消えない）")
        .contains(previousBusinessDateCastId)
        .doesNotContain(calendarDateCastId);
  }

  @Test
  @DisplayName("会員の指名候補の対象日検証が匿名出勤表と同じ営業日で揃うこと")
  void memberCandidateLookupSharesTheSameBusinessDate() {
    String memberToken = registerAndLoginAsMember();

    ResponseEntity<JsonNode> current = fetchCandidates(memberToken, BUSINESS_DATE);
    assertThat(current.getStatusCode()).as("現在の営業日は照会できること").isEqualTo(HttpStatus.OK);
    assertThat(castIdsOf(current.getBody())).contains(previousBusinessDateCastId);

    assertThat(fetchCandidates(memberToken, BUSINESS_DATE.minusDays(1)).getStatusCode())
        .as("現在の営業日より前は過去として撥ねること")
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 下限だけを見ると、上限が暦日基準のまま取り残されても気づけない（両端は同じ起点から数える）。
    assertThat(
            fetchCandidates(memberToken, BUSINESS_DATE.plusDays(MAX_LOOKAHEAD_DAYS))
                .getStatusCode())
        .as("先読み上限も営業日から数えること")
        .isEqualTo(HttpStatus.OK);
    assertThat(
            fetchCandidates(memberToken, BUSINESS_DATE.plusDays(MAX_LOOKAHEAD_DAYS + 1))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("会員経由の指名の書き込み検証が匿名出勤表と同じ営業日で揃うこと")
  void memberOrderRequestSharesTheSameBusinessDate() {
    String memberToken = registerAndLoginAsMember();

    ResponseEntity<JsonNode> accepted =
        requestReservation(memberToken, previousBusinessDateCastId, BUSINESS_DATE);
    assertThat(accepted.getStatusCode()).as("現在の営業日の出勤に対する指名は通ること").isEqualTo(HttpStatus.CREATED);

    assertThat(
            requestReservation(memberToken, previousBusinessDateCastId, BUSINESS_DATE.minusDays(1))
                .getStatusCode())
        .as("現在の営業日より前は過去として撥ねること")
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("キャストの出勤希望の対象日検証が匿名出勤表と同じ営業日で揃うこと")
  void castShiftRequestSharesTheSameBusinessDate() {
    String castToken = registerAndLoginAsCast();

    assertThat(submitShiftRequest(castToken, BUSINESS_DATE).getStatusCode())
        .as("現在の営業日はまだ希望を出せること")
        .isEqualTo(HttpStatus.CREATED);
    assertThat(submitShiftRequest(castToken, BUSINESS_DATE.minusDays(1)).getStatusCode())
        .as("現在の営業日より前は撥ねること")
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("目標営業日が終了した希望は承認できず、却下と一覧の承認可否がそれに揃うこと")
  void expiredRequestIsDeclinableButNotApprovable() {
    String expiredId = seedPendingRequest(BUSINESS_DATE.minusDays(1));
    String liveId = seedPendingRequest(BUSINESS_DATE);

    assertThat(approvableOf(liveId)).as("正向対照: 現在の営業日の希望は承認可能と導出されること").isTrue();
    assertThat(approvableOf(expiredId)).as("NEW 行にも承認可否が導出されること").isFalse();

    assertThat(approve(liveId).getStatusCode())
        .as("正向対照: 現在の営業日の希望は承認できること")
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> refused = approve(expiredId);
    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(refused.getBody().path("error").asString()).contains("営業日が終了した出勤希望は承認できません");

    ResponseEntity<JsonNode> declined =
        rest.exchange(
            "/store/shift-requests/" + expiredId + "/rejection",
            HttpMethod.POST,
            new HttpEntity<>("{}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(declined.getStatusCode()).as("却下は無期限に可能であること").isEqualTo(HttpStatus.OK);
    assertThat(declined.getBody().path("status").asString()).isEqualTo("DECLINED");
  }

  private void setDateChangeTime(String value) {
    systemConfigService.updateConfig(
        DATE_CHANGE_TIME_KEY, SystemConfigUpdateRequest.builder().configValue(value).build());
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

  /** 主題は対象日の判定なので、シフトは作成 API の検証を経由せずリポジトリ直挿しで置く。 */
  private void seedConfirmedShift(String castId, LocalDate workDate) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(workDate)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(ShiftStatus.CONFIRMED)
            .published(true)
            .build();
    shift.setStoreId(STORE_A);
    shiftRepository.save(shift);
  }

  /** 期限切れの希望は提出 API を通せないため（提出時にも同じ営業日で撥ねられる）、直挿しで置く。 */
  private String seedPendingRequest(LocalDate workDate) {
    ShiftRequest request =
        ShiftRequest.builder()
            .castId(previousBusinessDateCastId)
            .workDate(workDate)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .build();
    request.setStoreId(STORE_A);
    return shiftRequestRepository.save(request).getId();
  }

  private ResponseEntity<JsonNode> approve(String requestId) {
    return rest.exchange(
        "/store/shift-requests/" + requestId + "/approval",
        HttpMethod.POST,
        new HttpEntity<>("{}", storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private boolean approvableOf(String requestId) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/shift-requests?status=" + ShiftRequestStatus.PENDING,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (JsonNode row : res.getBody()) {
      if (requestId.equals(row.path("id").asString())) {
        return row.path("approvable").asBoolean();
      }
    }
    throw new AssertionError("前提: 直挿しした希望が inbox に現れること: " + requestId);
  }

  private ResponseEntity<JsonNode> fetchCandidates(String memberToken, LocalDate date) {
    return rest.exchange(
        "/platform/shifts/casts?store_id=" + STORE_A + "&date=" + date,
        HttpMethod.GET,
        new HttpEntity<>(bearer(memberToken)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> requestReservation(
      String memberToken, String castId, LocalDate businessDate) {
    return rest.postForEntity(
        "/platform/me/order-applications",
        new HttpEntity<>(
            "{\"store_id\": "
                + STORE_A
                + ", \"business_date\": \""
                + businessDate
                + "\", \"pax\": 2, \"declared_name\": \"営業日IT\", \"cast_id\": \""
                + castId
                + "\"}",
            bearer(memberToken)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> submitShiftRequest(String castToken, LocalDate workDate) {
    return rest.postForEntity(
        "/platform/me/shift-requests",
        new HttpEntity<>(
            "{\"store_id\": "
                + STORE_A
                + ", \"work_date\": \""
                + workDate
                + "\", \"start_time\": \"18:00:00\", \"end_time\": \"23:00:00\"}",
            bearer(castToken)),
        JsonNode.class);
  }

  /** 本人の cast 行を find-or-create する（@BeforeEach 毎の複製を避ける）。 */
  private String registerAndLoginAsCast() {
    PlatformUser castUser =
        platformUserRepository
            .findByEmail(CAST_EMAIL)
            .orElseGet(
                () ->
                    platformUserRepository.save(
                        PlatformUser.builder()
                            .email(CAST_EMAIL)
                            .password(passwordEncoder.encode(CAST_PASSWORD))
                            .displayName("営業日ITキャスト")
                            .enabled(true)
                            .userType(UserType.CAST)
                            .roleIds(Set.of())
                            .storeScopeType(StoreScopeType.ALL_STORES)
                            .storeIds(Set.of())
                            .build()));
    if (castRepository.findIdsByPlatformUserIdAndStoreId(castUser.getId(), STORE_A).isEmpty()) {
      Cast cast = Cast.builder().name("営業日IT本人").platformUserId(castUser.getId()).build();
      cast.setStoreId(STORE_A);
      castRepository.save(cast);
    }
    return login(CAST_EMAIL);
  }

  private String registerAndLoginAsMember() {
    String email = "business-date-it-" + System.nanoTime() + "@kizuna.test";
    HttpHeaders json = new HttpHeaders();
    json.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + MEMBER_PASSWORD
                    + "\", \"display_name\": \"営業日IT会員\"}",
                json),
            JsonNode.class);
    assertThat(registration.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> loggedIn =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + MEMBER_PASSWORD + "\"}", json),
            JsonNode.class);
    assertThat(loggedIn.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    return loggedIn.getBody().path("token").asString();
  }

  private static List<String> castIdsOf(JsonNode body) {
    List<String> ids = new ArrayList<>();
    for (JsonNode row : body) {
      ids.add(row.path("cast_id").asString());
    }
    return ids;
  }

  private static HttpHeaders anonymousStoreContext() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(STORE_A));
    return headers;
  }

  private static HttpHeaders bearer(String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
