package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.storescope.StoreContext;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CustomerProvisioningServiceTest {
  @Mock CustomerRepository customers;
  @Mock CustomerMemberLinkRepository links;
  @Mock CustomerReferenceResolver resolver;
  @Mock StoreContext storeContext;
  @InjectMocks CustomerProvisioningService service;

  @Test
  void storeCreationPreservesEveryInputField() {
    when(storeContext.getStoreId()).thenReturn(7L);
    when(customers.save(any()))
        .thenAnswer(
            invocation -> {
              Customer customer = invocation.getArgument(0);
              assertThat(customer.getName()).isEqualTo("氏名");
              assertThat(customer.getPhoneNumber()).isEqualTo("0901");
              assertThat(customer.getPhoneNumber2()).isEqualTo("0902");
              assertThat(customer.getAddress()).isEqualTo("住所");
              assertThat(customer.getBuildingName()).isEqualTo("建物");
              assertThat(customer.getLandmark()).isEqualTo("目印");
              assertThat(customer.getClassification()).isEqualTo("区分");
              assertThat(customer.getHasPet()).isTrue();
              assertThat(customer.getNgType()).isEqualTo("種別");
              assertThat(customer.getNgContent()).isEqualTo("内容");
              customer.setId("new");
              return customer;
            });
    assertThat(
            service.resolveStoreCustomer(
                new StoreCustomerInput(
                    null, "氏名", "0901", "0902", "住所", "建物", "目印", "区分", true, "種別", "内容")))
        .contains("new");
    verifyNoInteractions(resolver);
  }

  @Test
  void translatesOnlyTheActiveMemberConstraint() {
    Customer customer = Customer.builder().build();
    customer.setId("new");
    when(customers.save(any())).thenReturn(customer);
    DataIntegrityViolationException memberConflict =
        violation("uq_t_customer_member_links_active_member");
    DataIntegrityViolationException otherConflict =
        violation("uq_t_customer_member_links_active_customer");
    when(links.saveAndFlush(any())).thenThrow(memberConflict).thenThrow(otherConflict);
    MemberRequestCustomerInput input = new MemberRequestCustomerInput(3L, "code", "名乗り", 9L);
    assertThatThrownBy(() -> service.ensureMemberRequestCustomer(input))
        .isInstanceOf(MemberCustomerConflictException.class);
    assertThatThrownBy(() -> service.ensureMemberRequestCustomer(input)).isSameAs(otherConflict);
  }

  private static DataIntegrityViolationException violation(String constraint) {
    return new DataIntegrityViolationException(
        "duplicate",
        new ConstraintViolationException(
            "duplicate", new SQLException("duplicate", "23505"), constraint));
  }
}
