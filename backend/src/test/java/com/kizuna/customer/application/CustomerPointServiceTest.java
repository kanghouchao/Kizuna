package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import com.kizuna.customer.api.dto.CustomerPointAdjustmentRequest;
import com.kizuna.customer.api.dto.CustomerPointBalanceResponse;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerPointServiceTest {

  private static final String CUSTOMER_ID = "c1";
  private static final String ACTOR_EMAIL = "tanaka@kizuna.test";
  private static final long ACTOR_ID = 42L;
  private static final long MEMBER_ID = 777L;
  private static final long STORE_ID = 3L;

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private StoreContext storeContext;

  @InjectMocks private CustomerPointService service;

  @Test
  @DisplayName("未紐づけの顧客では linked が偽で残高が載らないこと")
  void balanceOfUnlinkedCustomerCarriesNoAmount() {
    givenCustomerExists();
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());

    CustomerPointBalanceResponse response = service.balance(CUSTOMER_ID);

    assertThat(response.isLinked()).isFalse();
    assertThat(response.getBalance()).isNull();
    // 非会員には台帳そのものが無いため、台帳へは問い合わせない
    Mockito.verify(pointLedgerService, Mockito.never()).balance(anyLong());
  }

  @Test
  @DisplayName("会員 ID の欠けた紐づけは未紐づけと同じに扱われること")
  void linkWithoutMemberIdIsTreatedAsUnlinked() {
    givenCustomerExists();
    // 会員行が消えると FK の SET NULL で紐づけの会員 ID が欠落する。集約の構築時検証は会員 ID を
    // 必須にしており、この状態は読み込みでしか生じないためモックで作る。
    CustomerMemberLink detached = Mockito.mock(CustomerMemberLink.class);
    Mockito.when(detached.getMemberId()).thenReturn(null);
    givenActiveLink(detached);

    CustomerPointBalanceResponse response = service.balance(CUSTOMER_ID);

    assertThat(response.isLinked()).isFalse();
    assertThat(response.getBalance()).isNull();
    Mockito.verify(pointLedgerService, Mockito.never()).balance(anyLong());
  }

  @Test
  @DisplayName("紐づけ済みの顧客では台帳の残高がそのまま返ること")
  void balanceOfLinkedCustomerComesFromTheLedger() {
    givenCustomerExists();
    givenActiveLink(MEMBER_ID);
    Mockito.when(pointLedgerService.balance(MEMBER_ID)).thenReturn(1500);

    CustomerPointBalanceResponse response = service.balance(CUSTOMER_ID);

    assertThat(response.isLinked()).isTrue();
    assertThat(response.getBalance()).isEqualTo(1500);
  }

  @Test
  @DisplayName("存在しない顧客の残高照会は 404 相当で撥ねられること")
  void balanceOfUnknownCustomerIsNotFound() {
    Mockito.when(customerRepository.existsById(CUSTOMER_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.balance(CUSTOMER_ID)).isInstanceOf(NotFoundException.class);
    Mockito.verify(customerMemberLinkRepository, Mockito.never())
        .findByCustomerIdAndStatus(anyString(), any());
  }

  @Test
  @DisplayName("未紐づけの顧客は調整できず、台帳へ何も積まれないこと")
  void adjustingAnUnlinkedCustomerIsRefused() {
    givenCustomerExists();
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.adjust(CUSTOMER_ID, request(100, "誤記帳の訂正", null), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("会員に紐づいていない顧客");
    Mockito.verify(pointLedgerService, Mockito.never())
        .adjust(anyLong(), any(), anyInt(), anyString(), any(), any());
  }

  @Test
  @DisplayName("調整は操作中の店舗を発生店舗として台帳へ委譲され、調整後の残高が返ること")
  void adjustDelegatesToTheLedgerWithTheCurrentStore() {
    givenCustomerExists();
    givenActiveLink(MEMBER_ID);
    Mockito.when(storeContext.getStoreId()).thenReturn(STORE_ID);
    Mockito.when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(actor()));
    Mockito.when(pointLedgerService.balance(MEMBER_ID)).thenReturn(300);
    LocalDate expiresOn = LocalDate.of(2099, 12, 31);

    CustomerPointBalanceResponse response =
        service.adjust(CUSTOMER_ID, request(300, "来店記念の付与", expiresOn), ACTOR_EMAIL);

    Mockito.verify(pointLedgerService)
        .adjust(MEMBER_ID, STORE_ID, 300, "来店記念の付与", expiresOn, ACTOR_ID);
    assertThat(response.isLinked()).isTrue();
    assertThat(response.getBalance()).isEqualTo(300);
  }

  @Test
  @DisplayName("認証主体のユーザーが存在しない場合は 401 系例外で、台帳へ何も積まれないこと")
  void adjustFailsWhenActorMissing() {
    // 追記型の台帳では実行者 null が「機構が起こした仕訳」の形。失効した認証セッションによる手動調整を
    // その形で残さない
    givenCustomerExists();
    givenActiveLink(MEMBER_ID);
    Mockito.when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.adjust(CUSTOMER_ID, request(300, "来店記念の付与", null), ACTOR_EMAIL))
        .isInstanceOf(StaleSessionException.class);
    Mockito.verify(pointLedgerService, Mockito.never())
        .adjust(anyLong(), any(), anyInt(), anyString(), any(), any());
  }

  private void givenCustomerExists() {
    Mockito.when(customerRepository.existsById(CUSTOMER_ID)).thenReturn(true);
  }

  private void givenActiveLink(long memberId) {
    givenActiveLink(
        CustomerMemberLink.builder()
            .customerId(CUSTOMER_ID)
            .memberId(memberId)
            .memberCode("123456789012")
            .linkedBy(ACTOR_ID)
            .linkedAt(OffsetDateTime.now())
            .build());
  }

  private void givenActiveLink(CustomerMemberLink link) {
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.of(link));
  }

  private static PlatformUser actor() {
    PlatformUser user =
        PlatformUser.builder()
            .email(ACTOR_EMAIL)
            .password("encoded")
            .displayName("田中花子")
            .enabled(true)
            .userType(UserType.STAFF)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(STORE_ID))
            .roleIds(Set.of(1L))
            .build();
    user.setId(ACTOR_ID);
    return user;
  }

  private static CustomerPointAdjustmentRequest request(
      int delta, String reason, LocalDate expiresOn) {
    CustomerPointAdjustmentRequest request = new CustomerPointAdjustmentRequest();
    request.setDelta(delta);
    request.setReason(reason);
    request.setExpiresOn(expiresOn);
    return request;
  }
}
