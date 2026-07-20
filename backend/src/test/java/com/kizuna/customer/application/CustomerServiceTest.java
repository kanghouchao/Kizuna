package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerMapper;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerPatch;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerMapper customerMapper;
  @Mock private StoreContext storeContext;
  @Mock private StoreRepository storeRepository;

  @InjectMocks private CustomerService customerService;

  @Test
  void list_returnsPage() {
    Customer c = Customer.builder().name("Test").build();
    Page<Customer> page = new PageImpl<>(List.of(c));

    CustomerResponse resp = new CustomerResponse();
    resp.setName("Test");

    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toResponse(c)).thenReturn(resp);

    Page<CustomerResponse> result =
        customerService.list("test", "GOLD", "VIP", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test");
  }

  @Test
  void list_withoutFilters_returnsAll() {
    Customer c = Customer.builder().name("All").build();
    Page<Customer> page = new PageImpl<>(List.of(c));

    CustomerResponse resp = new CustomerResponse();
    resp.setName("All");

    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toResponse(c)).thenReturn(resp);

    Page<CustomerResponse> result = customerService.list("", " ", null, PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("All");
  }

  @Test
  void get_returnsResponse() {
    Customer c = new Customer();
    c.setId("c1");

    CustomerResponse resp = new CustomerResponse();
    resp.setId("c1");

    when(customerRepository.findById("c1")).thenReturn(Optional.of(c));
    when(customerMapper.toResponse(c)).thenReturn(resp);

    assertThat(customerService.get("c1").getId()).isEqualTo("c1");
  }

  @Test
  void get_throwsWhenNotFound() {
    when(customerRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.get("missing"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  @Test
  void create_savesAndReturns() {
    CustomerCreateRequest req = new CustomerCreateRequest();
    req.setName("New");

    Customer customerEntity = Customer.builder().name("New").build();

    Store store = new Store();
    store.setId(1L);

    when(customerMapper.toEntity(req)).thenReturn(customerEntity);
    when(storeContext.getStoreId()).thenReturn(1L);
    when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

    when(customerRepository.save(any()))
        .thenAnswer(
            i -> {
              Customer saved = i.getArgument(0);
              saved.setId("new_id");
              return saved;
            });

    CustomerResponse resp = new CustomerResponse();
    resp.setId("new_id");
    resp.setName("New");
    when(customerMapper.toResponse(any())).thenReturn(resp);

    CustomerResponse res = customerService.create(req);
    assertThat(res.getId()).isEqualTo("new_id");
    assertThat(res.getName()).isEqualTo("New");
  }

  @Test
  void create_throwsWhenStoreNotFound() {
    CustomerCreateRequest req = new CustomerCreateRequest();
    req.setName("New");

    when(customerMapper.toEntity(req)).thenReturn(new Customer());
    when(storeContext.getStoreId()).thenReturn(1L);
    when(storeRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("店舗が見つかりません");
  }

  @Test
  void update_modifiesFields() {
    Customer c = new Customer();
    c.setId("c1");

    when(customerRepository.findById("c1")).thenReturn(Optional.of(c));
    when(customerRepository.save(any())).thenReturn(c);

    CustomerUpdateRequest req = new CustomerUpdateRequest();
    req.setName("Updated");

    when(customerMapper.toPatch(req))
        .thenReturn(
            new CustomerPatch(
                "Updated", null, null, null, null, null, null, null, null, null, null, null));

    CustomerResponse resp = new CustomerResponse();
    resp.setName("Updated");
    when(customerMapper.toResponse(c)).thenReturn(resp);

    CustomerResponse res = customerService.update("c1", req);
    assertThat(c.getName()).isEqualTo("Updated");
    assertThat(res.getName()).isEqualTo("Updated");
  }

  @Test
  void update_throwsWhenNotFound() {
    when(customerRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.update("missing", new CustomerUpdateRequest()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  @Test
  void delete_removesIfExists() {
    when(customerRepository.existsById("c1")).thenReturn(true);
    customerService.delete("c1");
    verify(customerRepository).deleteById("c1");
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(customerRepository.existsById("missing")).thenReturn(false);

    assertThatThrownBy(() -> customerService.delete("missing"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("顧客が見つかりません");
  }
}
