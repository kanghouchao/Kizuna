package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.order.api.dto.OrderAttributionCorrectionRequest;
import com.kizuna.order.api.dto.OrderAttributionCorrectionResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 誤帰属の台帳訂正（ADR 0012）。
 *
 * <p>この層で守るのは「誰から引くか」と「いくらまで引けるか」で、引き当ての規則そのものは台帳側の責務にある。
 */
@ExtendWith(MockitoExtension.class)
class OrderAttributionCorrectionServiceTest {

  private static final String ORDER_ID = "o1";
  private static final String ACTOR_EMAIL = "manager@kizuna.test";
  private static final long ACTOR_ID = 42L;
  private static final long STORE_ID = 1L;
  private static final long ATTRIBUTED_MEMBER_ID = 7L;
  private static final String MEMBER_CODE = "123456789012";
  private static final String OTHER_CODE = "999999999999";
  private static final long ATTRIBUTION_ID = 501L;
  private static final long OTHER_ATTRIBUTION_ID = 502L;
  private static final String REASON = "別人の来店を取り違えたため";
  private static final String KEY = "idem-1";
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-14T10:00:00+09:00");

  @Mock private OrderRepository orderRepository;
  @Mock private OrderAttributionRepository orderAttributionRepository;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private StoreContext storeContext;

  @InjectMocks private OrderAttributionCorrectionService service;

