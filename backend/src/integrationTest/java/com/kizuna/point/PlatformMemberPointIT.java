package com.kizuna.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.domain.PointAllocation;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
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
 * 会員本人のポイント読み口（残高・明細）を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>台帳は店舗で分割されない（ADR 0006）ため storeFilter が働かず、隔離は問い合わせに載せた会員 ID の一致だけが担う。 その一致が実際に効いていることは、他会員の仕訳を
 * 実データで仕込んで応答に現れないことで見る。
 *
 * <p>仕訳は台帳の工厂経由でそのまま積む。読み口は種別を区別しないので、付与・利用の生産経路（受注完了）は別の統合テストが 固定している事実に委ねる。
 */
class PlatformMemberPointIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** 明細の並びと帰属を一意に見分けるための増減。互いに部分文字列にならない値を選ぶ。 */
  private static final int CREDIT_WITH_EXPIRY = 121000;

  private static final int CREDIT_PLAIN = 342000;
  private static final int DEBIT_AT_OTHER_STORE = -53000;
  private static final int EXPIRED = 71000;
  private static final int CANCELLED = 100000;
  private static final int CLEARED = 50000;

  /** 他会員の台帳へ仕込む増減。本人の応答のどこにも現れてはならない。 */
  private static final int OTHER_MEMBER_CREDIT = 918273645;

  private static final LocalDate EXPIRY = LocalDate.of(2099, 12, 31);

  @Autowired private MemberRepository memberRepository;
  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private StoreRepository storeRepository;

  private long memberId;
  private String memberToken;
  private String storeAName;
  private String storeBName;

  @BeforeEach
  void registerMemberAndReadStoreNames() {
    RegisteredMember member = registerAndLogin();
    memberId = member.id();
    memberToken = member.token();
    storeAName = storeRepository.findById(STORE_A).map(Store::getName).orElseThrow();
    storeBName = storeRepository.findById(STORE_B).map(Store::getName).orElseThrow();
  }

  @Test
  @DisplayName("残高と、種別を問わない明細を新しい順に返すこと（店舗横断・発生店舗なしも欠落しない）")
  void returnsBalanceAndEveryEntryTypeNewestFirst() {
    seedLedger();

    ResponseEntity<JsonNode> balance = get("/platform/me/points/balance");
    assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 残高は「期限内ロットの残り」の合計。失効は仕訳を積むだけでなくロットの残りも減らす。
    assertThat(balance.getBody().path("balance").asLong())
        .isEqualTo((long) CREDIT_WITH_EXPIRY + CREDIT_PLAIN + DEBIT_AT_OTHER_STORE - EXPIRED);

    ResponseEntity<JsonNode> entries = get("/platform/me/points/entries");
    assertThat(entries.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode content = entries.getBody().path("content");

    assertThat(content).hasSize(4);
    // A 店で獲得した仕訳と B 店で減った仕訳が同じ一本の明細に並ぶ（ポイントは店舗横断で共通）。
    assertThat(rowsOf(content))
        .containsExactly(
            // 失効は複数ロットに跨る系統イベントなので発生店舗を持たない。行そのものは落ちない。
            List.of("EXPIRE", String.valueOf(-EXPIRED), "", ""),
            List.of("MANUAL_ADJUST", String.valueOf(DEBIT_AT_OTHER_STORE), storeBName, ""),
            List.of("MANUAL_ADJUST", String.valueOf(CREDIT_PLAIN), storeAName, ""),
            List.of(
                "MANUAL_ADJUST",
                String.valueOf(CREDIT_WITH_EXPIRY),
                storeAName,
                EXPIRY.toString()));
    assertThat(content.path(0).path("occurred_on").asString())
        .as("記帳日は業務のタイムゾーンで畳んだ日付")
        .matches("\\d{4}-\\d{2}-\\d{2}");
    // 引き当て・理由・実行者といった台帳内部の事情が現れないことは、項目名の白名単で見る
    // （MemberFacingLedgerLeakIT）。ここで内部 ID の非混入を生ボディの部分文字列で見ると、
    // 1 桁の ID が増減の中に偶然現れて赤くなる。
  }

  @Test
  @DisplayName("受注を要さない種別（調整・取消・失効・退会消去）がすべて明細に並ぶこと")
  void listsEveryEntryTypeThatDoesNotRequireAnOrder() {
    // 付与（ORDER_GRANT）と利用（USE）は受注 ID を必須とし、その生産経路は OrderCompletionIT が固定して
    // いる。読み口は種別で絞らない（問い合わせに entry_type の条件が無い）ので、ここで通した 4 種と
    // 同じ経路をその 2 種も通る。
    long creditId = seedCredit(CREDIT_PLAIN);
    PointEntry credit = pointEntryRepository.findById(creditId).orElseThrow();
    pointEntryRepository.save(PointEntry.cancel(credit, CANCELLED, null));
    pointEntryRepository.save(
        PointEntry.expire(memberId, EXPIRED, List.of(PointAllocation.of(creditId, EXPIRED))));
    pointEntryRepository.save(
        PointEntry.withdrawalClear(
            memberId, CLEARED, List.of(PointAllocation.of(creditId, CLEARED)), null));

    JsonNode content = get("/platform/me/points/entries").getBody().path("content");

    List<String> types = new ArrayList<>();
    content.forEach(row -> types.add(row.path("entry_type").asString("")));
    assertThat(types).containsExactly("WITHDRAWAL_CLEAR", "EXPIRE", "CANCEL", "MANUAL_ADJUST");
  }

  @Test
  @DisplayName("カーソルで明細を最後まで重複なく辿れること")
  void walksEveryEntryThroughTheCursor() {
    // 1 頁に収まらない件数を積む。増減で 1 行ずつ見分ける。
    List<Integer> seeded = List.of(771001, 771002, 771003, 771004, 771005);
    seeded.forEach(this::seedCredit);

    List<String> walked = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      ResponseEntity<JsonNode> page =
          get("/platform/me/points/entries?size=2" + (cursor == null ? "" : "&cursor=" + cursor));
      assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
      page.getBody().path("content").forEach(row -> walked.add(row.path("amount").asString()));
      JsonNode next = page.getBody().path("next_cursor");
      cursor = next.isString() ? next.asString() : null;
      pages++;
    } while (cursor != null && pages < 10);

    assertThat(cursor).as("続きを辿り切ること").isNull();
    // 続きの比較が並びと逆向きだと、2 頁目以降が手前へ戻って重複するか、丸ごと飛ぶ。
    assertThat(walked)
        .containsExactlyElementsOf(seeded.reversed().stream().map(String::valueOf).toList());
  }

  @Test
  @DisplayName("他の会員の仕訳と残高は本人の応答に現れないこと")
  void neverExposesAnotherMembersLedger() {
    seedCredit(CREDIT_PLAIN);
    long otherMemberId = registerAndLogin().id();
    pointEntryRepository.save(
        PointEntry.manualAdjust(
            otherMemberId,
            STORE_A,
            OTHER_MEMBER_CREDIT,
            "他会員の調整",
            null,
            List.of(),
            null,
            "member-point-it-other-" + otherMemberId));

    ResponseEntity<String> entries =
        rest.exchange(
            "/platform/me/points/entries",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            String.class);
    ResponseEntity<JsonNode> balance = get("/platform/me/points/balance");

    assertThat(entries.getBody())
        .as("他会員の仕訳の実データが本人の明細に現れないこと")
        .doesNotContain(String.valueOf(OTHER_MEMBER_CREDIT));
    assertThat(balance.getBody().path("balance").asLong())
        .as("他会員の加算が本人の残高へ混ざらないこと")
        .isEqualTo(CREDIT_PLAIN);
  }

  @Test
  @DisplayName("会員でない利用者は本人向けのポイント読み口へ到達できないこと")
  void refusesANonMemberPrincipal() {
    // 基底クラスのシードユーザーは店舗スタッフ。店舗側の残高照会は持つが、本人向けの読み口は持たない。
    ResponseEntity<String> denied =
        rest.exchange(
            "/platform/me/points/balance",
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            String.class);

    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  /** 本人の台帳へ 4 件の仕訳を積む。A 店の加算 2 件（うち 1 件は期限付き）、B 店の減算 1 件、発生店舗を持たない失効 1 件。 */
  private void seedLedger() {
    seedCredit(CREDIT_WITH_EXPIRY, EXPIRY);
    long creditId = seedCredit(CREDIT_PLAIN);
    pointEntryRepository.save(
        PointEntry.manualAdjust(
            memberId,
            STORE_B,
            DEBIT_AT_OTHER_STORE,
            "他店舗での減算",
            null,
            List.of(PointAllocation.of(creditId, -DEBIT_AT_OTHER_STORE)),
            null,
            "member-point-it-debit-" + memberId));
    pointEntryRepository.save(
        PointEntry.expire(memberId, EXPIRED, List.of(PointAllocation.of(creditId, EXPIRED))));
  }

  private long seedCredit(int amount) {
    return seedCredit(amount, null);
  }

  private long seedCredit(int amount, LocalDate expiresOn) {
    return pointEntryRepository
        .save(
            PointEntry.manualAdjust(
                memberId,
                STORE_A,
                amount,
                "検証用の加算",
                expiresOn,
                List.of(),
                null,
                "member-point-it-" + memberId + "-" + amount))
        .getId();
  }

  /** 明細を「種別・増減・店舗名・有効期限」の組へ畳む。null の項目は non_null 包含で応答から消えるため空文字で表す。 */
  private static List<List<String>> rowsOf(JsonNode content) {
    List<List<String>> rows = new ArrayList<>();
    content.forEach(
        row ->
            rows.add(
                List.of(
                    row.path("entry_type").asString(""),
                    row.path("amount").asString(""),
                    row.path("store_name").asString(""),
                    row.path("expires_on").asString(""))));
    return rows;
  }

  private ResponseEntity<JsonNode> get(String path) {
    return rest.exchange(
        path, HttpMethod.GET, new HttpEntity<>(bearer(memberToken)), JsonNode.class);
  }

  /** 登録した会員の本人確認材料。 */
  private record RegisteredMember(long id, String token) {}

  /** 会員を登録してログインし、その会員 ID と token を返す。 */
  private RegisteredMember registerAndLogin() {
    String email = "member-point-it-" + System.nanoTime() + "@kizuna.test";
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"ポイント検証会員\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(registration.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    long registeredId =
        memberRepository
            .findByMemberCode(registration.getBody().path("member_code").asString())
            .map(Member::getId)
            .orElseThrow();

    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    return new RegisteredMember(registeredId, login.getBody().path("token").asString());
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
