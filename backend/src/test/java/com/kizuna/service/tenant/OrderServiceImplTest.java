package com.kizuna.service.tenant;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.exception.ServiceException;
import com.kizuna.model.dto.tenant.order.OrderCreateRequest;
import com.kizuna.model.dto.tenant.order.OrderResponse;
import com.kizuna.model.dto.tenant.order.OrderUpdateRequest;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.model.entity.tenant.Customer;
import com.kizuna.model.entity.tenant.Girl;
import com.kizuna.model.entity.tenant.Order;
import com.kizuna.model.entity.tenant.security.TenantUser;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.CustomerRepository;
import com.kizuna.repository.tenant.GirlRepository;
import com.kizuna.repository.tenant.OrderRepository;
import com.kizuna.repository.tenant.TenantUserRepository;
import java.time.LocalDate;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

  @Mock OrderRepository orderRepository;
  @Mock CustomerRepository customerRepository;
  @Mock GirlRepository girlRepository;
  @Mock TenantUserRepository tenantUserRepository;
  @Mock TenantRepository tenantRepository;
  @Mock TenantContext tenantContext;

  @InjectMocks OrderServiceImpl service;

  @Captor ArgumentCaptor<Order> orderCaptor;
  @Captor ArgumentCaptor<Customer> customerCaptor;

  @Test
  void listReturnsPageOfOrderResponses() {
    Order o = new Order();
    o.setId("o1");
    o.setStoreName("Store A");
    Page<Order> page = new PageImpl<>(List.of(o), PageRequest.of(0, 10), 1);
    when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);

    Page<OrderResponse> result = service.list(PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getId()).isEqualTo("o1");
  }

  @Test
  void getReturnsOrderResponseOrThrows() {
    Order o = new Order();
    o.setId("o1");
    when(orderRepository.findById("o1")).thenReturn(Optional.of(o));
    when(orderRepository.findById("o2")).thenReturn(Optional.empty());

    assertThat(service.get("o1").getId()).isEqualTo("o1");
    assertThatThrownBy(() -> service.get("o2")).isInstanceOf(ServiceException.class);
  }

  @Test
  void createSavesOrderWithAssociations() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setStoreName("S1");
    req.setBusinessDate(LocalDate.now());
    req.setCustomerId("c1");
    req.setGirlId("g1");
    req.setReceptionistId("r1");

    when(customerRepository.findById("c1")).thenReturn(Optional.of(new Customer()));
    when(girlRepository.findById("g1")).thenReturn(Optional.of(new Girl()));
    when(tenantUserRepository.findById("r1")).thenReturn(Optional.of(new TenantUser()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

    OrderResponse res = service.create(req);

    verify(orderRepository).save(orderCaptor.capture());
    Order saved = orderCaptor.getValue();
    assertThat(saved.getStoreName()).isEqualTo("S1");
    assertThat(saved.getCustomer()).isNotNull();
    assertThat(saved.getGirl()).isNotNull();
    assertThat(saved.getReceptionist()).isNotNull();
    assertThat(res.getStatus()).isEqualTo("CREATED");
  }

  @Test
  void createCreatesCustomerWhenPhoneProvided() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setStoreName("S1");
    req.setPhoneNumber("09012345678");
    req.setCustomerName("New Guy");

    // Mock tenant context for customer creation
    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(new Tenant()));

    // Mock customer lookup (not found) and save
    when(customerRepository.findByPhoneNumber("09012345678")).thenReturn(Optional.empty());
    when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

    service.create(req);

    // Verify customer was created
    verify(customerRepository).save(customerCaptor.capture());
    Customer newCustomer = customerCaptor.getValue();
    assertThat(newCustomer.getName()).isEqualTo("New Guy");
    assertThat(newCustomer.getPhoneNumber()).isEqualTo("09012345678");
    assertThat(newCustomer.getTenant()).isNotNull(); // Verify tenant was set

    // Verify order was linked
    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomer()).isEqualTo(newCustomer);
  }

  @Test
  void createThrowsWhenCustomerNotFound() {

    OrderCreateRequest req = new OrderCreateRequest();
    req.setCustomerId("c1");
    when(customerRepository.findById("c1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(ServiceException.class);
  }

  @Test
  void updateModifiesFieldsAndValidatesRelations() {
    Order existing = new Order();
    existing.setId("o1");
    existing.setStoreName("Old");

    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(girlRepository.findById("g2")).thenReturn(Optional.of(new Girl()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setStoreName("New");
    req.setGirlId("g2");

    OrderResponse res = service.update("o1", req);

    assertThat(res.getStoreName()).isEqualTo("New");
    assertThat(existing.getGirl()).isNotNull();
  }

  @Test
  void deleteRemovesIfExists() {
    when(orderRepository.existsById("o1")).thenReturn(true);
    service.delete("o1");
    verify(orderRepository).deleteById("o1");

    when(orderRepository.existsById("o2")).thenReturn(false);
    assertThatThrownBy(() -> service.delete("o2")).isInstanceOf(ServiceException.class);
  }
}