  @BeforeEach
  void resolveActor() {
    PlatformUser actor =
        PlatformUser.builder()
            .email(ACTOR_EMAIL)
            .password("encoded")
            .displayName("店長 花子")
            .enabled(true)
            .userType(UserType.STAFF)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(STORE_ID))
            .roleIds(Set.of(1L))
            .build();
    actor.setId(ACTOR_ID);
    Mockito.lenient()
        .when(platformUserRepository.findByEmail(ACTOR_EMAIL))
        .thenReturn(Optional.of(actor));
    Mockito.lenient().when(storeContext.getStoreId()).thenReturn(STORE_ID);
  }

  @Test
  @DisplayName("差し引く相手は帰属記録が持つ会員であって、顧客に今紐づく会員ではないこと")
  void debitsTheMemberRecordedOnTheAttribution() {
    givenOrder();
    givenAttribution(invalidatedAttribution());
    givenGranted(100L);
    givenCorrected(0L, ATTRIBUTION_ID);

    service.correct(ORDER_ID, request(100), ACTOR_EMAIL);

    // 顧客経路を一切読まないことが、関連の解除・張り替えがあっても宛先がぶれない理由そのものである。
    Mockito.verify(pointLedgerService)
        .correctForAttribution(
            ATTRIBUTED_MEMBER_ID, STORE_ID, -100, REASON, ACTOR_ID, KEY, ATTRIBUTION_ID);
  }

  @Test
  @DisplayName("有効な帰属のポイントは差し引けないこと（先に無効化を求める）")
  void refusesToDebitAnActiveAttribution() {
    givenOrder();
    OrderAttribution active =
        OrderAttribution.onCompletion(ORDER_ID, ATTRIBUTED_MEMBER_ID, MEMBER_CODE, NOW);
    active.setId(ATTRIBUTION_ID);
    givenAttribution(active);

    assertThatThrownBy(() -> service.correct(ORDER_ID, request(100), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("無効化");

    Mockito.verifyNoInteractions(pointLedgerService);
  }

  @Test
  @DisplayName("別の受注の帰属記録を名指した訂正は届かないこと")
  void refusesAnAttributionBelongingToAnotherOrder() {
    givenOrder();
    // この受注が持つのは別の記録だけ。帰属記録は platform 帰属で storeFilter が働かないため、
    // id を直に引く形なら同一店舗の別受注の記録にも当たってしまう。
    OrderAttribution own =
        OrderAttribution.onCompletion(ORDER_ID, ATTRIBUTED_MEMBER_ID, MEMBER_CODE, NOW);
    own.setId(OTHER_ATTRIBUTION_ID);
    own.invalidate(REASON, ACTOR_ID, NOW);
    givenAttributions(own);

    assertThatThrownBy(() -> service.correct(ORDER_ID, request(100), ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class);

    Mockito.verifyNoInteractions(pointLedgerService);
  }

  @Test
  @DisplayName("同じ会員の別の帰属記録に積んだ訂正も上限に数えること（作用域が付与と揃うこと）")
  void countsCorrectionsAcrossEveryAttributionOfTheSameMember() {
    // 無効化 → 再発行 → 本人が申領し直す、を二度たどると同じ会員に同じ受注の付与が 2 本並ぶ。
    // 記録ごとに訂正済みを数えると、片方の枠がもう片方の付与まで飲み込む（既定値のまま送るだけで
    // 正当な付与が消える）。
    givenOrder();
    givenAttributions(reclaimedAttribution(), invalidatedAttribution());
    givenGranted(200L);
    givenCorrected(100L, ATTRIBUTION_ID, OTHER_ATTRIBUTION_ID);

    assertThatThrownBy(() -> service.correct(ORDER_ID, request(200), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("上限");

    verifyNoLedgerWrite();
  }

  @Test
  @DisplayName("その会員が現に帰属している受注では差し引けないこと")
  void refusesWhenTheSameMemberHoldsAnActiveAttribution() {
    // 台帳の付与行はどちらの帰属で付いたのかを持たない。正当な付与と誤った付与を切り分けられない以上、
    // 撥ねるほうが安全側である。
    givenOrder();
    OrderAttribution active =
        OrderAttribution.onReceiptClaim(ORDER_ID, ATTRIBUTED_MEMBER_ID, MEMBER_CODE, NOW);
    active.setId(OTHER_ATTRIBUTION_ID);
    givenAttributions(active, invalidatedAttribution());

    assertThatThrownBy(() -> service.correct(ORDER_ID, request(100), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("現に帰属している");

    verifyNoLedgerWrite();
  }

  @Test
  @DisplayName("正しい本人の帰属が別会員として成立していても、古い記録の訂正は妨げないこと（正の対照）")
  void allowsCorrectionWhenAnotherMemberHoldsTheActiveAttribution() {
    givenOrder();
    OrderAttribution otherMember = OrderAttribution.onReceiptClaim(ORDER_ID, 9L, OTHER_CODE, NOW);
    otherMember.setId(OTHER_ATTRIBUTION_ID);
    givenAttributions(otherMember, invalidatedAttribution());
    givenGranted(100L);
    givenCorrected(0L, ATTRIBUTION_ID);

    service.correct(ORDER_ID, request(100), ACTOR_EMAIL);

    Mockito.verify(pointLedgerService)
        .correctForAttribution(
            ATTRIBUTED_MEMBER_ID, STORE_ID, -100, REASON, ACTOR_ID, KEY, ATTRIBUTION_ID);
  }

  @Test
  @DisplayName("現況が別人の有効な帰属に入れ替わっても、残っている訂正を名指せること")
  void namesTheOutstandingCorrectionEvenWhenTheCurrentAttributionChanged() {
    // 訂正を後回しにした操作者が戻る道。現況の読み口は直近 1 件しか返さないので、これが無いと
    // 誤付与は相手の台帳に残ったまま画面からは二度と辿り着けない。
    givenOrder();
    OrderAttribution otherMember = OrderAttribution.onReceiptClaim(ORDER_ID, 9L, OTHER_CODE, NOW);
    otherMember.setId(OTHER_ATTRIBUTION_ID);
    givenAttributions(otherMember, invalidatedAttribution());
    givenGranted(100L);
    Mockito.when(pointLedgerService.correctedPointsFor(List.of(ATTRIBUTION_ID))).thenReturn(0L);

    assertThat(service.findPendingCorrection(ORDER_ID))
        .contains(
            new OrderAttributionCorrectionService.PendingCorrection(ATTRIBUTION_ID, MEMBER_CODE));
  }

  @Test
  @DisplayName("直近の無効化記録が引き切り済みでも、その先に残る古い記録を名指せること")
  void looksPastAnAlreadySettledInvalidationForAnOlderOutstandingOne() {
    // 2 人が続けて誤帰属し、どちらの差し引きも後回しにしたあと新しい側だけ片付けた状態。
    // 先に直近 1 件を選んでから条件を当てる形では、古い側が画面から永久に名指せなくなる。
    givenOrder();
    OrderAttribution settled = OrderAttribution.onReceiptClaim(ORDER_ID, 9L, OTHER_CODE, NOW);
    settled.setId(OTHER_ATTRIBUTION_ID);
    settled.invalidate(REASON, ACTOR_ID, NOW);
    givenAttributions(settled, invalidatedAttribution());
    givenGrantedFor(9L, 100L);
    Mockito.when(pointLedgerService.correctedPointsFor(List.of(OTHER_ATTRIBUTION_ID)))
        .thenReturn(100L);
    givenGranted(100L);
    Mockito.when(pointLedgerService.correctedPointsFor(List.of(ATTRIBUTION_ID))).thenReturn(0L);

    assertThat(service.findPendingCorrection(ORDER_ID))
        .contains(
            new OrderAttributionCorrectionService.PendingCorrection(ATTRIBUTION_ID, MEMBER_CODE));
  }

  @Test
  @DisplayName("記帳済みの冪等キーの再送は、現況を材料にする門で撥ねないこと")
  void letsACommittedRetryReachTheLedgerEvenAfterTheMemberReclaimed() {
    // 初回が commit したあと会員が再発行された伝票を申領し直すと、現況では門に掛かる。だが再送そのものは
    // 正当で、台帳が冪等キーで収束させる（ADR 0007）。ここで撥ねるとその機会自体が失われる。
    givenOrder();
    OrderAttribution reclaimed =
        OrderAttribution.onReceiptClaim(ORDER_ID, ATTRIBUTED_MEMBER_ID, MEMBER_CODE, NOW);
    reclaimed.setId(OTHER_ATTRIBUTION_ID);
    givenAttributions(reclaimed, invalidatedAttribution());
    Mockito.when(pointLedgerService.isIdempotencyKeyCommitted(KEY)).thenReturn(true);
    givenGranted(200L);
    givenCorrected(0L, ATTRIBUTION_ID);

    service.correct(ORDER_ID, request(100), ACTOR_EMAIL);

    Mockito.verify(pointLedgerService)
        .correctForAttribution(
            ATTRIBUTED_MEMBER_ID, STORE_ID, -100, REASON, ACTOR_ID, KEY, ATTRIBUTION_ID);
  }

  @Test
  @DisplayName("引き切った受注では残っている訂正を名乗らないこと")
  void namesNoOutstandingCorrectionOnceFullyDebited() {
    givenOrder();
    givenAttributions(invalidatedAttribution());
    givenGranted(100L);
    Mockito.when(pointLedgerService.correctedPointsFor(List.of(ATTRIBUTION_ID))).thenReturn(100L);

    assertThat(service.findPendingCorrection(ORDER_ID)).isEmpty();
  }

  @Test
  @DisplayName("その会員が現に帰属している受注では残っている訂正を名乗らないこと（押せない導線を描かない）")
  void namesNoOutstandingCorrectionWhileTheSameMemberIsAttributed() {
    givenOrder();
    OrderAttribution active =
        OrderAttribution.onReceiptClaim(ORDER_ID, ATTRIBUTED_MEMBER_ID, MEMBER_CODE, NOW);
    active.setId(OTHER_ATTRIBUTION_ID);
    givenAttributions(active, invalidatedAttribution());

    assertThat(service.findPendingCorrection(ORDER_ID)).isEmpty();
  }

  @Test
  @DisplayName("他店舗の受注は帰属記録を読む前に 404 で止まること")
  void refusesAnOrderOutsideTheStore() {
    Mockito.when(orderRepository.findScopedByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.correct(ORDER_ID, request(100), ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class);

    Mockito.verifyNoInteractions(orderAttributionRepository);
    Mockito.verifyNoInteractions(pointLedgerService);
  }

  @Test
  @DisplayName("同じ帰属記録に対する訂正の累計は付与額を超えられないこと")
  void refusesToExceedTheGrantedTotal() {
    givenOrder();
    givenAttribution(invalidatedAttribution());
    givenGranted(100L);
    givenCorrected(70L, ATTRIBUTION_ID);

    assertThatThrownBy(() -> service.correct(ORDER_ID, request(31), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("上限");

    verifyNoLedgerWrite();
  }

  @Test
  @DisplayName("上限ちょうどは通ること（正の対照）")
  void allowsExactlyTheGrantedTotal() {
    givenOrder();
    givenAttribution(invalidatedAttribution());
    givenGranted(100L);
    givenCorrected(70L, ATTRIBUTION_ID);

    OrderAttributionCorrectionResponse response =
        service.correct(ORDER_ID, request(30), ACTOR_EMAIL);

    assertThat(response.grantedPoints()).isEqualTo(100L);
    assertThat(response.correctedPoints()).isEqualTo(100L);
  }

  @Test
  @DisplayName("上限の判定が同じ冪等キーの記帳を数えないこと（正当な再送が超過で撥ねられない）")
  void doesNotCountItsOwnRetryTowardTheCap() {
    givenOrder();
    givenAttribution(invalidatedAttribution());
    givenGranted(100L);
    // 初回の 100 は記帳済み。だが同じキーの行を除けば 0 で、再送は初回と同じ判定に落ちなければならない。
    givenCorrected(0L, ATTRIBUTION_ID);
    // 除外しない読み方（表示用の重載）を掴むと 100 + 100 > 100 となり、応答を取り逃しただけの正当な再送が
    // 撥ねられる（ADR 0007 と同型の罠）。この stub が無いと Mockito の既定値 0 が返り、誤った実装でも緑になる。
    Mockito.lenient()
        .when(pointLedgerService.correctedPointsFor(List.of(ATTRIBUTION_ID)))
        .thenReturn(100L);

    service.correct(ORDER_ID, request(100), ACTOR_EMAIL);

    Mockito.verify(pointLedgerService)
        .correctForAttribution(
            ATTRIBUTED_MEMBER_ID, STORE_ID, -100, REASON, ACTOR_ID, KEY, ATTRIBUTION_ID);
  }

  @Test
  @DisplayName("進み具合は同じ会員の帰属記録すべての訂正を数えること")
  void statusCountsCorrectionsAcrossEveryAttributionOfTheSameMember() {
    givenOrder();
    givenAttributions(reclaimedAttribution(), invalidatedAttribution());
    givenGranted(200L);
    Mockito.when(
            pointLedgerService.correctedPointsFor(
                Mockito.argThat(
                    ids ->
                        ids != null
                            && ids.containsAll(List.of(ATTRIBUTION_ID, OTHER_ATTRIBUTION_ID)))))
        .thenReturn(100L);

    OrderAttributionCorrectionResponse response = service.status(ORDER_ID, ATTRIBUTION_ID);

    // 画面はこの差を既定値に取る。書き込み側と作用域がずれると、撥ねられる額を既定値として描く。
    assertThat(response.grantedPoints()).isEqualTo(200L);
    assertThat(response.correctedPoints()).isEqualTo(100L);
  }

  @Test
  @DisplayName("進み具合も書き込みと同じ門を通ること（撥ねられる材料を画面へ渡さない）")
  void statusRefusesWhenTheSameMemberHoldsAnActiveAttribution() {
    givenOrder();
    OrderAttribution active =
        OrderAttribution.onReceiptClaim(ORDER_ID, ATTRIBUTED_MEMBER_ID, MEMBER_CODE, NOW);
    active.setId(OTHER_ATTRIBUTION_ID);
    givenAttributions(active, invalidatedAttribution());

    assertThatThrownBy(() -> service.status(ORDER_ID, ATTRIBUTION_ID))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("現に帰属している");
  }

  @Test
  @DisplayName("会員が削除された帰属は積み先が無く、訂正できないこと")
  void refusesWhenTheMemberReferenceIsMissing() {
    givenOrder();
    OrderAttribution orphaned = invalidatedAttribution();
    ReflectionMemberId.clear(orphaned);
    givenAttribution(orphaned);

    assertThatThrownBy(() -> service.correct(ORDER_ID, request(100), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("削除");

    Mockito.verifyNoInteractions(pointLedgerService);
  }

  @Test
  @DisplayName("失効した認証セッションの訂正は実行者不明のまま通らないこと")
  void refusesAnUnresolvableActor() {
    givenOrder();
    givenAttribution(invalidatedAttribution());
    givenGranted(100L);
    givenCorrected(0L, ATTRIBUTION_ID);
    Mockito.when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.correct(ORDER_ID, request(100), ACTOR_EMAIL))
        .isInstanceOf(StaleSessionException.class);

    // 上限を判じるために台帳を読むところまでは進むので、見るべきは「記帳していない」ことである。
    verifyNoLedgerWrite();
  }

  // ==================== 補助 ====================

  /** 台帳へ記帳していないこと。読み（上限の判定）は無害なので、写像を狭めて書き込みだけを見る。 */
  private void verifyNoLedgerWrite() {
    Mockito.verify(pointLedgerService, Mockito.never())
        .correctForAttribution(
            Mockito.anyLong(),
            Mockito.any(),
            Mockito.anyInt(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.anyLong());
  }

  private void givenOrder() {
    Order order =
        Order.builder()
            .businessDate(LocalDate.parse("2026-08-10"))
            .status(OrderStatus.COMPLETED)
            .build();
    order.setId(ORDER_ID);
    order.setStoreId(STORE_ID);
    Mockito.lenient().when(orderRepository.findScopedById(ORDER_ID)).thenReturn(Optional.of(order));
    Mockito.lenient()
        .when(orderRepository.findScopedByIdForUpdate(ORDER_ID))
        .thenReturn(Optional.of(order));
  }

  /** この受注が持つ帰属記録（新しい順）。候補は受注から引くので、ここに無い ID は名指せない。 */
  private void givenAttributions(OrderAttribution... rows) {
    Mockito.lenient()
        .when(orderAttributionRepository.findByOrderIdOrderByIdDesc(ORDER_ID))
        .thenReturn(List.of(rows));
  }

  private void givenAttribution(OrderAttribution row) {
    givenAttributions(row);
  }

  /** 既に引かれた量。ID の集合が期待どおりの作用域を覆っているときだけ答える。 */
  private void givenCorrected(long amount, Long... expectedIds) {
    Mockito.when(
            pointLedgerService.correctedPointsFor(
                Mockito.argThat(ids -> ids != null && ids.containsAll(List.of(expectedIds))),
                Mockito.eq(KEY)))
        .thenReturn(amount);
  }

  private void givenGranted(long total) {
    givenGrantedFor(ATTRIBUTED_MEMBER_ID, total);
  }

  private void givenGrantedFor(long memberId, long total) {
    Mockito.lenient()
        .when(pointLedgerService.grantedPointsByOrder(memberId, List.of(ORDER_ID)))
        .thenReturn(Map.of(ORDER_ID, total));
  }

  /** 同じ会員が再発行された伝票を申領し直し、その帰属もまた無効化された状態（付与が 2 本並ぶ）。 */
  private static OrderAttribution reclaimedAttribution() {
    OrderAttribution row =
        OrderAttribution.onReceiptClaim(ORDER_ID, ATTRIBUTED_MEMBER_ID, MEMBER_CODE, NOW);
    row.setId(OTHER_ATTRIBUTION_ID);
    row.invalidate(REASON, ACTOR_ID, NOW);
    return row;
  }

  private static OrderAttribution invalidatedAttribution() {
    OrderAttribution row =
        OrderAttribution.onCompletion(ORDER_ID, ATTRIBUTED_MEMBER_ID, MEMBER_CODE, NOW);
    row.setId(ATTRIBUTION_ID);
    row.invalidate(REASON, ACTOR_ID, NOW);
    return row;
  }

  private static OrderAttributionCorrectionRequest request(int points) {
    OrderAttributionCorrectionRequest request = new OrderAttributionCorrectionRequest();
    request.setAttributionId(ATTRIBUTION_ID);
    request.setPoints(points);
    request.setReason(REASON);
    request.setIdempotencyKey(KEY);
    return request;
  }

  /** 会員削除で FK が SET NULL になった状態を作る。域には会員参照を落とす操作が無いため、ここでだけ直接倒す。 */
  private static final class ReflectionMemberId {
    static void clear(OrderAttribution attribution) {
      try {
        var field = OrderAttribution.class.getDeclaredField("memberId");
        field.setAccessible(true);
        field.set(attribution, null);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
