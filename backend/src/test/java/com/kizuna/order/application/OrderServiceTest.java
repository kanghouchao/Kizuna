package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.kizuna.user.domain.Capability;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
class OrderServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock CustomerRepository customerRepository;
  @Mock CastRepository castRepository;
  @Mock PlatformUserRepository platformUserRepository;
  @Mock CapabilityBundleRepository capabilityBundleRepository;
  @Mock TenantRepository tenantRepository;
  @Mock TenantContext tenantContext;
  @Mock OrderMapper orderMapper;

  @InjectMocks OrderService service;

  @Captor ArgumentCaptor<Order> orderCaptor;
  @Captor ArgumentCaptor<Customer> customerCaptor;

  private static final long TENANT_ID = 1L;

  private OrderPatch emptyPatch() {
    return new OrderPatch(null, null, null, null, null, null, null, null, null, null, null, null);
  }

  /** 受付担当ヘルパーが持つ既定束 id。@BeforeEach で ORDER_MANAGE を含むものとして緩く stub する。 */
  private static final long STAFF_BUNDLE_ID = 30L;

  @BeforeEach
  void stubReceptionistBundle() {
    // 受付担当検証は「束のいずれかが ORDER_MANAGE を含むか」を照会する。happy path 用に既定束は
    // 含む前提で lenient stub し、検証へ到達しないテストで UnnecessaryStubbing を出さない。
    lenient()
        .when(
            capabilityBundleRepository.anyBundleHasCapability(
                Set.of(STAFF_BUNDLE_ID), Capability.ORDER_MANAGE))
        .thenReturn(true);
  }

  private PlatformUser receptionist(
      UserType userType, StoreScopeType scopeType, Set<Long> storeIds) {
    return PlatformUser.builder()
        .email("receptionist@kizuna.test")
        .password("pw")
        .displayName("受付担当")
        .enabled(true)
        .userType(userType)
        .bundleIds(userType == UserType.STAFF ? Set.of(STAFF_BUNDLE_ID) : Set.of())
        .storeScopeType(scopeType)
        .storeIds(storeIds)
        .build();
  }

  /** 現テナント(store_id=1)を授権し ORDER_MANAGE 能力を持つ受付担当者。 */
  private PlatformUser authorizedReceptionist() {
    return receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(TENANT_ID));
  }

  @Test
  void listReturnsPageOfOrderResponses() {
    OrderView view = mock(OrderView.class);
    OrderResponse res = OrderResponse.builder().id("o1").build();
    Page<OrderView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 10), 1);

    when(orderRepository.findAllViews(eq(null), any(Pageable.class))).thenReturn(page);
    when(orderMapper.toResponse(view)).thenReturn(res);

    Page<OrderResponse> result = service.list(null, PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getId()).isEqualTo("o1");
  }

  @Test
  void listFiltersByCustomerId() {
    OrderView view = mock(OrderView.class);
    OrderResponse res = OrderResponse.builder().id("o1").build();
    Page<OrderView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 10), 1);

    when(orderRepository.findAllViews(eq("c1"), any(Pageable.class))).thenReturn(page);
    when(orderMapper.toResponse(view)).thenReturn(res);

    Page<OrderResponse> result = service.list("c1", PageRequest.of(0, 10));
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
    req.setReceptionistId(1L);

    Order entity = Order.builder().build();
    OrderResponse res = OrderResponse.builder().status("CREATED").build();

    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(entity);
    when(customerRepository.existsById("c1")).thenReturn(true);
    when(castRepository.existsById("g1")).thenReturn(true);
    when(platformUserRepository.findById(1L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(res);

    service.create(req);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isEqualTo("c1");
    assertThat(orderCaptor.getValue().getCastId()).isEqualTo("g1");
    assertThat(orderCaptor.getValue().getReceptionistId()).isEqualTo(1L);
  }

  @Test
  void createCreatesCustomerWhenPhoneProvided() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setPhoneNumber("09012345678");
    req.setCustomerName("New Guy");
    req.setCastId("g1");
    req.setReceptionistId(1L);

    Customer newCustomer = Customer.builder().phoneNumber("09012345678").build();

    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(customerRepository.findByPhoneNumberAndTenantId("09012345678", 1L))
        .thenReturn(Optional.empty());
    when(castRepository.existsById("g1")).thenReturn(true);
    when(platformUserRepository.findById(1L)).thenReturn(Optional.of(authorizedReceptionist()));

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
  void createRejectsReceptionistAuthorizedForDifferentStore() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");
    req.setReceptionistId(1L);

    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(castRepository.existsById("g1")).thenReturn(true);
    // 別店舗(store_id=2)専用スコープ: 現テナント(=1)を授権しない
    when(platformUserRepository.findById(1L))
        .thenReturn(
            Optional.of(receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L))));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createRejectsCastRoleReceptionist() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");
    req.setReceptionistId(1L);

    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(castRepository.existsById("g1")).thenReturn(true);
    // 全店舗授権でも CAST 本人種別は受付担当者になれない
    when(platformUserRepository.findById(1L))
        .thenReturn(Optional.of(receptionist(UserType.CAST, StoreScopeType.ALL_STORES, Set.of())));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createRejectsStaffWithoutOrderManageCapability() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");
    req.setReceptionistId(1L);

    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(castRepository.existsById("g1")).thenReturn(true);
    // 店舗を授権していても、束が ORDER_MANAGE を含まない STAFF（HQ 系束のみ等）は受付担当者になれない。
    PlatformUser staffWithoutOrderManage =
        PlatformUser.builder()
            .email("hq@kizuna.test")
            .password("pw")
            .displayName("HQ系スタッフ")
            .enabled(true)
            .userType(UserType.STAFF)
            .bundleIds(Set.of(31L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    when(platformUserRepository.findById(1L)).thenReturn(Optional.of(staffWithoutOrderManage));
    when(capabilityBundleRepository.anyBundleHasCapability(Set.of(31L), Capability.ORDER_MANAGE))
        .thenReturn(false);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createRejectsStoppedReceptionist() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");
    req.setReceptionistId(1L);

    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(new Tenant()));
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(castRepository.existsById("g1")).thenReturn(true);
    // 停止(enabled=false)された STAFF は束・店舗授権を保持したままだが、受付担当者にはなれない（PR#399 codex 指摘）。
    PlatformUser stopped = authorizedReceptionist();
    stopped.stop();
    when(platformUserRepository.findById(1L)).thenReturn(Optional.of(stopped));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void updateModifiesAssociations() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();

    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("g2")).thenReturn(true);
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);

    service.update("o1", req);

    assertThat(existing.getCastId()).isEqualTo("g2");
    assertThat(existing.getReceptionistId()).isEqualTo(2L);
  }

  @Test
  void updateAppliesPatchFields() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();

    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(
            new OrderPatch(
                "新しい店名", null, null, null, null, null, null, null, null, null, null, null));
    when(castRepository.existsById("g2")).thenReturn(true);
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);

    service.update("o1", req);

    assertThat(existing.getStoreName()).isEqualTo("新しい店名");
  }

  @Test
  void updateAppliesLegalStatusTransition() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("g2")).thenReturn(true);
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);
    req.setStatus("CONFIRMED");

    service.update("o1", req);

    assertThat(existing.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  void updateRejectsIllegalStatusTransition() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("g2")).thenReturn(true);
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);
    req.setStatus("COMPLETED");

    assertThatThrownBy(() -> service.update("o1", req)).isInstanceOf(ServiceException.class);
    assertThat(existing.getStatus()).isEqualTo(OrderStatus.CREATED);
  }

  @Test
  void updateRejectsUnknownStatusValue() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("g2")).thenReturn(true);
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);
    req.setStatus("GARBAGE");

    assertThatThrownBy(() -> service.update("o1", req)).isInstanceOf(ServiceException.class);
  }

  @Test
  void updateThrowsWhenCastNotFound() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("none")).thenReturn(false);
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("none");
    req.setReceptionistId(2L);

    assertThatThrownBy(() -> service.update("o1", req)).isInstanceOf(ServiceException.class);
  }

  @Test
  void updateRejectsReceptionistAuthorizedForDifferentStore() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    // 別店舗(store_id=2)専用スコープ: 現テナント(=1)を授権しない
    when(platformUserRepository.findById(2L))
        .thenReturn(
            Optional.of(receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L))));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void updateRejectsCastRoleReceptionist() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    // 全店舗授権でも CAST 本人種別は受付担当者になれない
    when(platformUserRepository.findById(2L))
        .thenReturn(Optional.of(receptionist(UserType.CAST, StoreScopeType.ALL_STORES, Set.of())));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
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
