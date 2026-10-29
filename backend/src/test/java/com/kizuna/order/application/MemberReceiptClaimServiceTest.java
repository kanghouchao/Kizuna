package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
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
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberReceiptClaimServiceTest {

  private static final String EMAIL = "member@example.com";
  private static final long USER_ID = 10L;
  private static final long MEMBER_ID = 7L;
  private static final String MEMBER_CODE = "123456789012";
  private static final long STORE_ID = 3L;
  private static final String ORDER_ID = "o1";
  private static final String RAW_TOKEN = "raw-token";
  private static final String DIGEST = "digest-of-raw-token";
  private static final int PLANNED_POINTS = 120;
  private static final long ATTRIBUTION_ID = 88L;

  @Mock private OrderReceiptTokenRepository orderReceiptTokenRepository;
  @Mock private OrderRepository orderRepository;
  @Mock private ReceiptTokenGenerator receiptTokenGenerator;
  @Mock private PointLedgerService pointLedgerService;
  private final UserRepository userRepository = Mockito.mock(UserRepository.class);

  @Spy private ActorIdentityService actorIdentityService = new ActorIdentityService(userRepository);

  @Mock private MemberLookupService memberLookupService;
  @Mock private AttributionMaterializer materializer;

  private final List<AuditEvent> events = new ArrayList<>();
  private MemberReceiptClaimService service;

  @BeforeEach
  void resolveAuthenticatedMember() {
    User user =
        User.builder()
            .email(EMAIL)
            .password("encoded")
            .displayName("会員 花子")
            .enabled(true)
            .userType(UserType.MEMBER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(USER_ID);
    Mockito.lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    Mockito.lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    var auditRepository = Mockito.mock(AuditEventRepository.class);
    Mockito.lenient()
        .when(auditRepository.saveAndFlush(Mockito.any(AuditEvent.class)))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              events.add(event);
              return event;
            });
    service =
        new MemberReceiptClaimService(
            orderReceiptTokenRepository,
            orderRepository,
            receiptTokenGenerator,
            pointLedgerService,
            actorIdentityService,
            memberLookupService,
            materializer,
            new BusinessAudit(userRepository, new AuditWriter(auditRepository, Clock.systemUTC())));
    Mockito.lenient()
        .when(memberLookupService.findByUserId(USER_ID))
        .thenReturn(Optional.of(new MemberLookup(MEMBER_ID, MEMBER_CODE)));
    Mockito.lenient().when(receiptTokenGenerator.digest(RAW_TOKEN)).thenReturn(DIGEST);
  }

  @Test
  @DisplayName("有効なトークンの申領で、帰属記録（RECEIPT_TOKEN）と発行時の確定額の記帳が成立すること")
  void claimRecordsTheAttributionAndBooksThePlannedPoints() {
    OrderReceiptToken token = Mockito.spy(issuedToken(OffsetDateTime.now()));
    givenToken(token);

    MemberReceiptClaimResult response = service.claim(EMAIL, RAW_TOKEN);

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
            Mockito.eq(USER_ID),
            Mockito.eq(new AttributionMaterializer.ReceiptClaim(PLANNED_POINTS)));
    Mockito.verify(token).claim(occurredAt.getValue());
  }

  @Test
  @DisplayName("申領の成功は RECEIPT_CLAIMED を 1 行だけ、帰属記録を source にして秘密なしで残すこと")
  void claimAuditsOnceWithoutTokenOrEmail() {
    givenToken(OrderReceiptToken.issueFor(ORDER_ID, DIGEST, PLANNED_POINTS, OffsetDateTime.now()));
    Mockito.doReturn(new AttributionMaterializer.Result(PLANNED_POINTS, 500, attribution(), true))
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

    service.claim(EMAIL, RAW_TOKEN);

    assertThat(events).hasSize(1);
    AuditEvent event = events.get(0);
    assertThat(event.getAction()).isEqualTo("RECEIPT_CLAIMED");
    assertThat(event.getTargetType()).isEqualTo("ORDER");
    assertThat(event.getTargetId()).isEqualTo(ORDER_ID);
    assertThat(event.getSourceType()).isEqualTo("ORDER_ATTRIBUTION");
    assertThat(event.getSourceId()).isEqualTo("88");
    assertThat(event.getActorId()).isEqualTo(USER_ID);
    assertThat(event.getStoreId()).isEqualTo(STORE_ID);
    assertThat(event.getBeforeValues()).containsEntry("exists", "false");
    assertThat(event.getAfterValues())
        .containsEntry("exists", "true")
        .containsEntry("status", "ACTIVE")
        .containsEntry("version", "0")
        .containsEntry("member_id", "7")
        .containsEntry("granted_points", "620")
        .containsEntry("rank_changed", "true");
    String everything = event.getBeforeValues().toString() + event.getAfterValues();
    assertThat(everything).doesNotContain(RAW_TOKEN, DIGEST, EMAIL);
  }

  @ParameterizedTest
  @CsvSource({"0, 500", "120, 620"})
  @DisplayName("来店特典が当たった申領は、伝票の予定額と特典の合計を返すこと")
  void claimReportsThePlannedPointsAndTheBenefitTogether(int plannedPoints, long expectedTotal) {
    // 応答は「この申領で記帳したポイント」。予定額だけを返すと、特典だけが付いた 0 円完了の伝票が
    // 画面で「付与はありません」になり、成立した記帳が利用者へ嘘になる。
    givenToken(OrderReceiptToken.issueFor(ORDER_ID, DIGEST, plannedPoints, OffsetDateTime.now()));
    Mockito.doReturn(new AttributionMaterializer.Result(plannedPoints, 500, attribution(), false))
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
            Mockito.eq(USER_ID),
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
    assertThat(events).isEmpty();
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
    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName("無効化済み受注の伝票は不在と同じ応答で拒否し、帰属や付与を物化しないこと")
  void refusesTheReceiptOfAnInvalidatedOrder() {
    OrderReceiptToken token = issuedToken(OffsetDateTime.now());
    givenToken(token);
    Mockito.when(orderRepository.findByReceiptTokenDigest(DIGEST))
        .thenReturn(Optional.of(Order.builder().completionInvalidated(true).build()));

    Throwable invalidated = catchThrowable(() -> service.claim(EMAIL, RAW_TOKEN));
    Mockito.when(orderReceiptTokenRepository.findByTokenDigest(DIGEST))
        .thenReturn(Optional.empty());
    Throwable missing = catchThrowable(() -> service.claim(EMAIL, RAW_TOKEN));

    assertThat(invalidated).isInstanceOf(NotFoundException.class).hasMessage(missing.getMessage());
    assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.ISSUED);
    Mockito.verify(orderReceiptTokenRepository, Mockito.never()).save(Mockito.any());
    Mockito.verifyNoInteractions(materializer);
  }

  @Test
  @DisplayName("会員でない主体には申領させないこと")
  void refusesANonMemberPrincipal() {
    Mockito.when(memberLookupService.findByUserId(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.claim(EMAIL, RAW_TOKEN))
        .isInstanceOf(StaleSessionException.class);
    Mockito.verifyNoInteractions(materializer, pointLedgerService);
  }

  @Test
  @DisplayName("認証主体のユーザーが存在しない場合は 401 系例外")
  void refusesAStalePrincipal() {
    Mockito.when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.claim(EMAIL, RAW_TOKEN))
        .isInstanceOf(StaleSessionException.class);
    Mockito.verifyNoInteractions(materializer, pointLedgerService);
  }

  private static OrderAttribution attribution() {
    var row =
        OrderAttribution.onReceiptClaim(ORDER_ID, MEMBER_ID, MEMBER_CODE, OffsetDateTime.now());
    row.setId(ATTRIBUTION_ID);
    ReflectionTestUtils.setField(row, "version", 0L);
    return row;
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
              return new AttributionMaterializer.Result(
                  trigger.plannedPoints(), 0, attribution(), false);
            });
    Order order =
        Order.builder()
            .businessDate(ORDER_BUSINESS_DATE)
            .pax(2)
            .status(OrderStatus.COMPLETED)
            .build();
    order.setId(ORDER_ID);
    order.setStoreId(STORE_ID);
    Mockito.when(orderRepository.findByReceiptTokenDigest(DIGEST)).thenReturn(Optional.of(order));
  }
}
