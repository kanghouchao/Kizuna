package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.api.dto.ContactPermissionRequest;
import com.kizuna.customer.api.dto.ContactRequest;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactHistory;
import com.kizuna.customer.domain.CustomerContactHistoryRepository;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CustomerContactAuditTest {
  private final CustomerRepository customers = mock(CustomerRepository.class);
  private final CustomerContactRepository contacts = mock(CustomerContactRepository.class);
  private final CustomerContactHistoryRepository histories =
      mock(CustomerContactHistoryRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final List<CustomerContact> rows = new ArrayList<>();
  private final List<CustomerContactHistory> history = new ArrayList<>();
  private final List<AuditEvent> audit = new ArrayList<>();
  private CustomerContactService service;

  @BeforeEach
  void prepare() {
    var actor =
        PlatformUser.builder()
            .email("operator@example.test")
            .password(UUID.randomUUID().toString())
            .displayName("連絡先担当")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    actor.setId(9L);
    when(users.findByEmail(anyString())).thenReturn(Optional.of(actor));
    when(users.findById(anyLong())).thenReturn(Optional.of(actor));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(actor.getEmail(), null, List.of()));
    var customer = Customer.builder().name("顧客").build();
    customer.setId("customer");
    when(customers.findByIdForUpdate(anyString())).thenReturn(Optional.of(customer));
    when(contacts.saveAndFlush(any(CustomerContact.class)))
        .thenAnswer(
            call -> {
              CustomerContact contact = call.getArgument(0);
              contact.setId("contact-" + rows.size());
              contact.setStoreId(1L);
              rows.add(contact);
              return contact;
            });
    when(contacts.findByIdAndCustomerIdAndDeletedFalse(anyString(), anyString()))
        .thenAnswer(
            call ->
                rows.stream()
                    .filter(
                        c ->
                            c.getId().equals(call.getArgument(0))
                                && c.getCustomerId().equals(call.getArgument(1))
                                && !c.isDeleted())
                    .findFirst());
    when(contacts.findByCustomerIdAndTypeAndValueAndDeletedFalse(anyString(), any(), anyString()))
        .thenAnswer(
            call ->
                rows.stream()
                    .filter(
                        c ->
                            c.getCustomerId().equals(call.getArgument(0))
                                && c.getType() == call.getArgument(1)
                                && c.getValue().equals(call.getArgument(2))
                                && !c.isDeleted())
                    .toList());
    when(contacts.findPreferred(anyString(), any()))
        .thenAnswer(
            call ->
                rows.stream()
                    .filter(
                        c ->
                            c.getCustomerId().equals(call.getArgument(0))
                                && c.getType() == call.getArgument(1)
                                && c.isPreferred()
                                && !c.isDeleted())
                    .findFirst());
    when(histories.save(any(CustomerContactHistory.class)))
        .thenAnswer(
            call -> {
              CustomerContactHistory row = call.getArgument(0);
              row.setId("history-" + history.size());
              history.add(row);
              return row;
            });
    when(events.saveAndFlush(any(AuditEvent.class)))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              audit.add(event);
              return event;
            });
    var sink = new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC()));
    service = new CustomerContactService(customers, contacts, histories, users, sink);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void createdContactIsTraceableWithoutCopyingItsPrivateValue() {
    var created =
        service.create("customer", new ContactRequest(ContactType.EMAIL, "private@EXAMPLE.TEST"));
    assertThat(audit).hasSize(1);
    var event = audit.getFirst();
    assertThat(event.getAction()).isEqualTo("CUSTOMER_CONTACT_CREATED");
    assertThat(event.getTargetType()).isEqualTo("CUSTOMER_CONTACT");
    assertThat(event.getTargetId()).isEqualTo(created.id());
    assertThat(event.getSourceType()).isEqualTo("CUSTOMER_CONTACT_HISTORY");
    assertThat(event.getSourceId()).isEqualTo(history.getFirst().getId());
    assertThat(event.getActorId()).isEqualTo(9L);
    assertThat(event.getBeforeValues()).isEmpty();
    assertThat(event.getAfterValues())
        .containsEntry("type", "EMAIL")
        .containsEntry("business_status", "UNKNOWN");
    assertThat(event.getAfterValues().toString()).doesNotContain("private", "EXAMPLE");
  }

  @Test
  void switchingPreferenceRecordsBothSidesOnceAndIgnoresRepeatingTheChoice() {
    var first = service.create("customer", new ContactRequest(ContactType.LINE, "private-first"));
    var second = service.create("customer", new ContactRequest(ContactType.LINE, "private-second"));
    service.prefer("customer", ContactType.LINE, first.id());
    audit.clear();
    service.prefer("customer", ContactType.LINE, second.id());
    assertThat(audit).hasSize(2);
    assertThat(audit)
        .allSatisfy(
            event ->
                assertThat(event.getAction()).isEqualTo("CUSTOMER_CONTACT_PREFERENCE_CHANGED"));
    assertThat(audit.getFirst().getTargetId()).isEqualTo(first.id());
    assertThat(audit.getFirst().getBeforeValues()).containsEntry("preferred", "true");
    assertThat(audit.getFirst().getAfterValues()).containsEntry("preferred", "false");
    assertThat(audit.getLast().getTargetId()).isEqualTo(second.id());
    assertThat(audit.getLast().getBeforeValues()).containsEntry("preferred", "false");
    assertThat(audit.getLast().getAfterValues()).containsEntry("preferred", "true");
    assertThat(audit.getFirst().getAfterValues().get("operation_id"))
        .isEqualTo(audit.getLast().getAfterValues().get("operation_id"));
    service.prefer("customer", ContactType.LINE, second.id());
    assertThat(audit).hasSize(2);
  }

  @Test
  void permissionEvidenceIsRecordedEvenWhenTheStatusIsUnchangedWithoutCopyingEvidence() {
    var contact =
        service.create("customer", new ContactRequest(ContactType.LINE, "private-contact"));
    audit.clear();
    var input =
        new ContactPermissionRequest(
            ContactPermissionStatus.ALLOWED, "private-source", "private-reason");
    service.changePermission("customer", contact.id(), ContactPurpose.BUSINESS, input);
    service.changePermission("customer", contact.id(), ContactPurpose.BUSINESS, input);
    assertThat(audit).hasSize(2);
    assertThat(audit)
        .allSatisfy(
            event -> {
              assertThat(event.getAction()).isEqualTo("CUSTOMER_CONTACT_PERMISSION_RECORDED");
              assertThat(event.getAfterValues()).containsEntry("purpose", "BUSINESS");
              assertThat(event.getAfterValues().toString()).doesNotContain("private");
            });
    assertThat(audit.getFirst().getBeforeValues()).containsEntry("business_status", "UNKNOWN");
    assertThat(audit.getFirst().getAfterValues()).containsEntry("business_status", "ALLOWED");
    assertThat(audit.getLast().getBeforeValues()).containsEntry("business_status", "ALLOWED");
    assertThat(audit.getLast().getSourceId()).isNotEqualTo(audit.getFirst().getSourceId());
  }

  @Test
  void deletingADenialAuditsTheRemainingContactsInheritedRestriction() {
    var source =
        service.create("customer", new ContactRequest(ContactType.LINE, "private-duplicate"));
    var target =
        service.create("customer", new ContactRequest(ContactType.LINE, "private-duplicate"));
    service.changePermission(
        "customer",
        source.id(),
        ContactPurpose.BUSINESS,
        new ContactPermissionRequest(
            ContactPermissionStatus.DENIED, "private-source", "private-reason"));
    audit.clear();
    service.delete("customer", source.id());
    assertThat(audit).hasSize(2);
    var inherited =
        audit.stream().filter(e -> e.getTargetId().equals(target.id())).findFirst().orElseThrow();
    var deleted =
        audit.stream().filter(e -> e.getTargetId().equals(source.id())).findFirst().orElseThrow();
    assertThat(inherited.getAction()).isEqualTo("CUSTOMER_CONTACT_RESTRICTION_INHERITED");
    assertThat(inherited.getBeforeValues()).containsEntry("business_status", "UNKNOWN");
    assertThat(inherited.getAfterValues())
        .containsEntry("business_status", "DENIED")
        .containsEntry("source_contact_id", source.id());
    assertThat(deleted.getAction()).isEqualTo("CUSTOMER_CONTACT_DELETED");
    assertThat(deleted.getBeforeValues()).containsEntry("deleted", "false");
    assertThat(deleted.getAfterValues()).containsEntry("deleted", "true");
    assertThat(inherited.getAfterValues().get("operation_id"))
        .isEqualTo(deleted.getAfterValues().get("operation_id"));
    assertThat(audit)
        .allSatisfy(e -> assertThat(e.getAfterValues().toString()).doesNotContain("private"));
  }

  @Test
  void changingAValueRecordsOnlyItsFieldNameAndLeavesNormalizedNoOpUnaudited() {
    var created =
        service.create("customer", new ContactRequest(ContactType.EMAIL, "private@EXAMPLE.TEST"));
    audit.clear();
    service.update(
        "customer", created.id(), new ContactRequest(ContactType.EMAIL, "private@example.test"));
    assertThat(audit).isEmpty();
    service.update("customer", created.id(), new ContactRequest(ContactType.LINE, "private-line"));
    assertThat(audit).hasSize(1);
    assertThat(audit.getFirst().getAction()).isEqualTo("CUSTOMER_CONTACT_UPDATED");
    assertThat(audit.getFirst().getBeforeValues()).containsEntry("type", "EMAIL");
    assertThat(audit.getFirst().getAfterValues())
        .containsEntry("type", "LINE")
        .containsEntry("redacted_fields_changed", "value");
    assertThat(audit.getFirst().getAfterValues().toString()).doesNotContain("private");
  }

  @Test
  void editingTheSourceInheritsRestrictionsButDeletingUnchangedDuplicatesAddsNoInheritanceAudit() {
    var source = service.create("customer", new ContactRequest(ContactType.LINE, "private-shared"));
    var target = service.create("customer", new ContactRequest(ContactType.LINE, "private-shared"));
    service.changePermission(
        "customer",
        source.id(),
        ContactPurpose.MARKETING,
        new ContactPermissionRequest(
            ContactPermissionStatus.DENIED, "private-source", "private-reason"));
    audit.clear();
    service.update("customer", source.id(), new ContactRequest(ContactType.LINE, "private-new"));
    assertThat(audit)
        .extracting(AuditEvent::getAction)
        .containsExactly("CUSTOMER_CONTACT_RESTRICTION_INHERITED", "CUSTOMER_CONTACT_UPDATED");
    assertThat(audit.getFirst().getTargetId()).isEqualTo(target.id());
    assertThat(audit.getFirst().getAfterValues()).containsEntry("marketing_status", "DENIED");
    var duplicate =
        service.create("customer", new ContactRequest(ContactType.LINE, "private-shared"));
    service.changePermission(
        "customer",
        duplicate.id(),
        ContactPurpose.MARKETING,
        new ContactPermissionRequest(
            ContactPermissionStatus.DENIED, "private-source", "private-reason"));
    audit.clear();
    service.delete("customer", target.id());
    assertThat(audit).extracting(AuditEvent::getAction).containsExactly("CUSTOMER_CONTACT_DELETED");
  }

  @Test
  void deniedInputsAndMissingContactsNeverCreateSuccessfulAudit() {
    var created = service.create("customer", new ContactRequest(ContactType.LINE, "private"));
    service.changePermission(
        "customer",
        created.id(),
        ContactPurpose.BUSINESS,
        new ContactPermissionRequest(
            ContactPermissionStatus.DENIED, "private-source", "private-reason"));
    audit.clear();
    assertThatThrownBy(
            () ->
                service.changePermission(
                    "customer",
                    created.id(),
                    ContactPurpose.BUSINESS,
                    new ContactPermissionRequest(
                        ContactPermissionStatus.UNKNOWN, "private-source", "private-reason")))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.delete("customer", "missing"))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.prefer("customer", ContactType.EMAIL, created.id()))
        .isInstanceOf(RuntimeException.class);
    when(customers.isMerged("customer")).thenReturn(true);
    assertThatThrownBy(() -> service.delete("customer", created.id()))
        .isInstanceOf(RuntimeException.class);
    assertThat(audit).isEmpty();
  }
}
