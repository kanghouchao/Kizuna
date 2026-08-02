package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import com.kizuna.customer.api.dto.CustomerMemberLinkHistoryResponse;
import com.kizuna.customer.api.dto.CustomerMemberLinkResponse;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMemberLinkView;
import com.kizuna.customer.domain.CustomerRepository;
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

  private void givenMember(String memberCode, long memberId) {
    Mockito.when(memberLookupService.findByMemberCode(memberCode))
        .thenReturn(Optional.of(new MemberLookup(memberId, memberCode)));
  }

  private static CustomerMemberLink activeLink(long memberId, String memberCode) {
    return CustomerMemberLink.builder()
        .customerId(CUSTOMER_ID)
        .memberId(memberId)
        .memberCode(memberCode)
        .linkedBy(1L)
        .linkedAt(OffsetDateTime.parse("2026-07-01T10:00:00+09:00"))
        .build();
  }

  private void givenSaveReturnsArgument() {
    Mockito.when(customerMemberLinkRepository.saveAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("未紐づけの顧客に会員コードを紐づけると ACTIVE の区間が 1 件作られること")
  void linkCreatesActiveLink() {
    givenActor();
    givenCustomerExists();
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
    assertThat(saved.getValue().getLinkedBy()).isEqualTo(ACTOR_ID);
  }

  @Test
  @DisplayName("存在しない顧客への紐づけは 404（他店舗の顧客も同じ経路で 404 になる）")
  void linkFailsWhenCustomerMissing() {
    givenActor();
    Mockito.when(customerRepository.existsById(CUSTOMER_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  @Test
  @DisplayName("存在しない会員コードでの紐づけは 404")
  void linkFailsWhenMemberCodeUnknown() {
    givenActor();
    givenCustomerExists();
    Mockito.when(memberLookupService.findByMemberCode(MEMBER_CODE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("会員コード");
  }

  @Test
  @DisplayName("既に紐づいている会員をもう一度紐づけると 409")
  void linkFailsWhenSameMemberAlreadyLinked() {
    givenActor();
    givenCustomerExists();
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
    givenCustomerExists();
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
    givenCustomerExists();
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
  @DisplayName("事前チェックをすり抜けた並行紐づけが一意索引に当たった場合も 409 になること")
  void linkMapsUniqueViolationToConflict() {
    givenActor();
    givenCustomerExists();
    givenMember(MEMBER_CODE, 7L);
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    Mockito.when(customerMemberLinkRepository.existsByMemberIdAndStatus(7L, LinkStatus.ACTIVE))
        .thenReturn(false);
    Mockito.when(customerMemberLinkRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("uq_t_customer_member_links_active_member"));

    assertThatThrownBy(() -> service.link(CUSTOMER_ID, MEMBER_CODE, ACTOR_EMAIL))
        .isInstanceOf(ConflictException.class);
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
    givenCustomerExists();
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
    givenCustomerExists();
    Mockito.when(
            customerMemberLinkRepository.findByCustomerIdAndStatus(CUSTOMER_ID, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.unlink(CUSTOMER_ID, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("紐づけられている会員がいません");
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
