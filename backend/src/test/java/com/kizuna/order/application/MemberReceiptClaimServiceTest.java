package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.api.dto.MemberReceiptClaimResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderReceiptTokenStatus;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberReceiptClaimServiceTest {

  private static final String EMAIL = "member@example.com";
  private static final long PLATFORM_USER_ID = 10L;
  private static final long MEMBER_ID = 7L;
  private static final String MEMBER_CODE = "123456789012";
  private static final long STORE_ID = 3L;
  private static final String ORDER_ID = "o1";
  private static final String RAW_TOKEN = "raw-token";
  private static final String DIGEST = "digest-of-raw-token";
  private static final int PLANNED_POINTS = 120;

  @Mock private OrderReceiptTokenRepository orderReceiptTokenRepository;
  @Mock private OrderRepository orderRepository;
  @Mock private ReceiptTokenGenerator receiptTokenGenerator;
  @Mock private PointLedgerService pointLedgerService;
  private final PlatformUserRepository platformUserRepository =
      Mockito.mock(PlatformUserRepository.class);

  @Spy
  private ActorIdentityService actorIdentityService =
      new ActorIdentityService(platformUserRepository);

  @Mock private MemberLookupService memberLookupService;
  @Mock private AttributionMaterializer materializer;

  @InjectMocks private MemberReceiptClaimService service;

  @BeforeEach
  void resolveAuthenticatedMember() {
    PlatformUser user =
        PlatformUser.builder()
            .email(EMAIL)
            .password("encoded")
            .displayName("会員 花子")
            .enabled(true)
            .userType(UserType.MEMBER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(PLATFORM_USER_ID);
    Mockito.lenient().when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    Mockito.lenient()
        .when(memberLookupService.findByPlatformUserId(PLATFORM_USER_ID))
        .thenReturn(Optional.of(new MemberLookup(MEMBER_ID, MEMBER_CODE)));
    Mockito.lenient().when(receiptTokenGenerator.digest(RAW_TOKEN)).thenReturn(DIGEST);
  }

  @Test
  @DisplayName("有効なトークンの申領で、帰属記録（RECEIPT_TOKEN）と発行時の確定額の記帳が成立すること")
  void claimRecordsTheAttributionAndBooksThePlannedPoints() {
    OrderReceiptToken token = Mockito.spy(issuedToken(OffsetDateTime.now()));
    givenToken(token);

    MemberReceiptClaimResponse response = service.claim(EMAIL, RAW_TOKEN);

    assertThat(response.grantedPoints()).isEqualTo(PLANNED_POINTS);
    assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.CLAIMED);
    Mockito.verify(orderReceiptTokenRepository).save(token);
    var occurredAt = ArgumentCaptor.forClass(OffsetDateTime.class);
    Mockito.verify(materializer)
        .materialize(
            Mockito.eq(MEMBER_ID),
            Mockito.eq(MEMBER_CODE),
            Mockito.eq(ORDER_ID),
            Mockito.eq(STORE_ID),
            Mockito.eq(ORDER_BUSINESS_DATE),
            occurredAt.capture(),
            Mockito.eq(PLATFORM_USER_ID),
            Mockito.eq(new AttributionMaterializer.ReceiptClaim(PLANNED_POINTS)));
    Mockito.verify(token).claim(occurredAt.getValue());
  }

  @ParameterizedTest
  @CsvSource({"0, 500", "120, 620"})
  @DisplayName("来店特典が当たった申領は、伝票の予定額と特典の合計を返すこと")
  void claimReportsThePlannedPointsAndTheBenefitTogether(int plannedPoints, long expectedTotal) {
    // 応答は「この申領で記帳したポイント」。予定額だけを返すと、特典だけが付いた 0 円完了の伝票が
    // 画面で「付与はありません」になり、成立した記帳が利用者へ嘘になる。
    givenToken(OrderReceiptToken.issueFor(ORDER_ID, DIGEST, plannedPoints, OffsetDateTime.now()));
    Mockito.doReturn(new AttributionMaterializer.Result(plannedPoints, 500))
        .when(materializer)
        .materialize(
            Mockito.anyLong(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any());

    assertThat(service.claim(EMAIL, RAW_TOKEN).grantedPoints()).isEqualTo(expectedTotal);
  }

  @Test
  @DisplayName("付与予定額 0 の伝票でも帰属記録は生まれ、記帳額は 0 で渡ること")
  void claimOfAZeroPointReceiptStillRecordsTheVisit() {
    // 申領の効果は来店の可視化に閉じる。帰属は付与の有無と独立している
    givenToken(OrderReceiptToken.issueFor(ORDER_ID, DIGEST, 0, OffsetDateTime.now()));
    assertThat(service.claim(EMAIL, RAW_TOKEN).grantedPoints()).isZero();
    Mockito.verify(materializer)
        .materialize(
            Mockito.eq(MEMBER_ID),
            Mockito.eq(MEMBER_CODE),
            Mockito.eq(ORDER_ID),
            Mockito.eq(STORE_ID),
            Mockito.eq(ORDER_BUSINESS_DATE),
            Mockito.any(OffsetDateTime.class),
            Mockito.eq(PLATFORM_USER_ID),
            Mockito.eq(new AttributionMaterializer.ReceiptClaim(0)));
  }

  @Test
  @DisplayName("不在・期限切れ・使用済みのトークンが同一の文言で撥ねられ、何も書かれないこと")
  void everyUnusableTokenFailsWithTheSameMessage() {
    // 応答を撃ち分けると、受注の存在と完了状態を応答の違いから辿れてしまう
    Mockito.when(orderReceiptTokenRepository.findByTokenDigest(DIGEST))
        .thenReturn(Optional.empty());
    Throwable missing = catchThrowable(() -> service.claim(EMAIL, RAW_TOKEN));

    OrderReceiptToken expired = issuedToken(OffsetDateTime.now().minusDays(91));
    Mockito.when(orderReceiptTokenRepository.findByTokenDigest(DIGEST))
        .thenReturn(Optional.of(expired));
    Throwable outOfDate = catchThrowable(() -> service.claim(EMAIL, RAW_TOKEN));

    OrderReceiptToken used = issuedToken(OffsetDateTime.now());
    used.claim(OffsetDateTime.now());
    Mockito.when(orderReceiptTokenRepository.findByTokenDigest(DIGEST))
        .thenReturn(Optional.of(used));
    Throwable alreadyClaimed = catchThrowable(() -> service.claim(EMAIL, RAW_TOKEN));

    assertThat(missing).isInstanceOf(NotFoundException.class);
    assertThat(outOfDate).isInstanceOf(NotFoundException.class).hasMessage(missing.getMessage());
    assertThat(alreadyClaimed)
        .isInstanceOf(NotFoundException.class)
        .hasMessage(missing.getMessage());
    assertThat(expired.getStatus())
        .as("撥ねた伝票は未申領のまま残ること")
        .isEqualTo(OrderReceiptTokenStatus.ISSUED);
    Mockito.verifyNoInteractions(materializer);
    Mockito.verifyNoInteractions(pointLedgerService);
  }

  @Test
  @DisplayName("巻き戻し済みの受注の伝票も、同じ文言で撥ねられて何も書かれないこと")
  void refusesTheReceiptOfARolledBackOrder() {
    // 拒否の材料は操作記録であって台帳の仕訳ではない。付与予定額は完了時点で固定され再発行でも
    // 計算し直されないため、記録で拦めなければ申領は原額の付与を積み直せる。
    OrderReceiptToken token = issuedToken(OffsetDateTime.now());
    // 受注は読まない。門はトークンの照合の直後で、発生店舗を解く前に閉じる。
    Mockito.when(orderReceiptTokenRepository.findByTokenDigest(DIGEST))
        .thenReturn(Optional.of(token));
    Mockito.when(pointLedgerService.isRolledBack(ORDER_ID)).thenReturn(true);

    Throwable rolledBack = catchThrowable(() -> service.claim(EMAIL, RAW_TOKEN));

    Mockito.when(orderReceiptTokenRepository.findByTokenDigest(DIGEST))
        .thenReturn(Optional.empty());
    Throwable missing = catchThrowable(() -> service.claim(EMAIL, RAW_TOKEN));

    assertThat(rolledBack).isInstanceOf(NotFoundException.class).hasMessage(missing.getMessage());
    assertThat(token.getStatus()).as("撥ねた伝票は未申領のまま残ること").isEqualTo(OrderReceiptTokenStatus.ISSUED);
    Mockito.verifyNoInteractions(materializer);
  }

  @Test
  @DisplayName("会員でない主体には申領させないこと")
  void refusesANonMemberPrincipal() {
    Mockito.when(memberLookupService.findByPlatformUserId(PLATFORM_USER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.claim(EMAIL, RAW_TOKEN))
        .isInstanceOf(StaleSessionException.class);
    Mockito.verifyNoInteractions(materializer, pointLedgerService);
  }

  @Test
  @DisplayName("認証主体のユーザーが存在しない場合は 401 系例外")
  void refusesAStalePrincipal() {
    Mockito.when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.claim(EMAIL, RAW_TOKEN))
        .isInstanceOf(StaleSessionException.class);
    Mockito.verifyNoInteractions(materializer, pointLedgerService);
  }

  private static OrderReceiptToken issuedToken(OffsetDateTime issuedAt) {
    return OrderReceiptToken.issueFor(ORDER_ID, DIGEST, PLANNED_POINTS, issuedAt);
  }

  /** 引ける伝票と、その受注（発生店舗の出どころ）を用意する。 */
  private static final LocalDate ORDER_BUSINESS_DATE = LocalDate.parse("2026-08-10");

  private void givenToken(OrderReceiptToken token) {
    Mockito.when(orderReceiptTokenRepository.findByTokenDigest(DIGEST))
        .thenReturn(Optional.of(token));
    Mockito.lenient()
        .when(
            materializer.materialize(
                Mockito.anyLong(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenAnswer(
            invocation -> {
              assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.CLAIMED);
              AttributionMaterializer.ReceiptClaim trigger = invocation.getArgument(7);
              return new AttributionMaterializer.Result(trigger.plannedPoints(), 0);
            });
    Order order =
        Order.builder()
            .businessDate(ORDER_BUSINESS_DATE)
            .pax(2)
            .status(OrderStatus.COMPLETED)
            .build();
    order.setId(ORDER_ID);
    order.setStoreId(STORE_ID);
    Mockito.when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
  }
}
