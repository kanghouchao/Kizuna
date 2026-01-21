package com.kizuna.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.exception.ServiceException;
import com.kizuna.model.dto.tenant.customer.CustomerCreateRequest;
import com.kizuna.model.dto.tenant.customer.CustomerResponse;
import com.kizuna.model.dto.tenant.customer.CustomerUpdateRequest;
import com.kizuna.model.entity.tenant.Customer;
import com.kizuna.repository.tenant.CustomerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

  @Mock CustomerRepository customerRepository;
  @InjectMocks CustomerServiceImpl service;
  @Captor ArgumentCaptor<Customer> customerCaptor;

  @Test
  void listReturnsPage() {
    Customer c = new Customer();
    c.setId("c1");
    c.setName("John");
    Page<Customer> page = new PageImpl<>(List.of(c));
    when(customerRepository.findAll(any(PageRequest.class))).thenReturn(page);

    Page<CustomerResponse> res = service.list(null, PageRequest.of(0, 10));
    assertThat(res.getContent()).hasSize(1);
    assertThat(res.getContent().get(0).getName()).isEqualTo("John");
  }

  @Test
  void createSavesCustomer() {
    CustomerCreateRequest req = new CustomerCreateRequest();
    req.setName("Doe");
    req.setPhoneNumber("123");
    when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

    service.create(req);
    verify(customerRepository).save(customerCaptor.capture());
    assertThat(customerCaptor.getValue().getName()).isEqualTo("Doe");
    assertThat(customerCaptor.getValue().getPoints()).isEqualTo(0);
  }

  @Test
  void updateModifiesCustomer() {
    Customer existing = new Customer();
    existing.setId("c1");
    existing.setName("OldName");
    when(customerRepository.findById("c1")).thenReturn(Optional.of(existing));
    when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

    CustomerUpdateRequest req = new CustomerUpdateRequest();
    req.setName("NewName");

    CustomerResponse res = service.update("c1", req);
    assertThat(res.getName()).isEqualTo("NewName");
  }

  @Test
  void deleteThrowsIfNotFound() {
    when(customerRepository.existsById("c99")).thenReturn(false);
    assertThatThrownBy(() -> service.delete("c99")).isInstanceOf(ServiceException.class);
  }
}
