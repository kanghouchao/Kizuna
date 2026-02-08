package com.kizuna.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.exception.ServiceException;
import com.kizuna.mapper.tenant.CustomerMapper;
import com.kizuna.mapper.tenant.OrderMapper;
import com.kizuna.model.dto.tenant.order.OrderCreateRequest;
import com.kizuna.model.dto.tenant.order.OrderResponse;
import com.kizuna.model.dto.tenant.order.OrderUpdateRequest;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.model.entity.tenant.Cast;
import com.kizuna.model.entity.tenant.Customer;
import com.kizuna.model.entity.tenant.Order;
import com.kizuna.model.entity.tenant.security.TenantUser;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.CastRepository;
import com.kizuna.repository.tenant.CustomerRepository;
import com.kizuna.repository.tenant.OrderRepository;
import com.kizuna.repository.tenant.TenantUserRepository;
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
  @Mock CastRepository castRepository;
  @Mock TenantUserRepository tenantUserRepository;
  @Mock TenantRepository tenantRepository;
  @Mock TenantContext tenantContext;
  @Mock OrderMapper orderMapper;
  @Mock CustomerMapper customerMapper;

  @InjectMocks OrderServiceImpl service;

  @Captor ArgumentCaptor<Order> orderCaptor;
  @Captor ArgumentCaptor<Customer> customerCaptor;

  @Test
  void listReturnsPageOfOrderResponses() {
    Order o = new Order();
    o.setId("o1");
    OrderResponse res = OrderResponse.builder().id("o1").build();
    Page<Order> page = new PageImpl<>(List.of(o), PageRequest.of(0, 10), 1);

    when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);
    when(orderMapper.toResponse(any(Order.class))).thenReturn(res);

    Page<OrderResponse> result = service.list(PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getId()).isEqualTo("o1");
  }

  @Test
  void getReturnsOrderResponseOrThrows() {
    Order o = new Order();
    o.setId("o1");
    OrderResponse res = OrderResponse.builder().id("o1").build();

    when(orderRepository.findById("o1")).thenReturn(Optional.of(o));
    when(orderMapper.toResponse(o)).thenReturn(res);
    when(orderRepository.findById("o2")).thenReturn(Optional.empty());

    assertThat(service.get("o1").getId()).isEqualTo("o1");
    assertThatThrownBy(() -> service.get("o2")).isInstanceOf(ServiceException.class);
  }

  @Test
  void createSavesOrderWithAssociations() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCustomerId("c1");
    req.setCastId("g1");
    req.setReceptionistId("r1");

    Order entity = new Order();
    OrderResponse res = OrderResponse.builder().status("CREATED").build();

    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(entity);
    when(customerRepository.findById("c1")).thenReturn(Optional.of(new Customer()));
    when(castRepository.findById("g1")).thenReturn(Optional.of(new Cast()));
    when(tenantUserRepository.findById("r1")).thenReturn(Optional.of(new TenantUser()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderMapper.toResponse(any(Order.class))).thenReturn(res);

    service.create(req);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomer()).isNotNull();
    assertThat(orderCaptor.getValue().getCast()).isNotNull();
    assertThat(orderCaptor.getValue().getReceptionist()).isNotNull();
  }

  @Test
  void createCreatesCustomerWhenPhoneProvided() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setPhoneNumber("09012345678");
    req.setCustomerName("New Guy");

    Customer newCustomer = new Customer();
    newCustomer.setPhoneNumber("09012345678");

    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(new Order());
    when(customerRepository.findByPhoneNumberAndTenantId("09012345678", 1L))
        .thenReturn(Optional.empty());

    when(customerMapper.toCustomer(req)).thenReturn(newCustomer);

    when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderMapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().build());

    service.create(req);

    verify(customerRepository).save(customerCaptor.capture());
    assertThat(customerCaptor.getValue().getPhoneNumber()).isEqualTo("09012345678");
  }

  @Test
  void updateModifiesAssociations() {
    Order existing = new Order();
    existing.setId("o1");

    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(castRepository.findById("g2")).thenReturn(Optional.of(new Cast()));
    when(tenantUserRepository.findById("r2")).thenReturn(Optional.of(new TenantUser()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderMapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().build());

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId("r2");

    service.update("o1", req);

    assertThat(existing.getCast()).isNotNull();
    assertThat(existing.getReceptionist()).isNotNull();
    verify(orderMapper).updateEntityFromRequest(req, existing);
  }

  @Test
  void updateThrowsWhenCastNotFound() {
    Order existing = new Order();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(castRepository.findById("none")).thenReturn(Optional.empty());

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("none");

    assertThatThrownBy(() -> service.update("o1", req)).isInstanceOf(ServiceException.class);
  }

  @Test
  void deleteRemovesIfExists() {
    when(orderRepository.existsById("o1")).thenReturn(true);
    service.delete("o1");
    verify(orderRepository).deleteById("o1");
  }

  @Test
  void createThrowsWhenCastNotFound() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g_none");

    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(new Order());
    when(castRepository.findById("g_none")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("キャストが見つかりません");
  }

  @Test
  void createThrowsWhenReceptionistNotFound() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setReceptionistId("r_none");

    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(new Order());
    when(tenantUserRepository.findById("r_none")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("受付担当者が見つかりません");
  }

  @Test
  void deleteThrowsIfNotFound() {
    when(orderRepository.existsById("bad")).thenReturn(false);
    assertThatThrownBy(() -> service.delete("bad")).isInstanceOf(ServiceException.class);
  }
}
