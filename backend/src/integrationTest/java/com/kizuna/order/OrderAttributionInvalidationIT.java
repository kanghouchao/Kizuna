package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionSource;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 誤帰属の訂正（無効化と伝票トークンの再発行）を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは 4 つ。①無効化が理由・実行者・時刻を<b>行として残す</b>こと（列の書き込みが実際に往復すること）。
 * ②無効化された来店が会員の履歴から消える一方でポイント台帳は動かないこと — 清算は手動調整であり、訂正は機械の級聯にしない（ADR 0009）。
 * ③再発行された伝票の申領期限が<b>再発行時点</b>から 90 日で数え直されること（同 ADR の意図的な例外）。 ④再発行された伝票で正しい本人が申領でき、根拠 RECEIPT_TOKEN
 * の帰属が成立すること。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与、利用は 100 ポイント単位」。
 */
class OrderAttributionInvalidationIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** v0.5.0 の山田次郎シード（STORE_STAFF・授権店舗 = 店舗1）。店舗A の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final int TOTAL_FEE = 12000;

  /** シード設定（100 円ごとに 1 ポイント）を {@link #TOTAL_FEE} に当てた付与額。 */
  private static final int EXPECTED_POINTS = 120;

  private static final String REASON = "別人の来店として取り違えたため";

  @Autowired private OrderAttributionRepository orderAttributionRepository;
  @Autowired private OrderReceiptTokenRepository orderReceiptTokenRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @PersistenceContext private EntityManager entityManager;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("無効化は理由・実行者・時刻を行に残し、帰属そのものの記録は書き換えないこと")
  void invalidationPersistsTheReasonActorAndTime() {
    RegisteredMember wrongMember = registerAndLogin("wrong");
    String orderId = completedOrderAttributedTo(wrongMember, "誤帰属担当-" + nonce);

    ResponseEntity<JsonNode> invalidated = invalidate(orderId, REASON);

    assertThat(invalidated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(invalidated.getBody().path("attributed").asBoolean()).isFalse();

    // 実データを読む。@Column の書き込み可否を取り違えると、行は INVALIDATED になりつつ理由だけ NULL になり、
    // 集約を往復させないテストは緑のままになる
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "select status, invalidated_reason, invalidated_by, invalidated_at"
                + " from t_order_attributions where order_id = ?",
            orderId);
    assertThat(row.get("status")).isEqualTo(OrderAttributionStatus.INVALIDATED.name());
    assertThat(row.get("invalidated_reason")).isEqualTo(REASON);
    assertThat(row.get("invalidated_by")).isEqualTo(seedStaffId());
    assertThat(row.get("invalidated_at")).isNotNull();

    // 誰の来店として記録されていたかは訂正後も辿れること（監査の対象そのもの）
    OrderAttribution attribution = attributionOf(orderId);
    assertThat(attribution.getMemberCode()).isEqualTo(wrongMember.memberCode());
    assertThat(attribution.getSource()).isEqualTo(OrderAttributionSource.COMPLETION);
  }

  @Test
  @DisplayName("無効化で来店は会員の履歴から消え、ポイント台帳は動かないこと（清算は手動調整）")
  void invalidationHidesTheVisitButLeavesTheLedgerUntouched() {
    RegisteredMember wrongMember = registerAndLogin("ledger");
    String castName = "台帳無波及担当-" + nonce;
    String orderId = completedOrderAttributedTo(wrongMember, castName);

    assertThat(castNamesInVisitsOf(wrongMember)).as("前提: 無効化の前は来店として見えること").contains(castName);
    assertThat(balanceOf(wrongMember)).as("前提: 付与が台帳へ入っていること").isEqualTo(EXPECTED_POINTS);

    assertThat(invalidate(orderId, REASON).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(castNamesInVisitsOf(wrongMember)).as("無効化された来店は履歴から消えること").doesNotContain(castName);
    // 誤って付与されたポイントは残る。取り消すなら理由の残る手動調整で行う（ADR 0009）
    assertThat(balanceOf(wrongMember)).as("台帳は動かないこと").isEqualTo(EXPECTED_POINTS);
    assertThat(ledgerRowsFor(orderId)).as("台帳へ打ち消しの仕訳も積まないこと").isEqualTo(1);
  }

  @Test
  @DisplayName("理由の無い無効化は撥ねられ、帰属記録が有効なまま残ること")
  void invalidationWithoutAReasonIsRejected() {
    RegisteredMember wrongMember = registerAndLogin("noreason");
    String orderId = completedOrderAttributedTo(wrongMember, "理由なし担当-" + nonce);

    assertThat(invalidate(orderId, " ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(attributionOf(orderId).getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
  }

  @Test
  @DisplayName("画面が見ていた記録と現に有効な記録がずれた無効化は 409 で差し戻され、正しい帰属が残ること")
  void invalidationOfAStaleTargetIsRefused() {
    // 画面を開いたまま別の操作者が訂正を一巡させると、有効な記録は「正しい本人の新しい帰属」に入れ替わる。
    // 受注から対象を導く実装だと、古い理由がその新しい帰属へ当たって取り戻したばかりの来店を消す
    RegisteredMember wrongMember = registerAndLogin("stale");
    RegisteredMember rightMember = registerAndLogin("staleowner");
    String castName = "取り違え上書き担当-" + nonce;
    Issued original = completedOrderWithToken(castName);
    assertThat(claim(wrongMember, original.token()).getStatusCode())
        .as("前提: 誤った本人が申領できること")
        .isEqualTo(HttpStatus.OK);
    Long staleTarget = activeAttributionIdOf(original.orderId());

    // 別の操作者が一巡させる
    assertThat(invalidate(original.orderId(), staleTarget, REASON).getStatusCode())
        .as("前提: 一巡目の無効化ができること")
        .isEqualTo(HttpStatus.OK);
    String raw = reissue(original.orderId()).getBody().path("receipt_token").asString();
    assertThat(claim(rightMember, raw).getStatusCode())
        .as("前提: 正しい本人が取り戻せること")
        .isEqualTo(HttpStatus.OK);

    // 開いたままだった画面が、古い記録を指したまま送信する
    ResponseEntity<JsonNode> stale = invalidate(original.orderId(), staleTarget, "古い理由");

    assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(castNamesInVisitsOf(rightMember)).as("正しい本人の来店が消えないこと").contains(castName);
    OrderAttribution active =
        attributionsOf(original.orderId()).stream()
            .filter(row -> row.getStatus() == OrderAttributionStatus.ACTIVE)
            .findFirst()
            .orElseThrow();
    assertThat(active.getMemberCode()).isEqualTo(rightMember.memberCode());
    assertThat(active.getInvalidatedReason()).as("誤った監査記録も残さないこと").isNull();
  }

  @Test
  @DisplayName("再発行の申領期限は原期限を引き継がず、再発行から 90 日で数え直されること")
  void reissuedTokenRestartsTheNinetyDayWindow() {
    // 完了時に会員へ達しなかった受注から始める。誤帰属が申領で成立した形なので、この受注には原期限を持つ
    // 伝票が実在し、「原期限を引き継ぐ」実装と「数え直す」実装を見分けられる（完了時帰属の受注には
    // 引き継ぐ元の期限がそもそも無く、どちらの実装でも同じ値になってしまう）
    RegisteredMember wrongMember = registerAndLogin("window");
    Issued original = completedOrderWithToken("期限再計算担当-" + nonce);
    assertThat(claim(wrongMember, original.token()).getStatusCode())
        .as("前提: 誤った本人が申領できること")
        .isEqualTo(HttpStatus.OK);
    assertThat(invalidate(original.orderId(), REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);

    // 原期限を「もうすぐ切れる」側へ倒す。引き継ぐ実装ならここで倒した値がそのまま新しい伝票の期限になる
    OffsetDateTime shortenedExpiry = OffsetDateTime.now().plusDays(3);
    jdbcTemplate.update(
        "update t_order_receipt_tokens set expires_at = ? where order_id = ?",
        shortenedExpiry,
        original.orderId());

    ResponseEntity<JsonNode> reissued = reissue(original.orderId());

    assertThat(reissued.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(reissued.getBody().path("receipt_token").asString()).isNotBlank();
    OrderReceiptToken token =
        orderReceiptTokenRepository.findById(issuedTokenIdOf(original.orderId())).orElseThrow();
    assertThat(token.getIssuedAt()).isAfter(OffsetDateTime.now().minusMinutes(5));
    assertThat(ChronoUnit.DAYS.between(token.getIssuedAt(), token.getExpiresAt()))
        .isEqualTo(OrderReceiptToken.VALIDITY.toDays());
    assertThat(token.getExpiresAt()).as("誤帰属の期間に食い潰された原期限を引き継がないこと").isAfter(shortenedExpiry);
  }

  @Test
  @DisplayName("再発行の付与予定額は完了時に確定した額を引き継ぎ、受注の付与実績（非会員完了では 0）を読まないこと")
  void reissueCarriesForwardThePlannedPointsFixedAtCompletion() {
    RegisteredMember wrongMember = registerAndLogin("planned");
    RegisteredMember rightMember = registerAndLogin("plannedowner");
    Issued original = completedOrderWithToken("予定額引継担当-" + nonce);
    // 発行時の確定額を現行規則では出得ない値へ倒す。会計金額から計算し直す実装ならここで倒した値が消える
    int fixedPoints = 7;
    jdbcTemplate.update(
        "update t_order_receipt_tokens set planned_points = ? where order_id = ?",
        fixedPoints,
        original.orderId());
    assertThat(claim(wrongMember, original.token()).getStatusCode())
        .as("前提: 誤った本人が申領できること")
        .isEqualTo(HttpStatus.OK);
    assertThat(invalidate(original.orderId(), REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);

    String raw = reissue(original.orderId()).getBody().path("receipt_token").asString();

    assertThat(claim(rightMember, raw).getBody().path("granted_points").asInt())
        .isEqualTo(fixedPoints);
    assertThat(balanceOf(rightMember)).isEqualTo(fixedPoints);
  }

  @Test
  @DisplayName("再発行された伝票で正しい本人が申領でき、根拠 RECEIPT_TOKEN の帰属が成立すること")
  void theRightMemberReclaimsTheVisitWithTheReissuedToken() {
    RegisteredMember wrongMember = registerAndLogin("thief");
    RegisteredMember rightMember = registerAndLogin("owner");
    String castName = "取り戻し担当-" + nonce;
    String orderId = completedOrderAttributedTo(wrongMember, castName);
    assertThat(invalidate(orderId, REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);

    String raw = reissue(orderId).getBody().path("receipt_token").asString();
    ResponseEntity<JsonNode> claimed = claim(rightMember, raw);

    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 付与予定額は完了時点で確定した額を引き継ぐ（訂正の早い遅いで同じ会計が別のポイントにならない）
    assertThat(claimed.getBody().path("granted_points").asInt()).isEqualTo(EXPECTED_POINTS);

    List<OrderAttribution> rows = attributionsOf(orderId);
    assertThat(rows).as("無効化した記録は残り、新しい帰属が別の行として生まれること").hasSize(2);
    OrderAttribution active =
        rows.stream()
            .filter(row -> row.getStatus() == OrderAttributionStatus.ACTIVE)
            .findFirst()
            .orElseThrow();
    assertThat(active.getSource()).isEqualTo(OrderAttributionSource.RECEIPT_TOKEN);
    assertThat(active.getMemberCode()).isEqualTo(rightMember.memberCode());

    assertThat(castNamesInVisitsOf(rightMember)).as("正しい本人の履歴に現れること").contains(castName);
    assertThat(balanceOf(rightMember)).isEqualTo(EXPECTED_POINTS);
    assertThat(castNamesInVisitsOf(wrongMember))
        .as("誤って帰属していた側からは消えたままであること")
        .doesNotContain(castName);
    // 誤帰属の付与は残ったまま。清算は手動調整で行うため、同じ受注に付与が 2 本立つ
    assertThat(ledgerRowsFor(orderId)).isEqualTo(2);
  }

  @Test
  @DisplayName("有効な帰属のある受注・帰属したことのない受注へは再発行できないこと")
  void reissueIsRefusedUnlessTheOrderWasInvalidated() {
    RegisteredMember member = registerAndLogin("guard");
    String attributedOrder = completedOrderAttributedTo(member, "帰属中担当-" + nonce);
    assertThat(reissue(attributedOrder).getStatusCode())
        .as("先に無効化していない受注へは発行しないこと")
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 完了時に会員へ達しなかった受注は、そもそも完了の応答で伝票を受け取っている。訂正の経路を発行の経路にしない
    String neverAttributed = completedOrderWithoutMember("無帰属担当-" + nonce);
    assertThat(reissue(neverAttributed).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("再発行は前に発行した伝票を失効させ、申領できる伝票を 1 本だけ残すこと")
  void reissueRevokesThePreviouslyIssuedToken() {
    // 発行の応答を取り逃した店舗（生値は二度と手に入らない）が訂正をやり直せる唯一の経路。撥ねる形にすると
    // 以後 90 日どの再試行も通らず、店舗は QR を出せないまま訂正を完了できなくなる
    RegisteredMember wrongMember = registerAndLogin("double");
    RegisteredMember rightMember = registerAndLogin("doubleowner");
    String orderId = completedOrderAttributedTo(wrongMember, "二重発行担当-" + nonce);
    assertThat(invalidate(orderId, REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);
    String first = reissue(orderId).getBody().path("receipt_token").asString();

    ResponseEntity<JsonNode> second = reissue(orderId);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(tokenCountFor(orderId)).as("行は削除しないこと").isEqualTo(2);
    assertThat(issuedTokenCountFor(orderId)).as("申領できる伝票は 1 本だけであること").isEqualTo(1);
    // 生値まで確かめる。状態だけを見るテストは、失効した QR が実際には申領できてしまう実装でも緑になる
    assertThat(claim(rightMember, first).getStatusCode())
        .as("前に渡した QR は使えなくなること")
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            claim(rightMember, second.getBody().path("receipt_token").asString()).getStatusCode())
        .as("最後に発行した QR で取り戻せること")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("並行する再発行が終わっても、申領できる伝票は 1 本だけであること")
  void concurrentReissuesLeaveOnlyOneClaimableToken() throws Exception {
    // 直列化されないと両者が同じ状態を観測して 2 本とも生きたまま残る。そうなると後から申領した側は
    // 帰属記録の部分一意違反（500 系）で落ち、「申領できない伝票はすべて同形のエラー」が壊れる。
    // どちらの応答も成立してよい — 失効させる形なので、遅い側の発行が早い側を殺す
    RegisteredMember wrongMember = registerAndLogin("race");
    RegisteredMember rightMember = registerAndLogin("raceowner");
    String orderId = completedOrderAttributedTo(wrongMember, "並行再発行担当-" + nonce);
    assertThat(invalidate(orderId, REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<ResponseEntity<JsonNode>> responses = new ArrayList<>();
    try {
      List<Future<ResponseEntity<JsonNode>>> races = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        races.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await(10, TimeUnit.SECONDS);
                  return reissue(orderId);
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).as("前提: 2 つの再発行が同時に構えること").isTrue();
      go.countDown();

      for (Future<ResponseEntity<JsonNode>> race : races) {
        responses.add(race.get(30, TimeUnit.SECONDS));
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(responses)
        .as("前提: 並行する再発行がどちらも 500 を出さないこと")
        .allMatch(response -> response.getStatusCode() == HttpStatus.OK);
    assertThat(issuedTokenCountFor(orderId)).as("申領できる伝票は 1 本だけであること").isEqualTo(1);
    // 生値で確かめる。生きているのは 1 本だけなので、申領が成立するのも 1 本だけになる
    assertThat(responses)
        .filteredOn(
            response ->
                claim(rightMember, response.getBody().path("receipt_token").asString())
                        .getStatusCode()
                    == HttpStatus.OK)
        .as("申領が成立するのは 1 本だけ")
        .hasSize(1);
  }

  @Test
  @DisplayName("同一受注に申領できる伝票を 2 本並べる書き込みは DB が拒むこと")
  void theDatabaseRefusesASecondIssuedTokenForTheSameOrder() {
    // 再発行のロックが守る不変量の最終防波堤。索引が黙って消えても、アプリ層のテストは緑のままになる
    RegisteredMember wrongMember = registerAndLogin("dbguard");
    String orderId = completedOrderAttributedTo(wrongMember, "DB防波堤担当-" + nonce);
    assertThat(invalidate(orderId, REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);
    assertThat(reissue(orderId).getStatusCode()).as("前提: 1 本目を発行できること").isEqualTo(HttpStatus.OK);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into t_order_receipt_tokens"
                        + " (order_id, token_digest, planned_points, issued_at, expires_at,"
                        + " status, created_at, updated_at, version)"
                        + " values (?, ?, 0, now(), now() + interval '90 days', 'ISSUED',"
                        + " now(), now(), 0)",
                    orderId,
                    "db-guard-digest-" + nonce))
        .isInstanceOf(DuplicateKeyException.class);

    assertThat(issuedTokenCountFor(orderId)).isEqualTo(1);
  }

  @Test
  @DisplayName("申領が確定する直前の受注へ走った再発行は、確定を待ってから撥ねられること")
  void reissueWaitsForAnInFlightClaimBeforeJudging() throws Exception {
    // 症状の本体は期限境界の競合だが、申領と再発行がそれぞれ now を読む瞬間を HTTP 2 本の外から
    // 整列させる手段が無く、字面どおりの競合テストは改修前に赤くできない。断言面を配線へ下ろし、
    // 「申領が確定する直前の状態」を別トランザクションで作って、再発行がそれを待つことを固定する。
    RegisteredMember wrongMember = registerAndLogin("inflight");
    RegisteredMember rightMember = registerAndLogin("inflightowner");
    String orderId = completedOrderAttributedTo(wrongMember, "在途申領担当-" + nonce);
    assertThat(invalidate(orderId, REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);
    assertThat(reissue(orderId).getStatusCode()).as("前提: 訂正用の伝票を発行できること").isEqualTo(HttpStatus.OK);
    long tokenId = issuedTokenIdOf(orderId);

    CountDownLatch claimHeld = new CountDownLatch(1);
    CountDownLatch releaseClaim = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // 申領の書き込み集合をそのまま再現したまま commit せずに待つ（トークン行のロックを保持し続ける）
      Future<?> inFlightClaim =
          pool.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .execute(
                          status -> {
                            OrderReceiptToken token =
                                entityManager.find(
                                    OrderReceiptToken.class,
                                    tokenId,
                                    LockModeType.PESSIMISTIC_WRITE);
                            token.claim(OffsetDateTime.now());
                            entityManager.persist(
                                OrderAttribution.onReceiptClaim(
                                    orderId,
                                    rightMember.id(),
                                    rightMember.memberCode(),
                                    OffsetDateTime.now()));
                            entityManager.flush();
                            claimHeld.countDown();
                            awaitQuietly(releaseClaim);
                            return null;
                          }));
      assertThat(claimHeld.await(30, TimeUnit.SECONDS)).as("前提: 申領が確定直前の状態で待つこと").isTrue();

      Future<ResponseEntity<JsonNode>> concurrentReissue = pool.submit(() -> reissue(orderId));
      // 押さえずに読む実装は在途の申領を見ないまま即座に判じて返る。固定したいのは「待つこと」その
      // ものである — 待たない実装は、期限境界では申領が成立するのと同時に 2 本目を発行してしまう
      assertThatThrownBy(() -> concurrentReissue.get(3, TimeUnit.SECONDS))
          .as("再発行は申領の確定を待つこと")
          .isInstanceOf(TimeoutException.class);

      releaseClaim.countDown();
      inFlightClaim.get(30, TimeUnit.SECONDS);

      ResponseEntity<JsonNode> refused = concurrentReissue.get(30, TimeUnit.SECONDS);
      assertThat(refused.getStatusCode()).as("確定した帰属を見てから撥ねること").isEqualTo(HttpStatus.BAD_REQUEST);
    } finally {
      releaseClaim.countDown();
      pool.shutdownNow();
    }

    assertThat(issuedTokenCountFor(orderId)).as("有効な帰属と申領できる伝票が並ばないこと").isZero();
    assertThat(activeAttributionCountFor(orderId)).isEqualTo(1);
  }

  @Test
  @DisplayName("他店舗の受注の帰属は読めず、無効化も再発行もできないこと")
  void otherStoreCannotReachTheAttribution() {
    RegisteredMember member = registerAndLogin("cross");
    String castName = "他店舗担当-" + nonce;
    String orderId = completedOrderAttributedTo(member, castName);

    assertThat(
            rest.exchange(
                    "/store/orders/" + orderId + "/attribution",
                    HttpMethod.GET,
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    JsonNode.class)
                .getBody()
                .path("member_code")
                .asString())
        .as("正向対照: 自店舗では会員コードまで読めること")
        .isEqualTo(member.memberCode());

    ResponseEntity<JsonNode> leaked =
        rest.exchange(
            "/store/orders/" + orderId + "/attribution",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(leaked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(leaked.getBody().toString())
        .as("他店舗の応答が会員コードを運ばないこと")
        .doesNotContain(member.memberCode());

    ResponseEntity<JsonNode> foreignInvalidation =
        rest.exchange(
            "/store/orders/" + orderId + "/attribution/invalidation",
            HttpMethod.POST,
            new HttpEntity<>("{\"reason\": \"" + REASON + "\"}", storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(foreignInvalidation.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    ResponseEntity<JsonNode> foreignReissue =
        rest.exchange(
            "/store/orders/" + orderId + "/receipt-token",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(foreignReissue.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    assertThat(attributionOf(orderId).getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
    assertThat(castNamesInVisitsOf(member)).contains(castName);
  }

  // ==================== 訂正の操作 ====================

  /** 現に有効な帰属記録を対象に無効化する（画面が読み口で得た ID を添える経路と同じ形）。 */
  private ResponseEntity<JsonNode> invalidate(String orderId, String reason) {
    return invalidate(orderId, activeAttributionIdOf(orderId), reason);
  }

  private ResponseEntity<JsonNode> invalidate(String orderId, Long attributionId, String reason) {
    return rest.exchange(
        "/store/orders/" + orderId + "/attribution/invalidation",
        HttpMethod.POST,
        new HttpEntity<>(
            "{\"attribution_id\": " + attributionId + ", \"reason\": \"" + reason + "\"}",
            storeHeaders(STORE_A)),
        JsonNode.class);
  }

  /** 読み口が返す現況の ID。画面はこれを持って無効化を要求する。 */
  private Long activeAttributionIdOf(String orderId) {
    ResponseEntity<JsonNode> current =
        rest.exchange(
            "/store/orders/" + orderId + "/attribution",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(current.getStatusCode()).as("前提: 帰属の現況が読めること").isEqualTo(HttpStatus.OK);
    return current.getBody().path("id").asLong();
  }

  private ResponseEntity<JsonNode> reissue(String orderId) {
    return rest.exchange(
        "/store/orders/" + orderId + "/receipt-token",
        HttpMethod.POST,
        new HttpEntity<>(storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> claim(RegisteredMember member, String rawToken) {
    return rest.exchange(
        "/platform/me/receipts/claim",
        HttpMethod.POST,
        new HttpEntity<>("{\"token\": \"" + rawToken + "\"}", bearer(member.token())),
        JsonNode.class);
  }

  // ==================== 会員側の読み口 ====================

  private List<String> castNamesInVisitsOf(RegisteredMember member) {
    ResponseEntity<JsonNode> response =
        rest.exchange(
            "/platform/me/visits",
            HttpMethod.GET,
            new HttpEntity<>(bearer(member.token())),
            JsonNode.class);
    assertThat(response.getStatusCode()).as("前提: 来店履歴が読めること").isEqualTo(HttpStatus.OK);
    List<String> castNames = new ArrayList<>();
    for (JsonNode visit : response.getBody().path("content")) {
      castNames.add(visit.path("cast_name").asString());
    }
    return castNames;
  }

  private long balanceOf(RegisteredMember member) {
    ResponseEntity<JsonNode> response =
        rest.exchange(
            "/platform/me/points/balance",
            HttpMethod.GET,
            new HttpEntity<>(bearer(member.token())),
            JsonNode.class);
    assertThat(response.getStatusCode()).as("前提: 残高が読めること").isEqualTo(HttpStatus.OK);
    return response.getBody().path("balance").asLong();
  }

  // ==================== 実データの読み出し ====================

  private OrderAttribution attributionOf(String orderId) {
    List<OrderAttribution> found = attributionsOf(orderId);
    assertThat(found).as("受注 %s の帰属記録が 1 件だけであること", orderId).hasSize(1);
    return found.get(0);
  }

  private List<OrderAttribution> attributionsOf(String orderId) {
    return orderAttributionRepository.findAll().stream()
        .filter(row -> orderId.equals(row.getOrderId()))
        .toList();
  }

  private int ledgerRowsFor(String orderId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_point_entries where order_id = ?", Integer.class, orderId);
  }

  private int tokenCountFor(String orderId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_order_receipt_tokens where order_id = ?", Integer.class, orderId);
  }

  /** 申領できる伝票の本数。失効・申領済みを数えないので、状態遷移が実際に往復したことまで見る。 */
  private int issuedTokenCountFor(String orderId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_order_receipt_tokens where order_id = ? and status = 'ISSUED'",
        Integer.class,
        orderId);
  }

  private long issuedTokenIdOf(String orderId) {
    return jdbcTemplate.queryForObject(
        "select id from t_order_receipt_tokens where order_id = ? and status = 'ISSUED'",
        Long.class,
        orderId);
  }

  private int activeAttributionCountFor(String orderId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_order_attributions where order_id = ? and status = 'ACTIVE'",
        Integer.class,
        orderId);
  }

  /** 保持側のトランザクション内で待つ。中断はテストの失敗として現れるので、ここでは握り潰さず状態だけ戻す。 */
  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private long seedStaffId() {
    return jdbcTemplate.queryForObject(
        "select id from t_users where email = ?", Long.class, "yamada.jiro@kizuna.test");
  }

  // ==================== 受注の用意 ====================

  /** 会員へ帰属した完了を 1 件作る（顧客 → 有効な関連 → 会員の一本道を通す）。 */
  private String completedOrderAttributedTo(RegisteredMember member, String castName) {
    String customerId = createCustomer(castName);
    linkMember(customerId, member.memberCode());
    String orderId = confirmedOrder(customerId, castName);
    assertThat(complete(orderId).getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    assertThat(attributionOf(orderId).getSource()).isEqualTo(OrderAttributionSource.COMPLETION);
    return orderId;
  }

  /** 会員へ帰属しない完了を 1 件作る（完了の応答が伝票を運ぶ枝）。 */
  private String completedOrderWithoutMember(String castName) {
    String orderId = confirmedOrder(null, castName);
    assertThat(complete(orderId).getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    return orderId;
  }

  /** 発行された伝票トークンとその受注。 */
  private record Issued(String orderId, String token) {}

  /** 会員へ帰属しない完了を 1 件作り、発行された生値を受け取る。 */
  private Issued completedOrderWithToken(String castName) {
    String orderId = confirmedOrder(null, castName);
    ResponseEntity<JsonNode> completed = complete(orderId);
    assertThat(completed.getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    String raw = completed.getBody().path("receipt_token").asString();
    assertThat(raw).as("前提: 完了応答が伝票トークンを運ぶこと").isNotBlank();
    return new Issued(orderId, raw);
  }

  private String confirmedOrder(String customerId, String castName) {
    String castId = createCast(castName);
    String orderId = createOrder(castId, customerId, castName);
    assertThat(updateStatus(orderId, castId, "CONFIRMED")).as("前提: 受注を確定できること").isTrue();
    return orderId;
  }

  private ResponseEntity<JsonNode> complete(String orderId) {
    return rest.exchange(
        "/store/orders/" + orderId + "/completion",
        HttpMethod.POST,
        new HttpEntity<>("{\"total_fee\": " + TOTAL_FEE + "}", storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private String createOrder(String castId, String customerId, String remarks) {
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\", \"pax\": 2"
            + (customerId == null ? "" : ", \"customer_id\": \"" + customerId + "\"")
            + ", \"remarks\": \""
            + remarks
            + "\"}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, storeHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 受注作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private boolean updateStatus(String orderId, String castId, String status) {
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"cast_id\": \""
            + castId
            + "\", \"status\": \""
            + status
            + "\"}";
    return rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.PUT,
            new HttpEntity<>(body, storeHeaders(STORE_A)),
            JsonNode.class)
        .getStatusCode()
        .is2xxSuccessful();
  }

  private String createCast(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCustomer(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private void linkMember(String customerId, String memberCode) {
    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>("{\"member_code\": \"" + memberCode + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 会員の紐づけが成功すること").isEqualTo(HttpStatus.OK);
  }

  // ==================== 会員 ====================

  /** 訂正の前後を会員側の読み口で確かめるための本人確認材料。 */
  private record RegisteredMember(long id, String memberCode, String token) {}

  /**
   * 会員を 1 人作ってログインまで済ませる。
   *
   * <p>{@code prefix} は同じテスト内での呼び分け。email の local 部は 64 文字までなので、区別に要る分より長い接頭辞を 足さない（超えると登録が 400
   * になり、前提の失敗として現れる）。
   */
  private RegisteredMember registerAndLogin(String prefix) {
    String email = prefix + "-attr-inv-" + nonce + "@kizuna.test";
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"帰属訂正検証会員\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(registration.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    String memberCode = registration.getBody().path("member_code").asString();
    long registeredId =
        memberRepository.findByMemberCode(memberCode).map(Member::getId).orElseThrow();

    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    return new RegisteredMember(registeredId, memberCode, login.getBody().path("token").asString());
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders bearer(String bearerToken) {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
