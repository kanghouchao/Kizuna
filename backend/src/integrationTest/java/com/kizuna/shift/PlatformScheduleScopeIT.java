package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * 本人（キャスト）ポータル週間スケジュールの cast_id 単層自限を本物の PostgreSQL で強断言する統合テスト（issue #328）。
 *
 * <p>断言は「帰属不一致」型の弱断言ではなく、応答生ボディに授権外の実データ（別キャストの確定シフト・非所属店舗の確定シフト・TENTATIVE）が
 * 一切現れないこと（AC story 8）で行う。読みは cast_id 単層自限のみで濾過し、{@code storeSetFilter}/{@code storeFilter}
 * は経由しない。先例は {@link com.kizuna.order.PlatformOrderScopeIT}（リポジトリ直挿 + 実データ断言）と {@link
 * ShiftCrossStoreIT}（単店隔離）。
 */
class PlatformScheduleScopeIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";
  private static final String CAST_EMAIL = "schedule-cast@kizuna.test";

  /** v0.1.0 seed/05-demo.yaml の店舗スタッフ（ROLE_CAST を持たない）。役割線 403 の検証に使う。 */
  private static final String NON_CAST_STAFF_EMAIL = "yamada.jiro@kizuna.test";

  private static final String STORE_B_DOMAIN = "schedule-it-b.kizuna.test";
  private static final String STORE_C_DOMAIN = "schedule-it-c.kizuna.test";
  private static final String STORE_C_NAME = "週間集約IT非所属店舗機密";

  private static final LocalDate WORK_DATE = LocalDate.of(2999, 4, 3);
  private static final LocalDate FROM = LocalDate.of(2999, 3, 30);
  private static final LocalDate TO = LocalDate.of(2999, 4, 5);

  private static final String MY_A_START = "18:00:00";
  private static final String MY_A_END = "20:00:00";
  private static final String MY_B_START = "10:00:00";
  private static final String MY_B_END = "12:00:00";

  /** 別キャスト（本人と無関係）の確定シフトの開始時刻。応答の生ボディに現れてはならないカナリア。 */
  private static final String CANARY_FOREIGN_CAST_START = "23:57:00";

  /** 本人の TENTATIVE シフトの開始時刻。CONFIRMED のみ返す不変条件のカナリア。 */
  private static final String CANARY_TENTATIVE_START = "05:05:00";

  @Autowired private CastRepository castRepository;
  @Autowired private ShiftRepository shiftRepository;
  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private long storeBId;
  private long storeCId;

  @BeforeEach
  void prepareFixture() {
    storeBId = ensureStore(STORE_B_DOMAIN, "週間集約IT第二店舗");
    storeCId = ensureStore(STORE_C_DOMAIN, STORE_C_NAME);

    Long castUserId =
        ensurePlatformUser(CAST_EMAIL, UserType.CAST, StoreScopeType.ALL_STORES, Set.of())
            .getId();

    // 本人の cast 行を店 A・店 B の双方に作る（跨店集約の対象。cast_id 自限が同時に店舗自限として機能する所以）。
    String myCastA = createCast(STORE_A, "週間集約IT本人（店A）", castUserId);
    String myCastB = createCast(storeBId, "週間集約IT本人（店B）", castUserId);

    // 正向: 本人の確定シフト（跨店）。
    saveShift(myCastA, STORE_A, LocalTime.parse(MY_A_START), LocalTime.parse(MY_A_END), "CONFIRMED");
    saveShift(
        myCastB, storeBId, LocalTime.parse(MY_B_START), LocalTime.parse(MY_B_END), "CONFIRMED");

    // 負向1: 別キャスト（本人と紐づかない）の確定シフト。所属店 A 内でも cast_id が違えば見えないこと。
    String foreignCastInStoreA = createCast(STORE_A, "週間集約IT別キャスト", null);
    saveShift(
        foreignCastInStoreA,
        STORE_A,
        LocalTime.parse(CANARY_FOREIGN_CAST_START),
        LocalTime.parse(CANARY_FOREIGN_CAST_START).plusHours(1),
        "CONFIRMED");

    // 負向2: 非所属店舗（店 C）の別キャストの確定シフト。cast_id 単層自限は店舗を跨いで一切現れないこと。
    String foreignCastInStoreC = createCast(storeCId, "週間集約IT非所属店キャスト", null);
    saveShift(
        foreignCastInStoreC, storeCId, LocalTime.of(14, 0), LocalTime.of(16, 0), "CONFIRMED");

    // 負向3: 本人（店A）の TENTATIVE シフト。CONFIRMED のみ返す不変条件のカナリア。
    saveShift(
        myCastA,
        STORE_A,
        LocalTime.parse(CANARY_TENTATIVE_START),
        LocalTime.parse(CANARY_TENTATIVE_START).plusHours(1),
        "TENTATIVE");
  }

  private long ensureStore(String domain, String name) {
    return storeRepository
        .findByDomain(domain)
        .orElseGet(() -> storeRepository.save(new Store(name, domain, null)))
        .getId();
  }

  private PlatformUser ensurePlatformUser(
      String email, UserType userType, StoreScopeType scopeType, Set<Long> storeIds) {
    return platformUserRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(email)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("週間集約IT " + userType.name())
                        .enabled(true)
                        .userType(userType)
                        .bundleIds(Set.of())
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build()));
  }

  /** リポジトリ直挿（テストスレッドは @StoreScoped を経由せず storeFilter が無効なので他店舗にも書ける）。 */
  private String createCast(long storeId, String name, Long platformUserId) {
    Cast cast =
        Cast.builder().name(name).status("ACTIVE").platformUserId(platformUserId).build();
    cast.setStoreId(storeId);
    return castRepository.save(cast).getId();
  }

  private void saveShift(
      String castId, long storeId, LocalTime start, LocalTime end, String status) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(WORK_DATE)
            .startTime(start)
            .endTime(end)
            .status(status)
            .build();
    shift.setStoreId(storeId);
    shiftRepository.save(shift);
  }

  private String platformToken(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password),
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    String t = res.getBody().path("token").asString();
    assertThat(t).isNotBlank();
    return t;
  }

  private ResponseEntity<JsonNode> getSchedule(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return rest.exchange(
        "/platform/me/schedule?from=" + FROM + "&to=" + TO,
        HttpMethod.GET,
        new HttpEntity<>(headers),
        JsonNode.class);
  }

  private ResponseEntity<String> getScheduleRaw(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return rest.exchange(
        "/platform/me/schedule?from=" + FROM + "&to=" + TO,
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);
  }

  private boolean containsEntry(JsonNode body, long storeId, String startTime) {
    for (JsonNode node : body) {
      if (node.path("store_id").asLong() == storeId
          && startTime.equals(node.path("start_time").asString())) {
        return true;
      }
    }
    return false;
  }

  @Test
  @DisplayName("2 店所属キャストの確定シフトが週間で跨店集約されること(正向対照)")
  void myConfirmedShiftsAggregateAcrossStores() {
    ResponseEntity<JsonNode> res = getSchedule(platformToken(CAST_EMAIL, PASSWORD));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = res.getBody();
    assertThat(containsEntry(body, STORE_A, MY_A_START))
        .as("店 A の本人確定シフトが応答に含まれること")
        .isTrue();
    assertThat(containsEntry(body, storeBId, MY_B_START))
        .as("店 B の本人確定シフトが応答に含まれること")
        .isTrue();
    for (JsonNode node : body) {
      assertThat(node.path("status").asString()).as("応答は常に CONFIRMED であること").isEqualTo("CONFIRMED");
    }
  }

  @Test
  @DisplayName("別キャスト・非所属店舗・TENTATIVE の実データが応答の生ボディに一切現れないこと(AC story 8)")
  void outOfScopeRealDataNeverAppearsInResponse() {
    ResponseEntity<String> res = getScheduleRaw(platformToken(CAST_EMAIL, PASSWORD));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody())
        .as("授権外の実データ（別キャスト確定・非所属店舗確定・TENTATIVE）が生ボディに現れないこと")
        .doesNotContain(CANARY_FOREIGN_CAST_START)
        .doesNotContain(STORE_C_NAME)
        .doesNotContain(CANARY_TENTATIVE_START)
        .doesNotContain("TENTATIVE");
  }

  @Test
  @DisplayName("ROLE_CAST を持たないユーザーは /platform/me/schedule で 403(@PreAuthorize の役割線)")
  void nonCastRoleIsRejectedOnPlatformMeSchedule() {
    ResponseEntity<String> res = getScheduleRaw(platformToken(NON_CAST_STAFF_EMAIL, PASSWORD));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
