package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderPatch;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.OrderView;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.StoreUserRepository;
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
  @Mock StoreUserRepository storeUserRepository;
  @Mock TenantRepository tenantRepository;
  @Mock TenantContext tenantContext;
  @Mock OrderMapper orderMapper;

  @InjectMocks OrderServiceImpl service;

  @Captor ArgumentCaptor<Order> orderCaptor;
  @Captor ArgumentCaptor<Customer> customerCaptor;

  private OrderPatch emptyPatch() {
    return new OrderPatch(null, null, null, null, null, null, null, null, null, null, null, null);
  }

  @Test
  void listReturnsPageOfOrderResponses() {
    OrderView view = mock(OrderView.class);
    OrderResponse res = OrderResponse.builder().id("o1").build();
    Page<OrderView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 10), 1);

    when(orderRepository.findAllViews(any(Pageable.class))).thenReturn(page);
    when(orderMapper.toResponse(view)).thenReturn(res);

    Page<OrderResponse> result = service.list(PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getId()).isEqualTo("o1");
  }

  @Test
  void getReturnsOrderResponseOrThrows() {
    OrderView view = mock(OrderView.class);
    OrderResponse res = OrderResponse.builder().id("o1").build();

    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(view));
    when(orderMapper.toResponse(view)).thenReturn(res);
    when(orderRepository.findViewById("o2")).thenReturn(Optional.empty());

    assertThat(service.get("o1").getId()).isEqualTo("o1");
    assertThatThrownBy(() -> service.get("o2")).isInstanceOf(ServiceException.class);
  }

  @Test
  void createSavesOrderWithAssociations() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCustomerId("c1");
    req.setCastId("g1");
    req.setReceptionistId("r1");

    Order entity = Order.builder().build();
    OrderResponse res = OrderResponse.builder().status("CREATED").build();

    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(entity);
    when(customerRepository.existsById("c1")).thenReturn(true);
    when(castRepository.existsById("g1")).thenReturn(true);
    when(storeUserRepository.existsById("r1")).thenReturn(true);
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(res);

    service.create(req);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isEqualTo("c1");
    assertThat(orderCaptor.getValue().getCastId()).isEqualTo("g1");
    assertThat(orderCaptor.getValue().getReceptionistId()).isEqualTo("r1");
  }

  @Test
  void createCreatesCustomerWhenPhoneProvided() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setPhoneNumber("09012345678");
    req.setCustomerName("New Guy");
    req.setCastId("g1");
    req.setReceptionistId("r1");

    Customer newCustomer = Customer.builder().phoneNumber("09012345678").build();

    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(customerRepository.findByPhoneNumberAndTenantId("09012345678", 1L))
        .thenReturn(Optional.empty());
    when(castRepository.existsById("g1")).thenReturn(true);
    when(storeUserRepository.existsById("r1")).thenReturn(true);

    when(orderMapper.toCustomer(req)).thenReturn(newCustomer);

    when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.create(req);

    verify(customerRepository).save(customerCaptor.capture());
    assertThat(customerCaptor.getValue().getPhoneNumber()).isEqualTo("09012345678");
  }

  @Test
  void updateModifiesAssociations() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();

    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("g2")).thenReturn(true);
    when(storeUserRepository.existsById("r2")).thenReturn(true);
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId("r2");

    service.update("o1", req);

    assertThat(existing.getCastId()).isEqualTo("g2");
    assertThat(existing.getReceptionistId()).isEqualTo("r2");
  }

  @Test
  void updateAppliesPatchFields() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();

    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(
            new OrderPatch(
                "新しい店名", null, null, null, null, null, null, null, null, null, null, null));
    when(castRepository.existsById("g2")).thenReturn(true);
    when(storeUserRepository.existsById("r2")).thenReturn(true);
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId("r2");

    service.update("o1", req);

    assertThat(existing.getStoreName()).isEqualTo("新しい店名");
  }

  @Test
  void updateAppliesLegalStatusTransition() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("g2")).thenReturn(true);
    when(storeUserRepository.existsById("r2")).thenReturn(true);
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId("r2");
    req.setStatus("CONFIRMED");

    service.update("o1", req);

    assertThat(existing.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  void updateRejectsIllegalStatusTransition() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("g2")).thenReturn(true);
    when(storeUserRepository.existsById("r2")).thenReturn(true);

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId("r2");
    req.setStatus("COMPLETED");

    assertThatThrownBy(() -> service.update("o1", req)).isInstanceOf(ServiceException.class);
    assertThat(existing.getStatus()).isEqualTo(OrderStatus.CREATED);
  }

  @Test
  void updateRejectsUnknownStatusValue() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("g2")).thenReturn(true);
    when(storeUserRepository.existsById("r2")).thenReturn(true);

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId("r2");
    req.setStatus("GARBAGE");

    assertThatThrownBy(() -> service.update("o1", req)).isInstanceOf(ServiceException.class);
  }

  @Test
  void updateThrowsWhenCastNotFound() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("none")).thenReturn(false);
    when(storeUserRepository.existsById("r2")).thenReturn(true);

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("none");
    req.setReceptionistId("r2");

    assertThatThrownBy(() -> service.update("o1", req)).isInstanceOf(ServiceException.class);
  }

  @Test
  void deleteRemovesIfExists() {
    when(orderRepository.existsById("o1")).thenReturn(true);
    service.delete("o1");
    verify(orderRepository).deleteById("o1");
  }

  @Test
  void deleteThrowsWhenMissing() {
    when(orderRepository.existsById("nope")).thenReturn(false);
    assertThatThrownBy(() -> service.delete("nope")).isInstanceOf(ServiceException.class);
  }
}
