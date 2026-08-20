package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.order.api.dto.GuestOrderApplicationCreateRequest;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 公開店面からのゲスト予約申請の単体テスト。 */
@ExtendWith(MockitoExtension.class)
class GuestOrderApplicationServiceTest {

  private static final Long STORE_ID = 1L;
  private static final LocalDate VISIT_DATE = LocalDate.of(2026, 8, 25);

  @Mock private OrderApplicationRepository orderApplicationRepository;
  @Mock private OrderApplicationIntake orderApplicationIntake;
  @Mock private StoreContext storeContext;

  @InjectMocks private GuestOrderApplicationService service;

  @Captor private ArgumentCaptor<OrderApplication> applicationCaptor;

  private static GuestOrderApplicationCreateRequest request() {
    GuestOrderApplicationCreateRequest request = new GuestOrderApplicationCreateRequest();
    request.setBusinessDate(VISIT_DATE);
    request.setPax(2);
    request.setContactName("ゲスト花子");
    request.setContactPhoneNumber("09000000000");
    return request;
  }

  @Test
  @DisplayName("ゲスト申請が PENDING の予約申請として起き、連絡先が残ること")
  void requestCreatesAPendingApplicationCarryingTheContact() {
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderApplicationRepository.save(any(OrderApplication.class)))
        .thenAnswer(
            invocation -> {
              OrderApplication saved = invocation.getArgument(0);
              saved.setId("app-1");
              return saved;
            });

    assertThat(service.request(request()).id()).isEqualTo("app-1");

    verify(orderApplicationRepository).save(applicationCaptor.capture());
    OrderApplication created = applicationCaptor.getValue();
    assertThat(created.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    assertThat(created.getContactName()).isEqualTo("ゲスト花子");
    assertThat(created.getContactPhoneNumber()).isEqualTo("09000000000");
    assertThat(created.isGuest()).as("会員コードを持たない申請がゲスト申請であること").isTrue();
    assertThat(created.getRequesterMemberId()).isNull();
  }

  @Test
  @DisplayName("希望内容が受け付けられない申請では行が起きないこと")
  void requestDoesNotWriteWhenTheRequestedVisitIsRejected() {
    // 匿名の入口が会員の入口より緩ければ、会員が撥ねられる希望内容を匿名で通せてしまう
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    doThrow(new ServiceException("過去の日付は申請できません"))
        .when(orderApplicationIntake)
        .validateRequestedVisit(STORE_ID, VISIT_DATE, null);

    assertThatThrownBy(() -> service.request(request()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("過去の日付");

    verify(orderApplicationRepository, never()).save(any());
  }

  @Test
  @DisplayName("店舗は文脈から取り、申請本体には自称させないこと")
  void requestTakesTheStoreFromTheContext() {
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderApplicationRepository.save(any(OrderApplication.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.request(request());

    verify(orderApplicationIntake).validateRequestedVisit(STORE_ID, VISIT_DATE, null);
  }
}
