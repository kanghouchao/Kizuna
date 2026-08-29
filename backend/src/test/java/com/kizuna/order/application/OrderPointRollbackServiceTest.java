package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.order.api.dto.OrderPointRollbackPreviewResponse;
import com.kizuna.order.api.dto.OrderPointRollbackRequest;
import com.kizuna.order.api.dto.OrderPointRollbackResponse;
import com.kizuna.order.api.dto.OrderReceiptTokenResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.point.application.PointLedgerService.PointRollbackPreview;
import com.kizuna.point.application.PointLedgerService.PointRollbackResult;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 受注を宛先とするポイント巻き戻し（ADR 0023）。
 *
 * <p>この層で守るのは「どの受注なら巻き戻せるか」と「何を押さえてから台帳へ渡すか」で、打ち消しの規則そのものは台帳側の責務にある。
 */
@ExtendWith(MockitoExtension.class)
class OrderPointRollbackServiceTest {

  private static final String ORDER_ID = "o1";
  private static final String ACTOR_EMAIL = "manager@kizuna.test";
  private static final long ACTOR_ID = 42L;
  private static final long STORE_ID = 1L;
  private static final long MEMBER_ID = 7L;
  private static final String MEMBER_CODE = "123456789012";
  private static final String REASON = "来店そのものが誤登録だったため";
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-29T10:00:00+09:00");

  @Mock private OrderRepository orderRepository;
  @Mock private OrderAttributionRepository orderAttributionRepository;
  @Mock private OrderAttributionService orderAttributionService;
  @Mock private OrderReceiptTokenRepository orderReceiptTokenRepository;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private PlatformUserRepository platformUserRepository;

