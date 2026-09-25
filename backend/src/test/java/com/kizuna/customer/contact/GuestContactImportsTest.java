package com.kizuna.customer.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactHistory;
import com.kizuna.customer.domain.CustomerContactHistoryRepository;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class GuestContactImportsTest {
  @Mock CustomerRepository customers;
  @Mock CustomerContactRepository contacts;
  @Mock CustomerContactHistoryRepository histories;
  @InjectMocks GuestContactImports imports;

  private static final String VALUE = "guest@example.com";

  private void customer() {
    when(customers.findByIdForUpdateNoWait("customer"))
        .thenReturn(Optional.of(Customer.builder().name("顧客").build()));
  }

  private CustomerContact contact(String id) {
    var contact = CustomerContact.create("customer", ContactType.EMAIL, VALUE);
    contact.setId(id);
    return contact;
  }

  @Test
  @DisplayName("新規顧客の販促取り込みは同意の明示選択に従う")
  void newCustomerPreviewUsesExplicitChoice() {
    assertThat(imports.preview(null, null, ContactType.EMAIL, VALUE, true).marketingResult())
        .isEqualTo(GuestContactImport.MarketingResult.ALLOWED);
    assertThat(imports.preview(null, null, ContactType.EMAIL, VALUE, false).marketingResult())
        .isEqualTo(GuestContactImport.MarketingResult.NOT_REQUESTED);
    assertThatThrownBy(() -> imports.preview(null, "existing", ContactType.EMAIL, VALUE, true))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("取り込み先とは別の同値行にある拒否も保つ")
  void preservesDenialInAnotherMatchingRow() {
    customer();
    var target = contact("target");
    var denial = contact("denial");
    denial.changePermission(ContactPurpose.MARKETING, ContactPermissionStatus.DENIED);
    when(contacts.findByIdAndCustomerIdAndDeletedFalse("target", "customer"))
        .thenReturn(Optional.of(target));
    when(contacts.findByCustomerIdAndTypeAndValueAndDeletedFalse(
            "customer", ContactType.EMAIL, VALUE))
        .thenReturn(List.of(target, denial));
    var result = imports.preview("customer", "target", ContactType.EMAIL, VALUE, true);
    assertThat(result.marketingResult())
        .isEqualTo(GuestContactImport.MarketingResult.DENIED_PRESERVED);
    assertThat(target.getMarketingStatus()).isEqualTo(ContactPermissionStatus.UNKNOWN);
  }

  @Test
  @DisplayName("異なる値・種類とスコープ外の取り込み先を拒否する")
  void rejectsMismatchedTargets() {
    customer();
    when(contacts.findByIdAndCustomerIdAndDeletedFalse("target", "customer"))
        .thenReturn(Optional.of(contact("target")));
    assertThatThrownBy(
            () ->
                imports.preview("customer", "target", ContactType.EMAIL, "other@example.com", true))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> imports.preview("customer", "target", ContactType.LINE, VALUE, true))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> imports.preview("customer", "absent", ContactType.EMAIL, VALUE, true))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("顧客の不存在と更新競合を判別する")
  void rejectsMissingOrContendedCustomer() {
    assertThatThrownBy(() -> imports.preview("absent", null, ContactType.EMAIL, VALUE, true))
        .isInstanceOf(NotFoundException.class);
    when(customers.findByIdForUpdateNoWait("busy"))
        .thenThrow(new CannotAcquireLockException("test"));
    assertThatThrownBy(() -> imports.preview("busy", null, ContactType.EMAIL, VALUE, true))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("新規行は未確認で作成し販促変更の前後と申請根拠を保存する")
  void recordsUnknownCreationAndMarketingEvidence() {
    customer();
    when(contacts.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              CustomerContact contact = call.getArgument(0);
              contact.setId("new-contact");
              return contact;
            });
    List<CustomerContactHistory> stored = new ArrayList<>();
    when(histories.save(any()))
        .thenAnswer(
            call -> {
              CustomerContactHistory history = call.getArgument(0);
              stored.add(history);
              return history;
            });
    var result =
        imports.record(
            "customer",
            new GuestContactImport(
                ContactType.EMAIL, VALUE, GuestContactImport.MarketingResult.ALLOWED, null),
            "application",
            "販促同意原文 取得日時: 2026-09-25T10:00:00Z",
            7L);
    assertThat(result.contactId()).isEqualTo("new-contact");
    assertThat(stored.getFirst().getAfter().marketingStatus())
        .isEqualTo(ContactPermissionStatus.UNKNOWN);
    var permission = stored.getLast();
    assertThat(permission.getBefore().marketingStatus()).isEqualTo(ContactPermissionStatus.UNKNOWN);
    assertThat(permission.getAfter().marketingStatus()).isEqualTo(ContactPermissionStatus.ALLOWED);
    assertThat(permission.getAfter().businessStatus()).isEqualTo(ContactPermissionStatus.UNKNOWN);
    assertThat(permission.getApplicationId()).isEqualTo("application");
    assertThat(permission.getActorId()).isEqualTo(7L);
    assertThat(permission.getReason()).contains("販促同意原文", "2026-09-25T10:00:00Z");
    assertThat(permission.getOccurredAt()).isNotNull();
    assertThat(permission.getOperationId()).isEqualTo(stored.getFirst().getOperationId());
  }

  @Test
  @DisplayName("販促未選択と拒否維持の取り込みは既存状態を変えない")
  void unselectedAndDeniedImportsRetainExistingState() {
    customer();
    var target = contact("target");
    target.changePermission(ContactPurpose.MARKETING, ContactPermissionStatus.DENIED);
    when(contacts.findByIdAndCustomerIdAndDeletedFalse("target", "customer"))
        .thenReturn(Optional.of(target));
    for (var result :
        List.of(
            GuestContactImport.MarketingResult.NOT_REQUESTED,
            GuestContactImport.MarketingResult.DENIED_PRESERVED)) {
      var imported =
          imports.record(
              "customer",
              new GuestContactImport(ContactType.EMAIL, VALUE, result, "target"),
              "application",
              "根拠",
              7L);
      assertThat(imported.marketingResult()).isEqualTo(result);
      assertThat(target.getMarketingStatus()).isEqualTo(ContactPermissionStatus.DENIED);
    }
  }
}
