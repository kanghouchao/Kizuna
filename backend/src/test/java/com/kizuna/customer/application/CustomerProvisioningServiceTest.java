package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