  @InjectMocks private OrderPointRollbackService service;

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
  }

  @Test
  @DisplayName("完了した受注の巻き戻しは、実行者を解いて台帳へ受注 ID と理由をそのまま渡すこと")
  void rollbackDelegatesToTheLedgerWithTheResolvedActor() {
    givenOrder(OrderStatus.COMPLETED);
    Mockito.when(pointLedgerService.rollbackForOrder(ORDER_ID, REASON, ACTOR_ID))
        .thenReturn(new PointRollbackResult(120, 300));

    OrderPointRollbackResponse response = service.rollback(ORDER_ID, request(), ACTOR_EMAIL);

    assertThat(response).isEqualTo(new OrderPointRollbackResponse(120, 300));
  }

  @Test
  @DisplayName("受注行と伝票トークン行を押さえてから台帳へ渡すこと（事後申領との直列化）")
  void rollbackLocksTheOrderAndItsReceiptTokensFirst() {
    // 押さえないと、申領は操作記録を見ず、巻き戻しは申領の付与を見ないまま双方が成立し、
    // 打ち消せない付与が残る。
    givenOrder(OrderStatus.COMPLETED);
    Mockito.when(pointLedgerService.rollbackForOrder(ORDER_ID, REASON, ACTOR_ID))
        .thenReturn(new PointRollbackResult(0, 0));

    service.rollback(ORDER_ID, request(), ACTOR_EMAIL);

    InOrder inOrder =
        Mockito.inOrder(orderRepository, orderReceiptTokenRepository, pointLedgerService);
    inOrder.verify(orderRepository).findScopedByIdForUpdate(ORDER_ID);
    inOrder.verify(orderReceiptTokenRepository).findByOrderIdForUpdate(ORDER_ID);
    inOrder.verify(pointLedgerService).rollbackForOrder(ORDER_ID, REASON, ACTOR_ID);
  }

  @Test
  @DisplayName("完了していない受注は巻き戻せず、台帳へ届かないこと")
  void refusesAnOrderThatIsNotCompleted() {
    // 確定済みを許すと、記録だけが先に書かれたあとの完了が付与を積み、その付与を打ち消す手立てが残らない。
    givenOrder(OrderStatus.CONFIRMED);

    assertThatThrownBy(() -> service.rollback(ORDER_ID, request(), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("完了した受注だけが");
    Mockito.verifyNoInteractions(pointLedgerService);
  }

  @Test
  @DisplayName("現店舗で引けない受注は 404 で、他店舗か不在かを区別しないこと")
  void refusesAnOrderOutsideTheCurrentStore() {
    Mockito.when(orderRepository.findScopedByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rollback(ORDER_ID, request(), ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class);
    Mockito.verifyNoInteractions(pointLedgerService);
  }

  @Test
  @DisplayName("認証主体が解決できなければ実行者 null で記帳せず失敗すること")
  void refusesAStalePrincipal() {
    // 台帳では実行者 null が「機構が起こした仕訳」の形であり、人手の操作と区別できなくなる。
    givenOrder(OrderStatus.COMPLETED);
    Mockito.when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rollback(ORDER_ID, request(), ACTOR_EMAIL))
        .isInstanceOf(StaleSessionException.class);
    Mockito.verifyNoInteractions(pointLedgerService);
  }

  @Test
  @DisplayName("再発行は受注行を押さえてから巻き戻し済みかを判じ、済みなら発行しないこと")
  void reissueRefusesARolledBackOrderWhileHoldingTheOrderLock() {
    // 判定だけ別の取引で先に済ませると、その隙間へ巻き戻しが割り込んだとき、申領が必ず拒む QR を発行する。
    givenOrder(OrderStatus.COMPLETED);
    Mockito.when(pointLedgerService.isRolledBack(ORDER_ID)).thenReturn(true);

    assertThatThrownBy(() -> service.reissueReceiptTokenUnlessRolledBack(ORDER_ID))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("巻き戻した受注には伝票を再発行できません");

    InOrder inOrder = Mockito.inOrder(orderRepository, pointLedgerService);
    inOrder.verify(orderRepository).findScopedByIdForUpdate(ORDER_ID);
    inOrder.verify(pointLedgerService).isRolledBack(ORDER_ID);
    Mockito.verifyNoInteractions(orderAttributionService);
  }

  @Test
  @DisplayName("巻き戻されていない受注には従来どおり再発行すること")
  void reissueProceedsForAnIntactOrder() {
    givenOrder(OrderStatus.COMPLETED);
    Mockito.when(pointLedgerService.isRolledBack(ORDER_ID)).thenReturn(false);
    OrderReceiptTokenResponse issued = new OrderReceiptTokenResponse("raw");
    Mockito.when(orderAttributionService.reissueReceiptToken(ORDER_ID)).thenReturn(issued);

    assertThat(service.reissueReceiptTokenUnlessRolledBack(ORDER_ID)).isEqualTo(issued);
  }

  @Test
  @DisplayName("下見は台帳の見込みに、現に帰属している会員コードを添えて返すこと")
  void previewCarriesTheActiveMemberCode() {
    Mockito.when(orderRepository.findScopedById(ORDER_ID))
        .thenReturn(Optional.of(completedOrder()));
    Mockito.when(pointLedgerService.previewRollbackForOrder(ORDER_ID))
        .thenReturn(new PointRollbackPreview(false, 120, 300));
    OrderAttribution active = OrderAttribution.onCompletion(ORDER_ID, MEMBER_ID, MEMBER_CODE, NOW);
    Mockito.when(orderAttributionRepository.findByOrderIdOrderByIdDesc(ORDER_ID))
        .thenReturn(List.of(active));

    assertThat(service.preview(ORDER_ID))
        .isEqualTo(new OrderPointRollbackPreviewResponse(false, MEMBER_CODE, 120, 300));
  }

  @Test
  @DisplayName("無効化された帰属しか無い受注の下見は、宛先の会員を名乗らないこと")
  void previewNamesNoMemberWhenTheAttributionIsInvalidated() {
    // 無効化済みの記録は「現に帰属している」ではない。名乗ると、既に外れた相手の台帳を見に行かせる。
    Mockito.when(orderRepository.findScopedById(ORDER_ID))
        .thenReturn(Optional.of(completedOrder()));
    Mockito.when(pointLedgerService.previewRollbackForOrder(ORDER_ID))
        .thenReturn(new PointRollbackPreview(true, 0, 0));
    OrderAttribution invalidated =
        OrderAttribution.onCompletion(ORDER_ID, MEMBER_ID, MEMBER_CODE, NOW);
    invalidated.invalidate(REASON, ACTOR_ID, NOW);
    Mockito.when(orderAttributionRepository.findByOrderIdOrderByIdDesc(ORDER_ID))
        .thenReturn(List.of(invalidated));

    assertThat(service.preview(ORDER_ID))
        .isEqualTo(new OrderPointRollbackPreviewResponse(true, null, 0, 0));
  }

  private void givenOrder(OrderStatus status) {
    Order order =
        Order.builder().businessDate(LocalDate.parse("2026-08-28")).status(status).build();
    order.setId(ORDER_ID);
    order.setStoreId(STORE_ID);
    Mockito.when(orderRepository.findScopedByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
  }

  private static Order completedOrder() {
    Order order =
        Order.builder()
            .businessDate(LocalDate.parse("2026-08-28"))
            .status(OrderStatus.COMPLETED)
            .build();
    order.setId(ORDER_ID);
    order.setStoreId(STORE_ID);
    return order;
  }

  private static OrderPointRollbackRequest request() {
    OrderPointRollbackRequest request = new OrderPointRollbackRequest();
    request.setReason(REASON);
    return request;
  }
}
