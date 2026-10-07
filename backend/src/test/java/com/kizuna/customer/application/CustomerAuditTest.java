package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerMapperImpl;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerCandidateRepository;
import com.kizuna.customer.domain.CustomerContactHistoryRepository;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerListRepository;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CustomerAuditTest {
  private final CustomerRepository customers = mock(CustomerRepository.class);
  private final CustomerContactRepository contacts = mock(CustomerContactRepository.class);
  private final CustomerMergeRepository merges = mock(CustomerMergeRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final List<AuditEvent> audit = new ArrayList<>();
  private CustomerService service;
  private Customer row;

  @BeforeEach
  void prepare() {
    var actor =
        PlatformUser.builder()
            .email("operator@example.test")
            .displayName("顧客担当")
            .userType(UserType.STAFF)
            .enabled(true)
            .password(UUID.randomUUID().toString())
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    actor.setId(9L);
    when(users.findByEmail(anyString())).thenReturn(Optional.of(actor));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(actor.getEmail(), null, List.of()));
    when(events.saveAndFlush(any(AuditEvent.class)))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              audit.add(event);
              return event;
            });
    when(customers.saveAndFlush(any(Customer.class)))
        .thenAnswer(
            call -> {
              row = call.getArgument(0);
              row.setId("customer");
              row.setStoreId(1L);
              return row;
            });
    when(customers.save(any(Customer.class))).thenAnswer(call -> call.getArgument(0));
    when(customers.findById(anyString())).thenAnswer(call -> Optional.ofNullable(row));
    when(customers.findByIdForUpdate(anyString())).thenAnswer(call -> Optional.ofNullable(row));
    var sink = new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC()));
    var contactService =
        new CustomerContactService(
            customers, contacts, mock(CustomerContactHistoryRepository.class), users, sink);
    service =
        new CustomerService(
            customers,
            mock(CustomerListRepository.class),
            mock(CustomerCandidateRepository.class),
            contactService,
            contacts,
            mock(CustomerMemberLinkRepository.class),
            merges,
            new CustomerMapperImpl(),
            sink);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void creationIsTraceableWithoutCopyingPrivateAttributes() {
    var request = new CustomerCreateRequest();
    request.setName("秘密の氏名");
    request.setAddress("秘密の住所");
    var created = service.create(request);
    assertThat(audit).hasSize(1);
    var event = audit.getFirst();
    assertThat(event.getAction()).isEqualTo("CUSTOMER_CREATED");
    assertThat(event.getTargetId()).isEqualTo(created.getId());
    assertThat(event.getTargetType()).isEqualTo("CUSTOMER");
    assertThat(event.getActorId()).isEqualTo(9L);
    assertThat(event.getStoreId()).isEqualTo(1L);
    assertThat(event.getSourceId()).isNull();
    assertThat(event.getSourceType()).isNull();
    assertThat(event.getBeforeValues()).isEmpty();
    assertThat(event.getAfterValues())
        .containsEntry("exists", "true")
        .containsEntry("redacted_fields_changed", "address,name");
    assertThat(event.getAfterValues().toString()).doesNotContain("秘密");
  }

  @Test
  void editingPrivateAttributesRecordsOnlyChangedNamesAndIgnoresNoOps() {
    var request = new CustomerCreateRequest();
    request.setName("元の秘密名");
    service.create(request);
    audit.clear();
    var patch = new CustomerUpdateRequest();
    patch.setName("新しい秘密名");
    patch.setAddress("秘密住所");
    patch.setBuildingName("秘密建物");
    patch.setLandmark("秘密目印");
    patch.setClassification("秘密分類");
    patch.setHasPet(false);
    patch.setUsageAreas("秘密地域");
    patch.setNgType("秘密区分");
    patch.setNgContent("秘密内容");
    service.update("customer", patch);
    service.update("customer", patch);
    service.update("customer", new CustomerUpdateRequest());
    assertThat(audit).hasSize(1);
    var event = audit.getFirst();
    assertThat(event.getAction()).isEqualTo("CUSTOMER_UPDATED");
    assertThat(event.getBeforeValues()).containsOnlyKeys("exists", "version");
    assertThat(event.getAfterValues())
        .containsOnlyKeys("exists", "version", "redacted_fields_changed")
        .containsEntry(
            "redacted_fields_changed",
            "address,building_name,classification,has_pet,landmark,name,ng_content,ng_type,usage_areas");
    assertThat(event.getAfterValues().toString()).doesNotContain("秘密");
  }

  @Test
  void physicalDeletionKeepsTheLastSafeSnapshot() {
    var request = new CustomerCreateRequest();
    request.setName("削除する秘密名");
    service.create(request);
    audit.clear();
    service.delete("customer");
    assertThat(audit).hasSize(1);
    var event = audit.getFirst();
    assertThat(event.getAction()).isEqualTo("CUSTOMER_DELETED");
    assertThat(event.getTargetId()).isEqualTo("customer");
    assertThat(event.getBeforeValues()).containsOnlyKeys("exists", "version");
    assertThat(event.getAfterValues()).isEqualTo(Map.of("exists", "false"));
  }

  @Test
  void missingMergedAndProtectedCustomersNeverProduceSuccessEvents() {
    assertThatThrownBy(() -> service.delete("missing")).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.update("missing", new CustomerUpdateRequest()))
        .isInstanceOf(RuntimeException.class);
    var request = new CustomerCreateRequest();
    request.setName("拒否対象");
    service.create(request);
    audit.clear();
    when(customers.isMerged("customer")).thenReturn(true);
    assertThatThrownBy(() -> service.update("customer", new CustomerUpdateRequest()))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.delete("customer")).isInstanceOf(RuntimeException.class);
    when(customers.isMerged("customer")).thenReturn(false);
    when(contacts.existsByCustomerIdOrOriginCustomerId("customer", "customer")).thenReturn(true);
    assertThatThrownBy(() -> service.delete("customer")).isInstanceOf(RuntimeException.class);
    when(contacts.existsByCustomerIdOrOriginCustomerId("customer", "customer")).thenReturn(false);
    when(merges.existsInvolving("customer")).thenReturn(true);
    assertThatThrownBy(() -> service.delete("customer")).isInstanceOf(RuntimeException.class);
    assertThat(audit).isEmpty();
  }
}
