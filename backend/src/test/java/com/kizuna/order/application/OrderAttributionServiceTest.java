package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.order.api.dto.OrderAttributionInvalidationRequest;
import com.kizuna.order.api.dto.OrderAttributionResponse;
import com.kizuna.order.api.dto.OrderReceiptTokenResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderAttributionServiceTest {

  private static final String ORDER_ID = "o1";
  private static final String ACTOR_EMAIL = "staff@kizuna.test";
  private static final long ACTOR_ID = 42L;
  private static final long MEMBER_ID = 7L;
  private static final String MEMBER_CODE = "123456789012";
  private static final String REASON = "別人の来店を取り違えたため";
  private static final int COMPLETION_GRANT = 98;
  private static final int TOKEN_PLANNED_POINTS = 120;

  @Mock private OrderRepository orderRepository;
  @Mock private OrderAttributionRepository orderAttributionRepository;
  @Mock private OrderReceiptTokenRepository orderReceiptTokenRepository;
  @Mock private ReceiptTokenGenerator receiptTokenGenerator;
  @Mock private PlatformUserRepository platformUserRepository;

  @InjectMocks private OrderAttributionService service;

  @Captor private ArgumentCaptor<OrderReceiptToken> savedToken;

  @BeforeEach
  void resolveActor() {
    PlatformUser actor =
        PlatformUser.builder()
            .email(ACTOR_EMAIL)
            .password("encoded")
            .displayName("店舗 太郎")
            .enabled(true)
            .userType(UserType.STAFF)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .roleIds(Set.of(1L))
            .build();
    actor.setId(ACTOR_ID);
    Mockito.lenient()
        .when(platformUserRepository.findByEmail(ACTOR_EMAIL))
        .thenReturn(Optional.of(actor));
  }

  // ==================== 現況の読み出し ====================

  @Test
  @DisplayName("有効な帰属のある受注は帰属済みとして会員コードと成立の機構を返すこと")
  void getReportsTheActiveAttribution() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions(activeAttribution());

    OrderAttributionResponse response = service.currentAttribution(ORDER_ID);

    assertThat(response.attributed()).isTrue();
    assertThat(response.memberCode()).isEqualTo(MEMBER_CODE);
    assertThat(response.source()).isEqualTo("COMPLETION");
    assertThat(response.invalidatedReason()).isNull();
  }

  @Test
  @DisplayName("無効化済みの受注は未帰属として、直近の記録の理由を添えて返すこと")
  void getReportsTheInvalidatedAttribution() {
    givenOrder(OrderStatus.COMPLETED);
    OrderAttribution invalidated = activeAttribution();
    invalidated.invalidate(REASON, ACTOR_ID, OffsetDateTime.now());
    givenAttributions(invalidated);

    OrderAttributionResponse response = service.currentAttribution(ORDER_ID);

    assertThat(response.attributed()).isFalse();
    // 誰の来店として記録されていたかは訂正後も読めなければならない（訂正の妥当性を店舗が確かめる材料）
    assertThat(response.memberCode()).isEqualTo(MEMBER_CODE);
    assertThat(response.invalidatedReason()).isEqualTo(REASON);
    assertThat(response.invalidatedAt()).isNotNull();
  }

  @Test
  @DisplayName("帰属記録の無い受注は未帰属として、会員側の項目を持たずに返すこと")
  void getReportsNoAttribution() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions();

    OrderAttributionResponse response = service.currentAttribution(ORDER_ID);

    assertThat(response.attributed()).isFalse();
    assertThat(response.memberCode()).isNull();
    assertThat(response.source()).isNull();
  }

  @Test
  @DisplayName("他店舗の受注（storeFilter で引けない）は帰属記録に触れる前に 404 になること")
  void getRejectsAnOrderOutsideTheStore() {
    Mockito.when(orderRepository.findScopedById(ORDER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.currentAttribution(ORDER_ID))
        .isInstanceOf(NotFoundException.class);
    // 帰属記録は platform 帰属で店舗行分離機構に載らない。受注を引けない時点で終えないと他店舗の会員コードが漏れる
    Mockito.verifyNoInteractions(orderAttributionRepository);
  }

  // ==================== 無効化 ====================

  @Test
  @DisplayName("無効化は理由・実行者・時刻を記録し、記録は残したまま未帰属へ戻すこと")
  void invalidateRecordsTheReasonAndActor() {
    givenOrder(OrderStatus.COMPLETED);
    OrderAttribution attribution = activeAttribution();
    givenAttributions(attribution);
    Mockito.when(orderAttributionRepository.save(attribution)).thenReturn(attribution);

    OrderAttributionResponse response = service.invalidate(ORDER_ID, request(REASON), ACTOR_EMAIL);

    assertThat(attribution.getStatus()).isEqualTo(OrderAttributionStatus.INVALIDATED);
    assertThat(attribution.getInvalidatedReason()).isEqualTo(REASON);
    assertThat(attribution.getInvalidatedBy()).isEqualTo(ACTOR_ID);
    assertThat(attribution.getInvalidatedAt()).isNotNull();
    assertThat(response.attributed()).isFalse();
  }

  @Test
  @DisplayName("帰属していない受注の無効化は拒まれること")
  void invalidateRejectsAnUnattributedOrder() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions();

    assertThatThrownBy(() -> service.invalidate(ORDER_ID, request(REASON), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("会員へ帰属していません");
  }

  @Test
  @DisplayName("無効化済みの帰属は二度目の無効化を受け付けないこと")
  void invalidateRejectsAnAlreadyInvalidatedAttribution() {
    givenOrder(OrderStatus.COMPLETED);
    OrderAttribution invalidated = activeAttribution();
    invalidated.invalidate("初回の理由", ACTOR_ID, OffsetDateTime.now());
    givenAttributions(invalidated);

    assertThatThrownBy(() -> service.invalidate(ORDER_ID, request(REASON), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class);
    assertThat(invalidated.getInvalidatedReason()).isEqualTo("初回の理由");
  }

  @Test
  @DisplayName("失効した認証セッションの無効化は実行者不明のまま通らないこと")
  void invalidateRejectsAnUnresolvableActor() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions(activeAttribution());
    Mockito.when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.invalidate(ORDER_ID, request(REASON), ACTOR_EMAIL))
        .isInstanceOf(StaleSessionException.class);
  }

  @Test
  @DisplayName("他店舗の受注の無効化は帰属記録に触れる前に 404 になること")
  void invalidateRejectsAnOrderOutsideTheStore() {
    Mockito.when(orderRepository.findScopedById(ORDER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.invalidate(ORDER_ID, request(REASON), ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class);
    Mockito.verifyNoInteractions(orderAttributionRepository);
  }

  // ==================== 再発行 ====================

  @Test
  @DisplayName("無効化された受注へ再発行でき、期限は再発行から 90 日で数え直されること")
  void reissueRestartsTheClaimWindow() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions(invalidatedAttribution());
    givenTokens();
    Mockito.when(receiptTokenGenerator.generate())
        .thenReturn(new ReceiptTokenGenerator.GeneratedToken("raw", "digest"));

    OrderReceiptTokenResponse response = service.reissueReceiptToken(ORDER_ID);

    assertThat(response.receiptToken()).isEqualTo("raw");
    Mockito.verify(orderReceiptTokenRepository).save(savedToken.capture());
    OrderReceiptToken token = savedToken.getValue();
    // 誤帰属の期間が本人の申領窓を食い潰したまま原期限を残すと、訂正が形骸化する（ADR 0009 の意図的な例外）
    assertThat(token.getExpiresAt())
        .isEqualTo(token.getIssuedAt().plus(OrderReceiptToken.VALIDITY));
    assertThat(token.getIssuedAt()).isAfter(OffsetDateTime.now().minusMinutes(1));
    // 保存されるのはダイジェストだけで、生値は応答にしか現れない
    assertThat(token.getTokenDigest()).isEqualTo("digest");
  }

  @Test
  @DisplayName("完了時に会員へ帰属した受注の再発行では、完了時に付与された額を予定額として引き継ぐこと")
  void reissueCarriesForwardTheGrantFixedAtCompletion() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions(invalidatedAttribution());
    // 完了時に会員へ帰属した受注には伝票トークンが発行されないため、確定額は受注の付与実績にしか無い
    givenTokens();
    Mockito.when(receiptTokenGenerator.generate())
        .thenReturn(new ReceiptTokenGenerator.GeneratedToken("raw", "digest"));

    service.reissueReceiptToken(ORDER_ID);

    Mockito.verify(orderReceiptTokenRepository).save(savedToken.capture());
    assertThat(savedToken.getValue().getPlannedPoints()).isEqualTo(COMPLETION_GRANT);
  }

  @Test
  @DisplayName("申領済みの伝票がある受注の再発行では、その伝票の予定額を引き継ぐこと（受注の付与実績は 0 のため）")
  void reissueCarriesForwardThePriorTokensPlannedPoints() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions(invalidatedAttribution());
    OrderReceiptToken claimed =
        OrderReceiptToken.issueFor(
            ORDER_ID, "old-digest", TOKEN_PLANNED_POINTS, OffsetDateTime.now());
    claimed.claim(OffsetDateTime.now());
    givenTokens(claimed);
    Mockito.when(receiptTokenGenerator.generate())
        .thenReturn(new ReceiptTokenGenerator.GeneratedToken("raw", "digest"));

    service.reissueReceiptToken(ORDER_ID);

    Mockito.verify(orderReceiptTokenRepository).save(savedToken.capture());
    // 受注側の付与実績（非会員完了なので 0）を読むと、正しい本人が取り戻せるポイントが消える
    assertThat(savedToken.getValue().getPlannedPoints()).isEqualTo(TOKEN_PLANNED_POINTS);
  }

  @Test
  @DisplayName("有効な帰属のある受注へは再発行しないこと（先に無効化が要る）")
  void reissueRejectsAnAttributedOrder() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions(activeAttribution());

    assertThatThrownBy(() -> service.reissueReceiptToken(ORDER_ID))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("先に帰属を無効化");
    Mockito.verify(orderReceiptTokenRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  @DisplayName("会員へ帰属したことのない受注へは再発行しないこと（訂正の経路であって発行の経路ではない）")
  void reissueRejectsAnOrderThatWasNeverAttributed() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions();

    assertThatThrownBy(() -> service.reissueReceiptToken(ORDER_ID))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("帰属したことがありません");
    Mockito.verify(orderReceiptTokenRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  @DisplayName("完了していない受注へは再発行しないこと")
  void reissueRejectsAnIncompleteOrder() {
    givenOrder(OrderStatus.CONFIRMED);

    assertThatThrownBy(() -> service.reissueReceiptToken(ORDER_ID))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("完了していない");
  }

  @Test
  @DisplayName("申領できる伝票が既にある受注へは再発行しないこと（生きた伝票が 2 本並ぶと申領の応答が同形でなくなる）")
  void reissueRejectsWhenAClaimableTokenAlreadyExists() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions(invalidatedAttribution());
    givenTokens(
        OrderReceiptToken.issueFor(
            ORDER_ID, "live-digest", TOKEN_PLANNED_POINTS, OffsetDateTime.now()));

    assertThatThrownBy(() -> service.reissueReceiptToken(ORDER_ID))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("申領できる伝票が既にあります");
    Mockito.verify(orderReceiptTokenRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  @DisplayName("期限切れの伝票は再発行を妨げないこと（申領できない伝票に窓を塞がせない）")
  void reissueIgnoresAnExpiredToken() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions(invalidatedAttribution());
    givenTokens(
        OrderReceiptToken.issueFor(
            ORDER_ID,
            "expired-digest",
            TOKEN_PLANNED_POINTS,
            OffsetDateTime.now().minus(OrderReceiptToken.VALIDITY).minusDays(1)));
    Mockito.when(receiptTokenGenerator.generate())
        .thenReturn(new ReceiptTokenGenerator.GeneratedToken("raw", "digest"));

    assertThat(service.reissueReceiptToken(ORDER_ID).receiptToken()).isEqualTo("raw");
  }

  @Test
  @DisplayName("再発行は受注を押さえてから門を判じること（並行する再発行が同じ「伝票が無い」を観測しないため）")
  void reissueLocksTheOrderBeforeCheckingItsGuards() {
    givenOrder(OrderStatus.COMPLETED);
    givenAttributions(invalidatedAttribution());
    givenTokens();
    Mockito.when(receiptTokenGenerator.generate())
        .thenReturn(new ReceiptTokenGenerator.GeneratedToken("raw", "digest"));

    service.reissueReceiptToken(ORDER_ID);

    // 実際の競合は統合テストが踏む。ここが固定するのは「ロックを取る読み口を通っている」配線そのもので、
    // 素の読み口へ差し替わると門が check-then-act に戻る
    Mockito.verify(orderRepository).findScopedByIdForUpdate(ORDER_ID);
    Mockito.verify(orderRepository, Mockito.never()).findScopedById(ORDER_ID);
  }

  @Test
  @DisplayName("他店舗の受注の再発行は帰属記録に触れる前に 404 になること")
  void reissueRejectsAnOrderOutsideTheStore() {
    Mockito.when(orderRepository.findScopedByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.reissueReceiptToken(ORDER_ID))
        .isInstanceOf(NotFoundException.class);
    Mockito.verifyNoInteractions(orderAttributionRepository, orderReceiptTokenRepository);
  }

  // ==================== 用意 ====================

  private static OrderAttributionInvalidationRequest request(String reason) {
    OrderAttributionInvalidationRequest request = new OrderAttributionInvalidationRequest();
    request.setReason(reason);
    return request;
  }

  private void givenOrder(OrderStatus status) {
    Order order =
        Order.builder()
            .businessDate(LocalDate.parse("2026-08-10"))
            .status(status)
            .autoGrantPoints(COMPLETION_GRANT)
            .build();
    order.setId(ORDER_ID);
    order.setStoreId(1L);
    Mockito.lenient().when(orderRepository.findScopedById(ORDER_ID)).thenReturn(Optional.of(order));
    Mockito.lenient()
        .when(orderRepository.findScopedByIdForUpdate(ORDER_ID))
        .thenReturn(Optional.of(order));
  }

  private void givenAttributions(OrderAttribution... rows) {
    Mockito.lenient()
        .when(orderAttributionRepository.findByOrderIdOrderByIdDesc(ORDER_ID))
        .thenReturn(List.of(rows));
    Mockito.lenient()
        .when(orderAttributionRepository.findFirstByOrderIdOrderByIdDesc(ORDER_ID))
        .thenReturn(rows.length == 0 ? Optional.empty() : Optional.of(rows[0]));
  }

  private void givenTokens(OrderReceiptToken... rows) {
    Mockito.when(orderReceiptTokenRepository.findByOrderIdOrderByIdDesc(ORDER_ID))
        .thenReturn(List.of(rows));
  }

  private static OrderAttribution activeAttribution() {
    return OrderAttribution.onCompletion(
        ORDER_ID, MEMBER_ID, MEMBER_CODE, OffsetDateTime.parse("2026-08-10T19:00:00Z"));
  }

  private static OrderAttribution invalidatedAttribution() {
    OrderAttribution attribution = activeAttribution();
    attribution.invalidate(REASON, ACTOR_ID, OffsetDateTime.parse("2026-08-12T10:00:00Z"));
    return attribution;
  }
}
