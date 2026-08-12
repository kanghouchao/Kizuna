package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import com.kizuna.customer.api.dto.CustomerMemberLinkHistoryResponse;
import com.kizuna.customer.api.dto.CustomerMemberLinkResponse;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMemberLinkView;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CustomerMemberLinkServiceTest {

  private static final String ACTOR_EMAIL = "staff@kizuna.test";
  private static final String CUSTOMER_ID = "c1";
  private static final String MEMBER_CODE = "123456789012";
  private static final long ACTOR_ID = 42L;

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock private MemberLookupService memberLookupService;
  @Mock private PlatformUserRepository platformUserRepository;

  @InjectMocks private CustomerMemberLinkService service;

  private void givenActor() {
    PlatformUser actor =
        PlatformUser.builder()
            .email(ACTOR_EMAIL)
            .password("encoded")
            .displayName("山田次郎")
            .enabled(true)
            .userType(UserType.STAFF)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .roleIds(Set.of(1L))
            .build();
    actor.setId(ACTOR_ID);
    Mockito.when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(actor));
  }

  private void givenCustomerExists() {
    Mockito.when(customerRepository.existsById(CUSTOMER_ID)).thenReturn(true);
  }

  /** 紐づけを書き換える経路（紐づけ・変更・解除）が引く顧客。解決の前にこの行を押さえる。 */
  private void givenCustomerLocked() {
    Mockito.when(customerRepository.findByIdForUpdate(CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().build()));
  }

  private void givenMember(String memberCode, long memberId) {
    Mockito.when(memberLookupService.findByMemberCode(memberCode))
        .thenReturn(Optional.of(new MemberLookup(memberId, memberCode)));
  }

  private static CustomerMemberLink activeLink(long memberId, String memberCode) {
    return CustomerMemberLink.builder()
        .customerId(CUSTOMER_ID)
        .memberId(memberId)
        .memberCode(memberCode)
        .reason(LinkReason.MEMBER_CODE)
        .linkedBy(1L)
        .linkedAt(OffsetDateTime.parse("2026-07-01T10:00:00+09:00"))
        .build();
  }

  private void givenSaveReturnsArgument() {
    Mockito.when(customerMemberLinkRepository.saveAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("未紐づけの顧客に会員コードを紐づけると ACTIVE の区間が 1 件作られ、成立根拠が MEMBER_CODE で記録されること")
  void linkCreatesActiveLink() {
    givenActor();
    givenCustomerLocked();
    givenMember(MEMBER_CODE, 7L);
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    Mockito.when(customerMemberLinkRepository.existsByMemberIdAndStatus(7L, LinkStatus.ACTIVE))
        .thenReturn(false);
    givenSaveReturnsArgument();

    CustomerMemberLinkResponse response = service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL);

    assertThat(response.linked()).isTrue();
    assertThat(response.memberCode()).isEqualTo(MEMBER_CODE);
    ArgumentCaptor<CustomerMemberLink> saved = ArgumentCaptor.forClass(CustomerMemberLink.class);
    Mockito.verify(customerMemberLinkRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(LinkStatus.ACTIVE);
    assertThat(saved.getValue().getMemberId()).isEqualTo(7L);
    assertThat(saved.getValue().getReason()).isEqualTo(LinkReason.MEMBER_CODE);
    assertThat(saved.getValue().getLinkedBy()).isEqualTo(ACTOR_ID);
  }

  @Test
  @DisplayName("存在しない顧客への紐づけは 404（他店舗の顧客も同じ経路で 404 になる）")
  void linkFailsWhenCustomerMissing() {
    givenActor();
    Mockito.when(customerRepository.findByIdForUpdate(CUSTOMER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  @Test
  @DisplayName("存在しない会員コードでの紐づけは 404")
  void linkFailsWhenMemberCodeUnknown() {
    givenActor();
    givenCustomerLocked();
    Mockito.when(memberLookupService.findByMemberCode(MEMBER_CODE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("会員コード");
  }

  @Test
  @DisplayName("既に紐づいている会員をもう一度紐づけると 409")
  void linkFailsWhenSameMemberAlreadyLinked() {
    givenActor();
    givenCustomerLocked();
    givenMember(MEMBER_CODE, 7L);
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.of(activeLink(7L, MEMBER_CODE)));

    assertThatThrownBy(() -> service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("既にこの会員と紐づいています");
    Mockito.verify(customerMemberLinkRepository, Mockito.never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("別会員への変更は旧区間を RELEASED にしてから新区間を ACTIVE で作ること（中間状態を作らない）")
  void linkSwitchesAtomically() {
    givenActor();
    givenCustomerLocked();
    givenMember("999999999999", 8L);
    CustomerMemberLink previous = activeLink(7L, MEMBER_CODE);
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.of(previous));
    Mockito.when(customerMemberLinkRepository.existsByMemberIdAndStatus(8L, LinkStatus.ACTIVE))
        .thenReturn(false);
    givenSaveReturnsArgument();

    CustomerMemberLinkResponse response = service.link(CUSTOMER_ID, "999999999999", ACTOR_EMAIL);

    assertThat(response.memberCode()).isEqualTo("999999999999");
    ArgumentCaptor<CustomerMemberLink> saved = ArgumentCaptor.forClass(CustomerMemberLink.class);
    Mockito.verify(customerMemberLinkRepository, Mockito.times(2)).saveAndFlush(saved.capture());
    // 部分一意索引は据置不可なので、旧区間の解除が新区間の作成より先に DB へ流れること
    assertThat(saved.getAllValues().get(0)).isSameAs(previous);
    assertThat(saved.getAllValues().get(0).getStatus()).isEqualTo(LinkStatus.RELEASED);
    assertThat(saved.getAllValues().get(0).getReleasedBy()).isEqualTo(ACTOR_ID);
    assertThat(saved.getAllValues().get(1).getStatus()).isEqualTo(LinkStatus.ACTIVE);
    assertThat(saved.getAllValues().get(1).getMemberId()).isEqualTo(8L);
  }

  @Test
  @DisplayName("同一店舗で他の顧客に紐づいている会員は紐づけられないこと（409）")
  void linkFailsWhenMemberTakenByAnotherCustomer() {
    givenActor();
    givenCustomerLocked();
    givenMember(MEMBER_CODE, 7L);
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    Mockito.when(customerMemberLinkRepository.existsByMemberIdAndStatus(7L, LinkStatus.ACTIVE))
        .thenReturn(true);

    assertThatThrownBy(() -> service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("既に他の顧客と紐づいています");
    Mockito.verify(customerMemberLinkRepository, Mockito.never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("事前チェックをすり抜けた整合性違反はサービスで握りつぶさず、そのまま伝播すること")
  void linkPropagatesDataIntegrityViolation() {
    givenActor();
    givenCustomerLocked();
    givenMember(MEMBER_CODE, 7L);
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    Mockito.when(customerMemberLinkRepository.existsByMemberIdAndStatus(7L, LinkStatus.ACTIVE))
        .thenReturn(false);
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException("uq_t_customer_member_links_active_member");
    Mockito.when(customerMemberLinkRepository.saveAndFlush(any())).thenThrow(violation);

    // 一意違反→409 / それ以外→500 の分類は CommonExceptionHandler が SQLSTATE で行うため、
    // サービス層で 409 に変換すると FK 等の実装欠陥まで「やり直せば直る」に化けてしまう
    assertThatThrownBy(() -> service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL))
        .isSameAs(violation);
  }

  @Test
  @DisplayName("認証主体のユーザーが存在しない場合は 401 系例外で、副作用が無いこと")
  void linkFailsWhenActorMissing() {
    Mockito.when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL))
        .isInstanceOf(StaleSessionException.class);
    Mockito.verify(customerMemberLinkRepository, Mockito.never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("解除は行を消さず RELEASED にし、解除の実行者を記録すること")
  void unlinkReleasesActiveLink() {
    givenActor();
    givenCustomerLocked();
    CustomerMemberLink current = activeLink(7L, MEMBER_CODE);
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.of(current));

    service.unlink(CUSTOMER_ID, ACTOR_EMAIL);

    assertThat(current.getStatus()).isEqualTo(LinkStatus.RELEASED);
    assertThat(current.getReleasedBy()).isEqualTo(ACTOR_ID);
    Mockito.verify(customerMemberLinkRepository).save(current);
    Mockito.verify(customerMemberLinkRepository, Mockito.never()).delete(any());
  }

  @Test
  @DisplayName("紐づけが無い顧客の解除は 404")
  void unlinkFailsWhenNoActiveLink() {
    givenActor();
    givenCustomerLocked();
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.unlink(CUSTOMER_ID, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("紐づけられている会員がいません");
  }

  @Test
  @DisplayName("紐づけは顧客行を排他ロックしてから現在の紐づけを読むこと")
  void linkTakesTheCustomerRowLockBeforeResolving() {
    // 記帳（受注完了・手動調整）と同じ行を直列化点にすることで、置換の途中の紐づけを記帳側に見せない
    givenActor();
    givenCustomerLocked();
    givenMember(MEMBER_CODE, 7L);
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    Mockito.when(customerMemberLinkRepository.existsByMemberIdAndStatus(7L, LinkStatus.ACTIVE))
        .thenReturn(false);
    givenSaveReturnsArgument();

    service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL);

    InOrder inOrder = Mockito.inOrder(customerRepository, customerMemberLinkRepository);
    inOrder.verify(customerRepository).findByIdForUpdate(CUSTOMER_ID);
    inOrder
        .verify(customerMemberLinkRepository)
        .findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE);
    Mockito.verify(customerRepository, Mockito.never()).existsById(any());
  }

  @Test
  @DisplayName("解除は顧客行を排他ロックしてから現在の紐づけを読むこと")
  void unlinkTakesTheCustomerRowLockBeforeResolving() {
    givenActor();
    givenCustomerLocked();
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.of(activeLink(7L, MEMBER_CODE)));

    service.unlink(CUSTOMER_ID, ACTOR_EMAIL);

    InOrder inOrder = Mockito.inOrder(customerRepository, customerMemberLinkRepository);
    inOrder.verify(customerRepository).findByIdForUpdate(CUSTOMER_ID);
    inOrder
        .verify(customerMemberLinkRepository)
        .findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE);
    Mockito.verify(customerRepository, Mockito.never()).existsById(any());
  }

  @Test
  @DisplayName("履歴照会は顧客行を押さえないこと")
  void historyDoesNotTakeTheCustomerRowLock() {
    // 読むだけの照会が行を押さえると、履歴を開いただけで並行する紐づけの書き換えを待たせる
    givenCustomerExists();
    Mockito.when(customerMemberLinkRepository.findHistory(CUSTOMER_ID)).thenReturn(List.of());

    service.history(CUSTOMER_ID);

    Mockito.verify(customerRepository, Mockito.never()).findByIdForUpdate(any());
  }

  @Test
  @DisplayName("履歴は projection をそのまま応答へ写すこと")
  void historyMapsView() {
    givenCustomerExists();
    CustomerMemberLinkView view = Mockito.mock(CustomerMemberLinkView.class);
    Mockito.when(view.getId()).thenReturn("l1");
    Mockito.when(view.getMemberCode()).thenReturn(MEMBER_CODE);
    Mockito.when(view.getStatus()).thenReturn(LinkStatus.RELEASED);
    Mockito.when(view.getLinkedAt()).thenReturn(OffsetDateTime.parse("2026-07-01T10:00:00+09:00"));
    Mockito.when(view.getLinkedByName()).thenReturn("山田次郎");
    Mockito.when(view.getReleasedAt())
        .thenReturn(OffsetDateTime.parse("2026-07-02T10:00:00+09:00"));
    Mockito.when(view.getReleasedByName()).thenReturn("田中花子");
    Mockito.when(customerMemberLinkRepository.findHistory(CUSTOMER_ID)).thenReturn(List.of(view));

    List<CustomerMemberLinkHistoryResponse> history = service.history(CUSTOMER_ID);

    assertThat(history).hasSize(1);
    CustomerMemberLinkHistoryResponse row = history.get(0);
    assertThat(row.id()).isEqualTo("l1");
    assertThat(row.memberCode()).isEqualTo(MEMBER_CODE);
    assertThat(row.status()).isEqualTo(LinkStatus.RELEASED);
    assertThat(row.linkedByName()).isEqualTo("山田次郎");
    assertThat(row.releasedByName()).isEqualTo("田中花子");
  }

  @Test
  @DisplayName("存在しない顧客の履歴照会は 404")
  void historyFailsWhenCustomerMissing() {
    Mockito.when(customerRepository.existsById(CUSTOMER_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.history(CUSTOMER_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }
}
