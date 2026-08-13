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
  private static final String OTHER_ORDER_ID = "o2";
  private static final String ACTOR_EMAIL = "manager@kizuna.test";
  private static final long ACTOR_ID = 42L;
  private static final long STORE_ID = 1L;
  private static final long ATTRIBUTED_MEMBER_ID = 7L;
  private static final String MEMBER_CODE = "123456789012";
  private static final long ATTRIBUTION_ID = 501L;
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
    Mockito.when(pointLedgerService.correctedPointsFor(ATTRIBUTION_ID, KEY)).thenReturn(0L);

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
    OrderAttribution foreign =
        OrderAttribution.onCompletion(OTHER_ORDER_ID, ATTRIBUTED_MEMBER_ID, MEMBER_CODE, NOW);
    foreign.setId(ATTRIBUTION_ID);
    foreign.invalidate(REASON, ACTOR_ID, NOW);
    givenAttribution(foreign);

    // 帰属記録は platform 帰属で storeFilter が働かず、id 直引きは同一店舗の別受注にも当たる。
    assertThatThrownBy(() -> service.correct(ORDER_ID, request(100), ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class);

    Mockito.verifyNoInteractions(pointLedgerService);
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
    Mockito.when(pointLedgerService.correctedPointsFor(ATTRIBUTION_ID, KEY)).thenReturn(70L);

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
    Mockito.when(pointLedgerService.correctedPointsFor(ATTRIBUTION_ID, KEY)).thenReturn(70L);

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
    Mockito.when(pointLedgerService.correctedPointsFor(ATTRIBUTION_ID, KEY)).thenReturn(0L);
    // 除外しない読み方（表示用の重載）を掴むと 100 + 100 > 100 となり、応答を取り逃しただけの正当な再送が
    // 撥ねられる（ADR 0007 と同型の罠）。この stub が無いと Mockito の既定値 0 が返り、誤った実装でも緑になる。
    Mockito.lenient().when(pointLedgerService.correctedPointsFor(ATTRIBUTION_ID)).thenReturn(100L);

    service.correct(ORDER_ID, request(100), ACTOR_EMAIL);

    Mockito.verify(pointLedgerService)
        .correctForAttribution(
            ATTRIBUTED_MEMBER_ID, STORE_ID, -100, REASON, ACTOR_ID, KEY, ATTRIBUTION_ID);
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
    Mockito.when(pointLedgerService.correctedPointsFor(ATTRIBUTION_ID, KEY)).thenReturn(0L);
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

  private void givenAttribution(OrderAttribution row) {
    Mockito.lenient()
        .when(orderAttributionRepository.findById(ATTRIBUTION_ID))
        .thenReturn(Optional.of(row));
  }

  private void givenGranted(long total) {
    Mockito.lenient()
        .when(pointLedgerService.grantedPointsByOrder(ATTRIBUTED_MEMBER_ID, List.of(ORDER_ID)))
        .thenReturn(Map.of(ORDER_ID, total));
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
