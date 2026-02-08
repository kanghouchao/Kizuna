package com.kizuna.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.mapper.tenant.CustomerMapper;
import com.kizuna.model.dto.tenant.customer.CustomerCreateRequest;
import com.kizuna.model.dto.tenant.customer.CustomerResponse;
import com.kizuna.model.dto.tenant.customer.CustomerUpdateRequest;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.model.entity.tenant.Customer;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.CustomerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerMapper customerMapper;
  @Mock private TenantContext tenantContext;
  @Mock private TenantRepository tenantRepository;

  @InjectMocks private CustomerServiceImpl customerService;

  @Test
  void list_returnsPage() {
    Customer c = new Customer();
    c.setName("Test");
    Page<Customer> page = new PageImpl<>(List.of(c));

    CustomerResponse resp = new CustomerResponse();
    resp.setName("Test");

    when(customerRepository.findByNameContainingIgnoreCase(eq("test"), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toResponse(c)).thenReturn(resp);

    Page<CustomerResponse> result = customerService.list("test", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test");
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
  void create_savesAndReturns() {
    CustomerCreateRequest req = new CustomerCreateRequest();
    req.setName("New");

    Customer customerEntity = new Customer();
    customerEntity.setName("New");

    Tenant tenant = new Tenant();
    tenant.setId(1L);

    when(customerMapper.toEntity(req)).thenReturn(customerEntity);
    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

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
  void update_modifiesFields() {
    Customer c = new Customer();
    c.setId("c1");

    when(customerRepository.findById("c1")).thenReturn(Optional.of(c));
    when(customerRepository.save(any())).thenReturn(c);

    CustomerUpdateRequest req = new CustomerUpdateRequest();
    req.setName("Updated");

    CustomerResponse resp = new CustomerResponse();
    resp.setName("Updated");
    when(customerMapper.toResponse(c)).thenReturn(resp);

    CustomerResponse res = customerService.update("c1", req);
    verify(customerMapper).updateEntityFromRequest(req, c);
    assertThat(res.getName()).isEqualTo("Updated");
  }

  @Test
  void delete_removesIfExists() {
    when(customerRepository.existsById("c1")).thenReturn(true);
    customerService.delete("c1");
    verify(customerRepository).deleteById("c1");
  }
}
