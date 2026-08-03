package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.api.dto.MemberOrderCreateRequest;
import com.kizuna.order.domain.IllegalOrderStateTransitionException;
import com.kizuna.order.domain.MemberOrderView;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.ReceptionRoute;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MemberOrderServiceTest {

  private static final long STORE_ID = 1L;
  private static final long OTHER_STORE_ID = 2L;
  private static final long MEMBER_ID = 100L;
  private static final long PLATFORM_USER_ID = 50L;
  private static final String EMAIL = "member@kizuna.test";
  private static final String MEMBER_CODE = "123456789012";
  private static final String TIMEZONE = "Asia/Tokyo";

  @Mock OrderRepository orderRepository;
  @Mock CastRepository castRepository;
  @Mock CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock PlatformUserRepository platformUserRepository;
  @Mock MemberLookupService memberLookupService;
  @Mock ConfirmedShiftLookupService confirmedShiftLookupService;
  @Mock StoreExistenceCheck storeExistenceCheck;
  @Mock AppProperties appProperties;

  @InjectMocks MemberOrderService service;

  @Captor ArgumentCaptor<Order> orderCaptor;

  @BeforeEach
  void stubAuthenticatedMember() {
    lenient().when(appProperties.getTimezone()).thenReturn(TIMEZONE);
    PlatformUser user =
        PlatformUser.builder()
            .email(EMAIL)
            .password("pw")
            .displayName("会員")
            .enabled(true)
            .userType(UserType.MEMBER)
            .roleIds(Set.of())
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(PLATFORM_USER_ID);
    lenient().when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    lenient()
        .when(memberLookupService.findByPlatformUserId(PLATFORM_USER_ID))
        .thenReturn(Optional.of(new MemberLookup(MEMBER_ID, MEMBER_CODE)));
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of(TIMEZONE));
  }

  private MemberOrderCreateRequest requestFor(LocalDate date, String castId) {
    MemberOrderCreateRequest request = new MemberOrderCreateRequest();
    request.setStoreId(STORE_ID);
    request.setBusinessDate(date);
    request.setPax(2);
    request.setCastId(castId);
    return request;
  }

  private void stubSavedView() {
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    MemberOrderView view = mock(MemberOrderView.class);
    when(orderRepository.findMemberView(anyLong(), any())).thenReturn(Optional.of(view));
  }

  @Test
  @DisplayName("申請が Web 経路の CREATED 受注として、人数と申請会員を持って起きること")
  void requestCreatesWebOriginatedOrder() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);
    when(customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            STORE_ID, MEMBER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    stubSavedView();

    service.request(EMAIL, requestFor(today(), null));

    verify(orderRepository).save(orderCaptor.capture());
    Order saved = orderCaptor.getValue();
    assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);
    assertThat(saved.getReceptionRoute()).isEqualTo(ReceptionRoute.WEB);
    assertThat(saved.getRequesterMemberId()).isEqualTo(MEMBER_ID);
    assertThat(saved.getRequesterMemberCode()).isEqualTo(MEMBER_CODE);
    assertThat(saved.getPax()).isEqualTo(2);
    assertThat(saved.getStoreId()).as("店舗文脈が無い経路でも店舗が確定していること").isEqualTo(STORE_ID);
    assertThat(saved.getReceptionistId()).as("申請時点では受付担当がいないこと").isNull();
    assertThat(saved.getCustomerId()).as("紐づけが無ければ顧客は空のままであること").isNull();
  }

  @Test
  @DisplayName("申請先店舗に紐づけ済み顧客があれば結び付けること")
  void requestLinksCustomerWhenMemberLinkExists() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);
    CustomerMemberLink link =
        CustomerMemberLink.builder()
            .customerId("cust-1")
            .memberId(MEMBER_ID)
            .memberCode(MEMBER_CODE)
            .linkedBy(9L)
            .linkedAt(OffsetDateTime.now())
            .build();
    when(customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            STORE_ID, MEMBER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.of(link));
    stubSavedView();

    service.request(EMAIL, requestFor(today(), null));

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isEqualTo("cust-1");
  }

  @Test
  @DisplayName("存在しない店舗への申請を拒否すること")
  void requestRejectsUnknownStore() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.request(EMAIL, requestFor(today(), null)))
        .isInstanceOf(ServiceException.class);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  @DisplayName("過去日の申請を拒否すること")
  void requestRejectsPastDate() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);

    assertThatThrownBy(() -> service.request(EMAIL, requestFor(today().minusDays(1), null)))
        .isInstanceOf(ServiceException.class);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  @DisplayName("他店舗のキャストは指名できないこと")
  void requestRejectsCastOfAnotherStore() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);
    Cast otherStoreCast = Cast.builder().name("よその子").status("ACTIVE").build();
    otherStoreCast.setStoreId(OTHER_STORE_ID);
    when(castRepository.findById("cast-1")).thenReturn(Optional.of(otherStoreCast));

    assertThatThrownBy(() -> service.request(EMAIL, requestFor(today(), "cast-1")))
        .isInstanceOf(NotFoundException.class);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  @DisplayName("在籍していない（非 ACTIVE）キャストは指名できないこと")
  void requestRejectsInactiveCast() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);
    Cast retired = Cast.builder().name("退店した子").status("INACTIVE").build();
    retired.setStoreId(STORE_ID);
    retired.setId("cast-1");
    when(castRepository.findById("cast-1")).thenReturn(Optional.of(retired));

    assertThatThrownBy(() -> service.request(EMAIL, requestFor(today(), "cast-1")))
        .isInstanceOf(NotFoundException.class);
    // 候補に出さないだけでは、キャスト ID を直接送る要求を防げない
    verify(confirmedShiftLookupService, never()).hasConfirmedShift(anyLong(), anyString(), any());
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  @DisplayName("その日の確定シフトが無いキャストは指名できないこと")
  void requestRejectsCastWithoutConfirmedShift() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);
    Cast cast = Cast.builder().name("さくら").status("ACTIVE").build();
    cast.setStoreId(STORE_ID);
    cast.setId("cast-1");
    when(castRepository.findById("cast-1")).thenReturn(Optional.of(cast));
    when(confirmedShiftLookupService.hasConfirmedShift(STORE_ID, "cast-1", today()))
        .thenReturn(false);

    assertThatThrownBy(() -> service.request(EMAIL, requestFor(today(), "cast-1")))
        .isInstanceOf(ServiceException.class);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  @DisplayName("確定シフトのあるキャストは指名できること")
  void requestAcceptsNominationBackedByConfirmedShift() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);
    Cast cast = Cast.builder().name("さくら").status("ACTIVE").build();
    cast.setStoreId(STORE_ID);
    cast.setId("cast-1");
    when(castRepository.findById("cast-1")).thenReturn(Optional.of(cast));
    when(confirmedShiftLookupService.hasConfirmedShift(STORE_ID, "cast-1", today()))
        .thenReturn(true);
    when(customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            STORE_ID, MEMBER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    stubSavedView();

    service.request(EMAIL, requestFor(today(), "cast-1"));

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCastId()).isEqualTo("cast-1");
  }

  @Test
  @DisplayName("一覧は本人が申請した予約に限って引くこと")
  void listQueriesOwnRequestsOnly() {
    when(orderRepository.findMemberViews(anyLong(), any())).thenReturn(Page.empty());

    service.list(EMAIL, PageRequest.of(0, 20));

    verify(orderRepository).findMemberViews(eq(MEMBER_ID), any(Pageable.class));
  }

  @Test
  @DisplayName("未確定の予約を本人が取り下げられること")
  void cancelWithdrawsPendingRequest() {
    Order own = Order.builder().status(OrderStatus.CREATED).requesterMemberId(MEMBER_ID).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(own));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findMemberView(MEMBER_ID, "o1"))
        .thenReturn(Optional.of(mock(MemberOrderView.class)));

    service.cancel(EMAIL, "o1");

    assertThat(own.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  @DisplayName("確定後は本人でも取り下げられないこと")
  void cancelRejectsConfirmedOrder() {
    Order own = Order.builder().status(OrderStatus.CONFIRMED).requesterMemberId(MEMBER_ID).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(own));

    assertThatThrownBy(() -> service.cancel(EMAIL, "o1"))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThat(own.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  @DisplayName("他人の予約は見つからないものとして扱うこと（存在を漏らさない）")
  void cancelTreatsOthersOrderAsNotFound() {
    Order othersOrder = Order.builder().status(OrderStatus.CREATED).requesterMemberId(999L).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(othersOrder));

    assertThatThrownBy(() -> service.cancel(EMAIL, "o1")).isInstanceOf(NotFoundException.class);
    assertThat(othersOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  @DisplayName("店舗が起こした受注（申請者なし）は本人経路から取り下げられないこと")
  void cancelTreatsStoreOriginatedOrderAsNotFound() {
    Order storeOrder = Order.builder().status(OrderStatus.CREATED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(storeOrder));

    assertThatThrownBy(() -> service.cancel(EMAIL, "o1")).isInstanceOf(NotFoundException.class);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  @DisplayName("会員でない主体は本人経路を使えないこと")
  void rejectsNonMemberPrincipal() {
    when(memberLookupService.findByPlatformUserId(PLATFORM_USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.list(EMAIL, Pageable.unpaged()))
        .isInstanceOf(StaleSessionException.class);
    verify(orderRepository, never()).findMemberViews(anyLong(), any());
  }

  @Test
  @DisplayName("認証主体が実在しない場合は失効セッションとして扱うこと")
  void rejectsUnknownPrincipal() {
    when(platformUserRepository.findByEmail(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.list("ghost@kizuna.test", Pageable.unpaged()))
        .isInstanceOf(StaleSessionException.class);
  }
}
