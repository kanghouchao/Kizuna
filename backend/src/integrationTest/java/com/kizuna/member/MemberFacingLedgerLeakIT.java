package com.kizuna.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.ReceptionRoute;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
 * <p>紐づけは店舗が会員を自店舗の台帳に結び付ける操作であって、会員に台帳を開くものではない。ランク・区分・NG・連絡先などの内部評価は 店舗の内部情報であり、本人であっても
 * 会員側の経路からは到達できてはならない。
 *
 * <p>ポイント残高は会員自身のものなので、本人向けのポイント端点<b>だけ</b>が残高系の項目を返してよい。残りの会員側端点では 従来どおり禁止する（端点ごとの許可は {@link
 * MemberEndpoint#exposesBalance}）。手動調整の理由は店員が書く運用内部の文言なので、 ポイント端点でも禁止のままにする。
 *
 * <p>数値のカナリアは会員の台帳へ仕込む。顧客の保有ポイント列は廃止され、残高は台帳の合計としてのみ存在する。
 *
 * <p>断言は 2 段。生ボディに対しては、実データそのもの（カナリア文字列）と<b>引用符付きの項目名</b>（{@code "rank"} 等）の 非混入を見る — 応答には JWT
 * が含まれ、base64url の字母表に {@code "} は無いため、引用符付きなら token
 * 由来の偶然一致で赤くならない。加えて項目を返す端点は項目名の白名単反復で、想定外の項目が増えたら落ちるようにする。
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

  /** 手動調整の理由。店員が書く運用内部の文言であり、本人向けのポイント明細にも現れてはならない。 */
  private static final String CANARY_REASON = "CANARY-REASON-ecb1f0d4-8a2c";

  /** 会員のポイント台帳へ仕込む加算。本人向けのポイント端点だけが返してよく、他の会員側端点には現れてはならない。 */
  private static final int CANARY_ADJUSTED = 987654321;

  /**
   * 来店した受注の会計金額と利用ポイント。店舗の会計の内部事情であり、来店履歴を含むどの会員側端点にも現れてはならない。
   *
   * <p>数値は互いに部分文字列にならない値を選び、桁数も残高のカナリアに揃える — 短い数は snowflake の ID の途中に偶然現れうる。
   */
  private static final int CANARY_TOTAL_FEE = 483920175;

  private static final int CANARY_USED_POINTS = 271649538;

  /** 来店で得たポイント。来店履歴が返してよい唯一の数値なので、上の 2 つと見分けが付く値にする。 */
  private static final int CANARY_GRANTED_POINTS = 6318;

  /** 来店の担当キャスト名。来店履歴の担当が実際に埋まっていることの確認に使う。 */
  private static final String VISIT_CAST_NAME = "来店担当-ecb1f0d4-8a2c";

  /** 仕込んだ加算と来店の付与を合わせた残高。残高を返してよい端点ではこの合計が読める。 */
  private static final int CANARY_BALANCE = CANARY_ADJUSTED + CANARY_GRANTED_POINTS;

  /** 加算ロットの有効期限。ポイント明細の期限表示が実際に通ることの確認に使う。 */
  private static final LocalDate CANARY_EXPIRY = LocalDate.of(2099, 12, 31);

  private static final List<String> CANARY_VALUES =
      List.of(
          CANARY_RANK,
          CANARY_CLASSIFICATION,
          CANARY_NG_TYPE,
          CANARY_NG_CONTENT,
          CANARY_PHONE,
          CANARY_LINE_ID,
          CANARY_ADDRESS,
          CANARY_USAGE_AREAS,
          CANARY_REASON);

  /** 残高そのものを指す項目名。ポイント端点にだけ許し、他の端点では下の一覧の一部として禁止し続ける。 */
  private static final List<String> BALANCE_FIELD_NAMES =
      List.of("\"points\"", "\"point_balance\"", "\"balance\"");

  /** 台帳側と会計側の項目名。引用符付きで見ることで JWT の base64url 本体との偶然一致を排除する。 */
  private static final List<String> LEDGER_FIELD_NAMES =
      List.of(
          "\"total_fee\"",
          "\"used_points\"",
          "\"auto_grant_points\"",
          "\"rank\"",
          "\"classification\"",
          "\"ng_type\"",
          "\"ng_content\"",
          "\"points\"",
          "\"point_balance\"",
          "\"balance\"",
          "\"phone_number\"",
          "\"phone_number2\"",
          "\"line_id\"",
          "\"address\"",
          "\"building_name\"",
          "\"usage_areas\"",
          "\"has_pet\"",
          "\"member_linked\"",
          "\"linked_member_code\"");

  /** ポイント端点で禁止する項目名。残高系だけを上の一覧から差し引いて作る — 2 本の一覧を並べて書くと、 台帳側に項目が増えたときに片方だけが更新されて掃き出しが静かに緩む。 */
  private static final List<String> POINT_ENDPOINT_DENIED_FIELD_NAMES =
      LEDGER_FIELD_NAMES.stream().filter(name -> !BALANCE_FIELD_NAMES.contains(name)).toList();

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

  /** ポイント明細 1 行が返してよい項目名。増えたら落ちる。 */
  private static final List<String> MEMBER_POINT_ENTRY_ALLOWED_FIELDS =
      List.of("occurred_on", "store_name", "entry_type", "amount", "expires_on");

  /** 来店 1 件が返してよい項目名。増えたら落ちる。 */
  private static final List<String> MEMBER_VISIT_ALLOWED_FIELDS =
      List.of("visited_on", "store_name", "pax", "cast_name", "granted_points");

  /** 伝票トークンの申領が返してよい項目名。増えたら落ちる。 */
  private static final List<String> MEMBER_RECEIPT_CLAIM_ALLOWED_FIELDS = List.of("granted_points");

  /**
   * 掃き出す会員側の GET 端点。残高系の項目を許すかは端点ごとに決める。
   *
   * <p>申領（{@code POST /platform/me/receipts/claim}）はこの表に入らない — 一覧の掃き出しは同じ要求を何度でも
   * 撃てることが前提で、トークンの状態を進める操作は載せられない。代わりに専用の 1 件（{@link
   * #memberReceiptClaimReturnsOnlyWhitelistedFields}）で同じ掃き出しを当てる。
   *
   * @param exposesBalance 残高系の項目と残高の実値を返してよい端点か
   */
  private record MemberEndpoint(String path, boolean exposesBalance) {}

  private static final List<MemberEndpoint> MEMBER_GET_ENDPOINTS =
      List.of(
          new MemberEndpoint("/platform/me", false),
          new MemberEndpoint("/platform/me/member", false),
          new MemberEndpoint("/platform/me/orders", false),
          new MemberEndpoint("/platform/me/visits", false),
          new MemberEndpoint("/platform/me/points/balance", true),
          new MemberEndpoint("/platform/me/points/entries", true));

  @Autowired private CustomerRepository customerRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private CastRepository castRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderAttributionRepository orderAttributionRepository;

  private String customerId;
  private String memberEmail;
  private String memberCode;
  private String memberToken;
  private String registrationBody;
  private String loginBody;

  @BeforeEach
  void seedLinkedLedgerCustomer() {
    // storeFilter を経由しない直挿しで店舗1 の顧客を用意する（NG 等は作成 API に項目が無いため）。
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
            .rank(CANARY_RANK)
            .lineId(CANARY_LINE_ID)
            .usageAreas(CANARY_USAGE_AREAS)
            .ngType(CANARY_NG_TYPE)
            .ngContent(CANARY_NG_CONTENT)
            .build();
    customer.setStoreId(STORE_A);
    customerId = customerRepository.save(customer).getId();

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

    // 残高は顧客ではなく会員の台帳が持つため、数値のカナリアは台帳の仕訳として積む。発生店舗と有効期限を
    // 埋めるのは、明細の store_name / expires_on が non_null 包含で消えたまま素通りしないようにするため。
    long memberId = memberRepository.findByMemberCode(memberCode).map(Member::getId).orElseThrow();
    pointEntryRepository.save(
        PointEntry.manualAdjust(
            memberId,
            STORE_A,
            CANARY_ADJUSTED,
            CANARY_REASON,
            CANARY_EXPIRY,
            List.of(),
            null,
            "ledger-leak-" + memberId));

    seedAttributedVisit(memberId);

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

    // 残高も同じく、店舗側の照会からは読める（数値のカナリアが台帳へ届いていることの証明でもある）
    ResponseEntity<String> storeBalance =
        rest.exchange(
            "/store/customers/" + customerId + "/member-point-balance",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            String.class);
    assertThat(storeBalance.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storeBalance.getBody()).contains(String.valueOf(CANARY_BALANCE));

    // 予約申請は紐づけ済み顧客に結び付くため、台帳側の項目が会員側の一覧へ回り込む余地が最も大きい経路になる。
    requestReservation();

    for (MemberEndpoint endpoint : MEMBER_GET_ENDPOINTS) {
      ResponseEntity<String> response =
          rest.exchange(
              endpoint.path(), HttpMethod.GET, new HttpEntity<>(bearer(memberToken)), String.class);
      assertThat(response.getStatusCode())
          .as("前提: %s が読めること", endpoint.path())
          .isEqualTo(HttpStatus.OK);
      assertNoLedgerLeak("GET " + endpoint.path(), response.getBody(), endpoint.exposesBalance());
    }

    assertNoLedgerLeak("会員登録", registrationBody, false);
    assertNoLedgerLeak("平台ログイン", loginBody, false);
  }

  @Test
  @DisplayName("残高は本人向けのポイント端点だけが返し、他の会員側端点では禁止のままであること")
  void onlyThePointEndpointsExposeTheBalance() {
    // 掃き出しの断言（残高がポイント端点以外に現れない）が空振りでないことの正向対照。
    // 残高系の許可を端点ごとに分けた以上、許した側で実際に読めることまで見ないと、
    // 「どこにも出ない」状態でも掃き出しは緑のままになる。
    ResponseEntity<String> balance =
        rest.exchange(
            "/platform/me/points/balance",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            String.class);

    assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(balance.getBody()).contains(String.valueOf(CANARY_BALANCE));
  }

  @Test
  @DisplayName("ポイント残高は残高だけを返すこと（項目名の白名単）")
  void memberPointBalanceReturnsOnlyWhitelistedFields() {
    ResponseEntity<JsonNode> balance =
        rest.exchange(
            "/platform/me/points/balance",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            JsonNode.class);

    assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> fields = new ArrayList<>();
    balance.getBody().propertyNames().forEach(fields::add);
    assertThat(fields).as("ポイント残高の応答項目（想定外の項目が増えていないこと）").containsExactly("balance");
  }

  @Test
  @DisplayName("ポイント明細は日付・店舗・種別・増減・有効期限だけを返すこと（項目名の白名単）")
  void memberPointEntryReturnsOnlyWhitelistedFields() {
    ResponseEntity<JsonNode> entries =
        rest.exchange(
            "/platform/me/points/entries",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            JsonNode.class);

    assertThat(entries.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 値が null の項目は non_null 包含設定で応答から消えるため、白名単は「これ以外が現れないこと」で見る。
    // 断言対象は手動調整の行に固定する — 発生店舗と有効期限を持つのはこの行だけで、来店の付与行を
    // 掴むと期限が空のまま白名単を通り、項目の欠落を検出できなくなる。
    JsonNode adjustment = null;
    for (JsonNode row : entries.getBody().path("content")) {
      if ("MANUAL_ADJUST".equals(row.path("entry_type").asString(""))) {
        adjustment = row;
      }
    }
    assertThat(adjustment).as("前提: 仕込んだ仕訳が明細に現れること").isNotNull();
    List<String> fields = new ArrayList<>();
    adjustment.propertyNames().forEach(fields::add);
    assertThat(fields)
        .as("ポイント明細 1 件の応答項目（想定外の項目が増えていないこと）")
        .containsExactlyInAnyOrderElementsOf(MEMBER_POINT_ENTRY_ALLOWED_FIELDS);
  }

  @Test
  @DisplayName("来店履歴は日付・店舗・人数・担当・獲得ポイントだけを返すこと（項目名の白名単）")
  void memberVisitReturnsOnlyWhitelistedFields() {
    ResponseEntity<JsonNode> visits =
        rest.exchange(
            "/platform/me/visits",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            JsonNode.class);

    assertThat(visits.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode first = visits.getBody().path("content").path(0);
    assertThat(first.isObject()).as("前提: 仕込んだ来店が履歴に現れること").isTrue();
    List<String> fields = new ArrayList<>();
    first.propertyNames().forEach(fields::add);
    // 値が null の項目は non_null 包含設定で応答から消えるため、仕込んだ来店は人数も担当も埋めてある。
    // 掃き出しの断言が空振りでないことの正向対照も兼ねて、返してよい実データが実際に読めることまで見る。
    assertThat(fields)
        .as("来店 1 件の応答項目（想定外の項目が増えていないこと）")
        .containsExactlyInAnyOrderElementsOf(MEMBER_VISIT_ALLOWED_FIELDS);
    assertThat(first.path("cast_name").asString()).isEqualTo(VISIT_CAST_NAME);
    assertThat(first.path("granted_points").asInt()).isEqualTo(CANARY_GRANTED_POINTS);
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

  /**
   * 会員へ帰属した来店を 1 件仕込む。会計金額・利用ポイントを埋めた完了受注に帰属記録を添え、来店履歴の項目に会計が回り込む余地を作る。
   *
   * <p>完了 API ではなく直挿しにするのは、会計の実データを狙った値で置くため（付与額は付与設定から決まり、利用は残高の 引き当てを伴う）。完了から帰属記録が生まれる経路そのものは
   * {@code OrderAttributionIT} が固定している。
   */
  private void seedAttributedVisit(long memberId) {
    Cast cast = Cast.builder().name(VISIT_CAST_NAME).build();
    cast.setStoreId(STORE_A);
    String castId = castRepository.save(cast).getId();

    Order order =
        Order.builder()
            .businessDate(LocalDate.now(ZoneId.of("Asia/Tokyo")))
            .customerId(customerId)
            .castId(castId)
            .pax(2)
            .status(OrderStatus.COMPLETED)
            .receptionRoute(ReceptionRoute.PHONE)
            .totalFee(CANARY_TOTAL_FEE)
            .usedPoints(CANARY_USED_POINTS)
            .autoGrantPoints(CANARY_GRANTED_POINTS)
            .build();
    order.setStoreId(STORE_A);
    String orderId = orderRepository.save(order).getId();

    orderAttributionRepository.save(
        OrderAttribution.onCompletion(orderId, memberId, memberCode, OffsetDateTime.now()));
    pointEntryRepository.save(
        PointEntry.grantForOrder(memberId, orderId, STORE_A, CANARY_GRANTED_POINTS, null));
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
                    + LocalDate.now(ZoneId.of("Asia/Tokyo"))
                    + "\", \"pax\": 2, \"declared_name\": \"名乗り太郎\"}",
                headers),
            JsonNode.class);
    assertThat(requested.getStatusCode()).as("前提: 会員が予約を申請できること").isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("伝票トークンの申領は記帳したポイントだけを返すこと（項目名の白名単＋台帳の掃き出し）")
  void memberReceiptClaimReturnsOnlyWhitelistedFields() {
    // 申領は会員が店舗の受注へ触れる唯一の書き込みで、応答に受注や台帳の項目を添えたくなる形をしている。
    // 帰属しなかった完了（＝トークンが発行される唯一の経路）を本番の完了 API で作って申領する
    String rawToken = issueReceiptTokenForUnattributedVisit();

    // トークンは 1 度しか使えないため、生ボディの掃き出しと項目名の白名単を同じ 1 応答から見る
    ResponseEntity<JsonNode> claimed =
        rest.postForEntity(
            "/platform/me/receipts/claim",
            new HttpEntity<>("{\"token\": \"" + rawToken + "\"}", bearer(memberToken)),
            JsonNode.class);

    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertNoLedgerLeak("POST /platform/me/receipts/claim", claimed.getBody().toString(), false);
    List<String> fields = new ArrayList<>();
    claimed.getBody().propertyNames().forEach(fields::add);
    assertThat(fields)
        .as("申領の応答項目（想定外の項目が増えていないこと）")
        .containsExactlyInAnyOrderElementsOf(MEMBER_RECEIPT_CLAIM_ALLOWED_FIELDS);
  }

  /**
   * 会員へ帰属しない完了を店舗側の本番経路で 1 件作り、発行された伝票トークンの生値を返す。
   *
   * <p>紐づけ済みの顧客に着けると完了時に会員へ帰属してトークンが発行されないため、顧客を着けない受注で作る。
   */
  private String issueReceiptTokenForUnattributedVisit() {
    ResponseEntity<JsonNode> cast =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>(
                "{\"name\": \"申領検証担当-" + System.nanoTime() + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(cast.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    String castId = cast.getBody().path("id").asString();

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders",
            new HttpEntity<>(
                "{\"receptionist_id\": 3, \"business_date\": \""
                    + LocalDate.now(ZoneId.of("Asia/Tokyo"))
                    + "\", \"cast_id\": \""
                    + castId
                    + "\", \"pax\": 2}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 受注作成が成功すること").isTrue();
    String orderId = created.getBody().path("id").asString();

    ResponseEntity<JsonNode> confirmed =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.PUT,
            new HttpEntity<>(
                "{\"receptionist_id\": 3, \"cast_id\": \""
                    + castId
                    + "\", \"status\": \"CONFIRMED\"}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(confirmed.getStatusCode().is2xxSuccessful()).as("前提: 受注を確定できること").isTrue();

    ResponseEntity<JsonNode> completed =
        rest.exchange(
            "/store/orders/" + orderId + "/completion",
            HttpMethod.POST,
            new HttpEntity<>("{\"total_fee\": 3000}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(completed.getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    String rawToken = completed.getBody().path("receipt_token").asString();
    assertThat(rawToken).as("前提: 会員へ帰属しない完了が伝票トークンを発行すること").isNotBlank();
    return rawToken;
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

  /**
   * 1 端点分の掃き出し。顧客台帳の実データと項目名は常に禁止で、残高だけが端点ごとに許否が分かれる。
   *
   * @param exposesBalance 残高系の項目と残高の実値を返してよい端点か
   */
  private static void assertNoLedgerLeak(String endpoint, String body, boolean exposesBalance) {
    assertThat(body).as("%s の応答本文", endpoint).isNotBlank();
    for (String canary : CANARY_VALUES) {
      assertThat(body).as("%s に台帳の実データ %s が現れないこと", endpoint, canary).doesNotContain(canary);
    }
    // 会計の実データは残高と違って端点ごとの許否が無い — 来店履歴を含め、どの会員側端点にも現れてはならない。
    assertThat(body)
        .as("%s に会計金額が現れないこと", endpoint)
        .doesNotContain(String.valueOf(CANARY_TOTAL_FEE));
    assertThat(body)
        .as("%s に利用ポイントが現れないこと", endpoint)
        .doesNotContain(String.valueOf(CANARY_USED_POINTS));
    if (!exposesBalance) {
      assertThat(body)
          .as("%s にポイント残高が現れないこと", endpoint)
          .doesNotContain(String.valueOf(CANARY_BALANCE));
      assertThat(body)
          .as("%s に台帳の増減が現れないこと", endpoint)
          .doesNotContain(String.valueOf(CANARY_ADJUSTED));
    }
    List<String> deniedFieldNames =
        exposesBalance ? POINT_ENDPOINT_DENIED_FIELD_NAMES : LEDGER_FIELD_NAMES;
    for (String fieldName : deniedFieldNames) {
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
