package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CustomerProvisioningServiceTest {
  @Mock CustomerRepository customers;
  @Mock CustomerMemberLinkRepository links;
  @Mock CustomerReferenceResolver resolver;
  @InjectMocks CustomerProvisioningService service;

  private static final Long STORE_ID = 7L;

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
    assertThatThrownBy(() -> service.ensureMemberRequestCustomer(STORE_ID, input))
        .isInstanceOf(MemberCustomerConflictException.class);
    assertThatThrownBy(() -> service.ensureMemberRequestCustomer(STORE_ID, input))
        .isSameAs(otherConflict);
  }

  @Test
  void provisionedRowsCarryTheGivenStore() {
    when(customers.save(any()))
        .thenAnswer(
            call -> {
              Customer saved = call.getArgument(0);
              saved.setId("new");
              return saved;
            });
    service.ensureMemberRequestCustomer(
        STORE_ID, new MemberRequestCustomerInput(3L, "code", "名乗り", 9L));
    service.createCustomer(STORE_ID, new NewCustomerInput("新規"));

    var customer = ArgumentCaptor.forClass(Customer.class);
    verify(customers, times(2)).save(customer.capture());
    assertThat(customer.getAllValues()).extracting(Customer::getStoreId).containsOnly(STORE_ID);
    var link = ArgumentCaptor.forClass(CustomerMemberLink.class);
    verify(links).saveAndFlush(link.capture());
    assertThat(link.getValue().getStoreId()).isEqualTo(STORE_ID);
  }

  private static DataIntegrityViolationException violation(String constraint) {
    return new DataIntegrityViolationException(
        "duplicate",
        new ConstraintViolationException(
            "duplicate", new SQLException("duplicate", "23505"), constraint));
  }
}
