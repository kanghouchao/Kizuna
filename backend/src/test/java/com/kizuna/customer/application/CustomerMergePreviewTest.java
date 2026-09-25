package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.customer.api.dto.CustomerMergePreviewRequest;
import com.kizuna.customer.api.dto.CustomerMergeRequest;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.customer.domain.MergePreferences;
import com.kizuna.customer.domain.MergeProfile;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CustomerMergePreviewTest {
  private final CustomerRepository customers = mock(CustomerRepository.class);
  private final CustomerContactRepository contacts = mock(CustomerContactRepository.class);
  private final CustomerMemberLinkRepository links = mock(CustomerMemberLinkRepository.class);
  private final CustomerMergeRepository merges = mock(CustomerMergeRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final PointLedgerService points = mock(PointLedgerService.class);
  private final MemberLookupService members = mock(MemberLookupService.class);
  private final StoreContext store = mock(StoreContext.class);
  private CustomerMergeService service;
  private CustomerContact first;
  private CustomerContact second;

  @BeforeEach
  void setUp() {
    when(store.getStoreId()).thenReturn(1L);
    var properties = new AppProperties();
    var jwt = new AppProperties.Jwt();
    jwt.setSecret(UUID.randomUUID().toString());
    properties.setJwt(jwt);
    var confirmation = new MergeConfirmation(JsonMapper.builder().build(), properties, store);
    var preparation =
        new MergePreparation(
            customers, contacts, links, merges, users, points, members, store, confirmation);
    service =
        new CustomerMergeService(
            mock(EntityManager.class),
            preparation,
            confirmation,
            customers,
            mock(CustomerContactService.class),
            links,
            merges,
            users,
            store);
    for (String id : List.of("a", "b")) {
      var customer = Customer.builder().name(id).build();
      customer.setId(id);
      when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(customer));
      when(customers.findById(id)).thenReturn(Optional.of(customer));
    }
    first = contact("a", "one");
    second = contact("b", "two");
    when(contacts.findByCustomerIdOrderByIdAsc("a")).thenReturn(List.of(first));
    when(contacts.findByCustomerIdOrderByIdAsc("b")).thenReturn(List.of(second));
  }

  @Test
  void requiresExplicitPreferenceWhenBothRowsHaveOne() {
    var unresolved = service.preview("a", new CustomerMergePreviewRequest("b", null, null));
    assertThat(unresolved.preferenceConflicts()).containsExactly(ContactType.EMAIL);
    assertThat(unresolved.previewToken()).isNull();
    var selected =
        service.preview(
            "a",
            new CustomerMergePreviewRequest("b", null, new MergePreferences(null, "two", null)));
    assertThat(selected.preferenceConflicts()).isEmpty();
    assertThat(selected.preferredContacts().email()).isEqualTo("two");
    assertThat(selected.profile().name()).isEqualTo("a");
    assertThat(selected.previewToken()).hasSize(43);
    assertThat(selected.movedContactCount()).isEqualTo(1);
    assertThat(selected.merged().contacts().getFirst().originCustomerId()).isEqualTo("b");
  }

  @Test
  void refusesDeletedAndWrongTypePreferences() {
    second.delete();
    assertThatThrownBy(
            () ->
                service.preview(
                    "a",
                    new CustomerMergePreviewRequest(
                        "b", null, new MergePreferences(null, "two", null))))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () ->
                service.preview(
                    "a",
                    new CustomerMergePreviewRequest(
                        "b", null, new MergePreferences("one", null, null))))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void reportsFinalMemberBalanceOrdersAndRestrictionWithoutChangingSources() {
    var memberLink =
        CustomerMemberLink.builder()
            .customerId("b")
            .memberId(10L)
            .memberCode("MEMBER")
            .reason(LinkReason.MEMBER_CODE)
            .linkedBy(3L)
            .linkedAt(OffsetDateTime.now())
            .build();
    when(links.findByCustomerIdInAndStatus(List.of("a", "b"), LinkStatus.ACTIVE))
        .thenReturn(List.of(memberLink));
    when(links.findByCustomerIdOrderByIdAsc("b")).thenReturn(List.of(memberLink));
    when(points.balance(10L)).thenReturn(120L);
    var order = mock(CustomerMergeRepository.OrderState.class);
    when(order.getId()).thenReturn("order");
    when(order.getCustomerId()).thenReturn("b");
    when(order.getStatus()).thenReturn("CONFIRMED");
    when(order.getVersion()).thenReturn(1L);
    when(merges.lockOrders(1L, List.of("a", "b"))).thenReturn(List.of(order));
    var denying = contact("a", "denial");
    denying.changePermission(ContactPurpose.MARKETING, ContactPermissionStatus.DENIED);
    when(contacts.findByCustomerIdOrderByIdAsc("a")).thenReturn(List.of(first, denying));
    var profile = new MergeProfile("選択名", null, null, null, null, null, null, null, "注意");
    var result =
        service.preview(
            "a",
            new CustomerMergePreviewRequest("b", profile, new MergePreferences(null, "one", null)));
    assertThat(result.profile()).isEqualTo(profile);
    assertThat(result.surviving().profile().name()).isEqualTo("a");
    assertThat(result.finalMemberCode()).isEqualTo("MEMBER");
    assertThat(result.pointBalance()).isEqualTo(120L);
    assertThat(result.unfinishedOrderCount()).isEqualTo(1);
    assertThat(result.movedOrderCount()).isEqualTo(1);
    assertThat(result.movedLinkCount()).isEqualTo(1);
    assertThat(result.surviving().contacts().getFirst().effectiveMarketingStatus())
        .isEqualTo(ContactPermissionStatus.DENIED);
    assertThat(first.getMarketingStatus()).isEqualTo(ContactPermissionStatus.UNKNOWN);
    var locking = inOrder(members, points);
    locking.verify(members).lockForBalanceConfirmation(10L);
    locking.verify(points).balance(10L);
  }

  @Test
  void rejectsAlteredConfirmationBeforeAnyWrites() {
    var preferences = new MergePreferences(null, "one", null);
    var preview = service.preview("a", new CustomerMergePreviewRequest("b", null, preferences));
    var actor = mock(PlatformUser.class);
    when(actor.getId()).thenReturn(3L);
    when(users.findByEmail("actor")).thenReturn(Optional.of(actor));
    second.change(ContactType.EMAIL, "changed@example.com");
    assertThatThrownBy(
            () ->
                service.merge(
                    "a",
                    new CustomerMergeRequest(
                        "b", preview.previewToken(), null, preferences, true, "照合"),
                    "actor"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("関連情報が変更");
    verify(merges, never()).save(any());
    verify(merges, never()).repointOrders(any(), any(), any());
  }

  private CustomerContact contact(String customerId, String id) {
    var result = CustomerContact.create(customerId, ContactType.EMAIL, "shared@example.com");
    result.setId(id);
    result.prefer(true);
    return result;
  }
}
