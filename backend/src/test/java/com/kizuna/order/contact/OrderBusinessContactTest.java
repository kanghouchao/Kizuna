package com.kizuna.order.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.customer.contact.BusinessContactRestrictions;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.order.api.dto.BusinessContactPermissionRequest;
import com.kizuna.order.application.BusinessContactPermissions;
import com.kizuna.order.domain.BusinessContactHistory;
import com.kizuna.order.domain.BusinessContactHistoryRepository;
import com.kizuna.order.domain.BusinessContactState;
import com.kizuna.order.domain.ContactSnapshot;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderBusinessContactTest {
  private final CustomerContactRepository contacts = mock(CustomerContactRepository.class);
  private final BusinessContactHistoryRepository histories =
      mock(BusinessContactHistoryRepository.class);
  private final OrderRepository orders = mock(OrderRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final StoreContext store = new StoreContext();
  private final BusinessContactPolicy policy =
      new BusinessContactPolicy(new BusinessContactRestrictions(contacts, store));
  private final BusinessContactPermissions permissions =
      new BusinessContactPermissions(new ActorIdentityService(users), histories, orders, policy);
  private final OrderBusinessContact boundary =
      new OrderBusinessContact(orders, permissions, store);

  @BeforeEach
  void storeContext() {
    store.setStoreId(1L);
  }

  @ParameterizedTest
  @EnumSource(ContactPermissionStatus.class)
  void applicationDecisionHonorsDenialBeforeItsOwnConsent(ContactPermissionStatus status) {
    when(contacts.hasBusinessDenial(ContactType.EMAIL, "Person@example.com")).thenReturn(true);
    assertThat(policy.evaluate(ContactType.EMAIL, " Person@EXAMPLE.COM ", status))
        .isEqualTo(BusinessContactDecision.STORE_DENIED);
  }

  @ParameterizedTest
  @EnumSource(ContactPermissionStatus.class)
  void applicationNeedsExplicitPermissionWhenNoCustomerDenies(ContactPermissionStatus status) {
    assertThat(policy.evaluate(ContactType.LINE, " line-id ", status))
        .isEqualTo(
            status == ContactPermissionStatus.ALLOWED
                ? BusinessContactDecision.ALLOWED
                : BusinessContactDecision.NOT_ALLOWED);
  }

  @Test
  void orderReturnsOnlyPersistedDestinationAndRechecksEveryTime() {
    var order = Order.builder().build();
    order.setId("o1");
    order.replaceContact(
        ContactSnapshot.normalize(null, "090-1234-5678", "Person@EXAMPLE.COM", "once-line"));
    when(orders.findById("o1")).thenReturn(Optional.of(order));
    when(histories.findFirstByOrderIdAndTypeOrderByIdDesc(eq("o1"), any()))
        .thenAnswer(
            invocation -> {
              ContactType type = invocation.getArgument(1);
              String value =
                  switch (type) {
                    case PHONE -> "+819012345678";
                    case EMAIL -> "Person@example.com";
                    case LINE -> "once-line";
                  };
              return Optional.of(
                  BusinessContactHistory.record(
                      "o1",
                      type,
                      "RECORDED",
                      null,
                      new BusinessContactState(
                          value, ContactPermissionStatus.ALLOWED, "電話", "今回だけ"),
                      1L));
            });
    assertThat(boundary.decide("o1", ContactType.PHONE))
        .isEqualTo(
            new OrderBusinessContact.Decision("+819012345678", BusinessContactDecision.ALLOWED));
    when(contacts.hasBusinessDenial(ContactType.PHONE, "+819012345678")).thenReturn(true);
    assertThat(boundary.decide("o1", ContactType.PHONE).decision())
        .isEqualTo(BusinessContactDecision.STORE_DENIED);
    when(contacts.hasBusinessDenial(ContactType.PHONE, "+819012345678")).thenReturn(false);
    assertThat(boundary.decide("o1", ContactType.PHONE).decision())
        .isEqualTo(BusinessContactDecision.ALLOWED);
  }

  @Test
  void absenceOfEvidenceOrDestinationNeverGrantsPermission() {
    var order = Order.builder().build();
    order.setId("o1");
    when(orders.findById("o1")).thenReturn(Optional.of(order));
    assertThat(boundary.decide("o1", ContactType.EMAIL))
        .isEqualTo(new OrderBusinessContact.Decision(null, BusinessContactDecision.NOT_ALLOWED));
    order.replaceContact(ContactSnapshot.normalize(null, null, "once@example.com", null));
    assertThat(boundary.decide("o1", ContactType.EMAIL))
        .isEqualTo(
            new OrderBusinessContact.Decision(
                "once@example.com", BusinessContactDecision.NOT_ALLOWED));
  }

  @Test
  void evidenceForAnotherDestinationFailsClosed() {
    var order = Order.builder().build();
    order.setId("o1");
    order.replaceContact(ContactSnapshot.normalize(null, null, "new@example.com", null));
    when(orders.findById("o1")).thenReturn(Optional.of(order));
    when(histories.findFirstByOrderIdAndTypeOrderByIdDesc("o1", ContactType.EMAIL))
        .thenReturn(
            Optional.of(
                BusinessContactHistory.record(
                    "o1",
                    ContactType.EMAIL,
                    "RECORDED",
                    null,
                    new BusinessContactState(
                        "old@example.com", ContactPermissionStatus.ALLOWED, "電話", "今回だけ"),
                    1L)));
    assertThatThrownBy(() -> boundary.decide("o1", ContactType.EMAIL))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void changingDestinationRevokesRecordedPermissionWithoutRevivingItOnReturn() {
    var order = Order.builder().build();
    order.setId("o1");
    var actor =
        PlatformUser.builder()
            .email("actor@kizuna.test")
            .password(UUID.randomUUID().toString())
            .userType(UserType.STAFF)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    actor.setId(1L);
    when(users.findByEmail("actor@kizuna.test")).thenReturn(Optional.of(actor));
    when(orders.findById("o1")).thenReturn(Optional.of(order));
    var stored = new EnumMap<ContactType, BusinessContactHistory>(ContactType.class);
    when(histories.save(any()))
        .thenAnswer(
            invocation -> {
              BusinessContactHistory history = invocation.getArgument(0);
              stored.put(history.getType(), history);
              return history;
            });
    when(histories.findFirstByOrderIdAndTypeOrderByIdDesc(eq("o1"), any()))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(1))));
    var original = ContactSnapshot.normalize(null, null, "first@example.com", null);
    order.replaceContact(original);
    permissions.record(
        order,
        ContactSnapshot.empty(),
        List.of(
            new BusinessContactPermissionRequest(
                ContactType.EMAIL, ContactPermissionStatus.ALLOWED, "本人申告", "今回の連絡を希望")),
        "actor@kizuna.test");
    assertThat(boundary.decide("o1", ContactType.EMAIL).decision())
        .isEqualTo(BusinessContactDecision.ALLOWED);
    var changed = ContactSnapshot.normalize(null, null, "second@example.com", null);
    order.replaceContact(changed);
    permissions.record(order, original, List.of(), "actor@kizuna.test");
    assertThat(boundary.decide("o1", ContactType.EMAIL))
        .isEqualTo(
            new OrderBusinessContact.Decision(
                "second@example.com", BusinessContactDecision.NOT_ALLOWED));
    order.replaceContact(original);
    permissions.record(order, changed, null, "actor@kizuna.test");
    assertThat(boundary.decide("o1", ContactType.EMAIL).decision())
        .isEqualTo(BusinessContactDecision.NOT_ALLOWED);
  }

  @Test
  void invalidOrUnscopedCallsCannotProducePermission() {
    assertThatThrownBy(() -> boundary.decide("missing", ContactType.PHONE))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () -> policy.evaluate(ContactType.PHONE, "invalid", ContactPermissionStatus.ALLOWED))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> policy.evaluate(null, "value", ContactPermissionStatus.ALLOWED))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () -> policy.evaluate(ContactType.LINE, " ", ContactPermissionStatus.ALLOWED))
        .isInstanceOf(ServiceException.class);
    store.clear();
    assertThatThrownBy(() -> boundary.decide("o1", ContactType.PHONE))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () ->
                policy.evaluate(
                    ContactType.EMAIL, "once@example.com", ContactPermissionStatus.ALLOWED))
        .isInstanceOf(ServiceException.class);
  }
}
