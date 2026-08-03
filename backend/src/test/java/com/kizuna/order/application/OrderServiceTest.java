package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.domain.IllegalOrderStateTransitionException;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderPatch;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.OrderView;
import com.kizuna.order.domain.ReceptionRoute;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
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
  @Mock CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock CastRepository castRepository;
  @Mock PlatformUserRepository platformUserRepository;
  @Mock RoleRepository roleRepository;
  @Mock StoreContext storeContext;
  @Mock OrderMapper orderMapper;

  @InjectMocks OrderService service;

  @Captor ArgumentCaptor<Order> orderCaptor;
  @Captor ArgumentCaptor<Customer> customerCaptor;

  private static final long STORE_ID = 1L;

  private OrderPatch emptyPatch() {
    return new OrderPatch(null, null, null, null, null, null, null, null, null, null, null, null);
  }

  /** 受付担当ヘルパーが持つ既定ロール id。@BeforeEach で ORDER_MANAGE を含むものとして緩く stub する。 */
  private static final long STAFF_ROLE_ID = 30L;

  @BeforeEach
  void stubReceptionistRole() {
    // 受付担当検証・受付候補一覧はいずれも「ORDER_MANAGE を含むロール id 集合」を照会する。happy path 用に
    // 既定ロールを含む前提で lenient stub し、検証へ到達しないテストで UnnecessaryStubbing を出さない。
    lenient()
        .when(roleRepository.findIdsByPermissionCode(PermissionCode.ORDER_MANAGE.name()))
        .thenReturn(Set.of(STAFF_ROLE_ID));
  }

  private PlatformUser receptionist(
      UserType userType, StoreScopeType scopeType, Set<Long> storeIds) {
    return PlatformUser.builder()
        .email("receptionist@kizuna.test")
        .password("pw")
        .displayName("受付担当")
        .enabled(true)
        .userType(userType)
        .roleIds(userType == UserType.STAFF ? Set.of(STAFF_ROLE_ID) : Set.of())
        .storeScopeType(scopeType)
        .storeIds(storeIds)
        .build();
  }

  /** 現店舗(store_id=1)を授権し ORDER_MANAGE 権限を持つ受付担当者。 */
  private PlatformUser authorizedReceptionist() {
    return receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(STORE_ID));
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
    assertThatThrownBy(() -> service.get("o2")).isInstanceOf(NotFoundException.class);
  }

  @Test
  void createSavesOrderWithAssociations() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCustomerId("c1");
    req.setCastId("g1");
    req.setReceptionistId(1L);

    Order entity = Order.builder().build();
    OrderResponse res = OrderResponse.builder().status("CREATED").build();

    when(storeContext.getStoreId()).thenReturn(1L);
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

    when(storeContext.getStoreId()).thenReturn(1L);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(customerRepository.findByPhoneNumberAndStoreId("09012345678", 1L))
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

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(castRepository.existsById("g1")).thenReturn(true);
    // 別店舗(store_id=2)専用スコープ: 現店舗(=1)を授権しない
    when(platformUserRepository.findById(1L))
        .thenReturn(
            Optional.of(receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L))));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createRejectsCastRoleReceptionist() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");
    req.setReceptionistId(1L);

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(castRepository.existsById("g1")).thenReturn(true);
    // 全店舗授権でも CAST 本人種別は受付担当者になれない
    when(platformUserRepository.findById(1L))
        .thenReturn(Optional.of(receptionist(UserType.CAST, StoreScopeType.ALL_STORES, Set.of())));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createRejectsStaffWithoutOrderManagePermission() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");
    req.setReceptionistId(1L);

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(castRepository.existsById("g1")).thenReturn(true);
    // 店舗を授権していても、ロールが ORDER_MANAGE を含まない STAFF（HQ 系ロールのみ等）は受付担当者になれない。
    PlatformUser staffWithoutOrderManage =
        PlatformUser.builder()
            .email("hq@kizuna.test")
            .password("pw")
            .displayName("HQ系スタッフ")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(31L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    when(platformUserRepository.findById(1L)).thenReturn(Optional.of(staffWithoutOrderManage));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createRejectsStoppedReceptionist() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");
    req.setReceptionistId(1L);

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(castRepository.existsById("g1")).thenReturn(true);
    // 停止(enabled=false)された STAFF はロール・店舗授権を保持したままだが、受付担当者にはなれない。
    PlatformUser stopped = authorizedReceptionist();
    stopped.stop();
    when(platformUserRepository.findById(1L)).thenReturn(Optional.of(stopped));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void updateModifiesAssociations() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
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
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(
            new OrderPatch(
                null, null, null, null, null, null, "新しい割引名", null, null, null, null, null));
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

    assertThat(existing.getDiscountName()).isEqualTo("新しい割引名");
  }

  @Test
  void updateAppliesLegalStatusTransition() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
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
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
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
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
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
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(castRepository.existsById("none")).thenReturn(false);
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("none");
    req.setReceptionistId(2L);

    assertThatThrownBy(() -> service.update("o1", req)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void updateRejectsReceptionistAuthorizedForDifferentStore() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    // 別店舗(store_id=2)専用スコープ: 現店舗(=1)を授権しない
    when(platformUserRepository.findById(2L))
        .thenReturn(
            Optional.of(receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L))));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void updateRejectsCastRoleReceptionist() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    // 全店舗授権でも CAST 本人種別は受付担当者になれない
    when(platformUserRepository.findById(2L))
        .thenReturn(Optional.of(receptionist(UserType.CAST, StoreScopeType.ALL_STORES, Set.of())));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void listPendingReservationRequestsDelegatesFilteringToTheQuery() {
    OrderView view = mock(OrderView.class);
    OrderResponse res = OrderResponse.builder().id("o1").build();
    when(orderRepository.findPendingReservationRequestViews()).thenReturn(List.of(view));
    when(orderMapper.toResponse(view)).thenReturn(res);

    assertThat(service.listPendingReservationRequests()).containsExactly(res);
    // 受注一覧の先頭ページを取って手元で選り分ける実装だと、確定済みが積み上がった店舗で申請が窓から落ちる
    verify(orderRepository, never()).findAllViews(any(), any(Pageable.class));
  }

  /** 確定・謝絶の対象となる会員申請の形（Web 受付 + 申請時点の会員コード）。 */
  private static Order.OrderBuilder reservationRequest() {
    return Order.builder()
        .status(OrderStatus.CREATED)
        .receptionRoute(ReceptionRoute.WEB)
        .requesterMemberCode("123456789012");
  }

  @Test
  void confirmAssignsActorAsReceptionistWhenSlotIsEmpty() {
    Order request = reservationRequest().build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    PlatformUser actor = authorizedReceptionist();
    actor.setId(7L);
    when(platformUserRepository.findByEmail("staff@kizuna.test")).thenReturn(Optional.of(actor));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.confirm("o1", "staff@kizuna.test");

    assertThat(request.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(request.getReceptionistId()).isEqualTo(7L);
  }

  @Test
  void confirmLeavesReceptionistEmptyWhenActorIsNotEligible() {
    Order request = reservationRequest().build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    PlatformUser otherStoreActor =
        receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L));
    otherStoreActor.setId(7L);
    when(platformUserRepository.findByEmail("staff@kizuna.test"))
        .thenReturn(Optional.of(otherStoreActor));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.confirm("o1", "staff@kizuna.test");

    assertThat(request.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(request.getReceptionistId()).as("適格でない実行者は受付担当に据えないこと").isNull();
  }

  @Test
  void confirmKeepsExistingReceptionist() {
    Order existing = reservationRequest().receptionistId(3L).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.confirm("o1", "staff@kizuna.test");

    assertThat(existing.getReceptionistId()).isEqualTo(3L);
    verify(platformUserRepository, never()).findByEmail(anyString());
  }

  @Test
  void confirmLinksCustomerResolvedAfterTheRequestWasSubmitted() {
    // 初回来店は「申請 → 店舗が会員コードを読んで台帳に紐づけ → 確定」の順になるため、
    // 申請時点では顧客が決まらない。確定時に見直さないと受注が顧客履歴に載らないまま残る。
    Order request = reservationRequest().requesterMemberId(100L).receptionistId(3L).build();
    request.setStoreId(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    CustomerMemberLink link =
        CustomerMemberLink.builder()
            .customerId("cust-1")
            .memberId(100L)
            .memberCode("123456789012")
            .linkedBy(3L)
            .linkedAt(OffsetDateTime.now())
            .build();
    when(customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            STORE_ID, 100L, LinkStatus.ACTIVE))
        .thenReturn(Optional.of(link));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.confirm("o1", "staff@kizuna.test");

    assertThat(request.getCustomerId()).isEqualTo("cust-1");
  }

  @Test
  void confirmRejectsStoreOriginatedOrders() {
    // 店舗が起こした受注は ID を知っていても申請専用の確定操作では変更させない（受付経路 WEB を
    // 手入力で付けただけの受注も、申請者の会員コードが無ければ対象外）
    Order storeOrder =
        Order.builder()
            .status(OrderStatus.CREATED)
            .receptionRoute(ReceptionRoute.WEB)
            .customerId("cust-existing")
            .receptionistId(3L)
            .build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(storeOrder));

    assertThatThrownBy(() -> service.confirm("o1", "staff@kizuna.test"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("予約申請が見つかりません");
    assertThat(storeOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void confirmThrowsWhenOrderMissing() {
    when(orderRepository.findById("nope")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.confirm("nope", "staff@kizuna.test"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void declineCancelsPendingRequest() {
    Order request = reservationRequest().build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.decline("o1");

    assertThat(request.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void declineRejectsAlreadyConfirmedOrder() {
    Order confirmed = reservationRequest().status(OrderStatus.CONFIRMED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));

    assertThatThrownBy(() -> service.decline("o1"))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void declineRejectsStoreOriginatedOrders() {
    // 謝絶は申請専用の操作。店舗起点の受注に対して呼ばれても CANCELLED へ落とさない
    Order storeOrder = Order.builder().status(OrderStatus.CREATED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(storeOrder));

    assertThatThrownBy(() -> service.decline("o1"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("予約申請が見つかりません");
    assertThat(storeOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
    verify(orderRepository, never()).save(any(Order.class));
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
    assertThatThrownBy(() -> service.delete("nope")).isInstanceOf(NotFoundException.class);
  }

  /** {@link #authorizedReceptionist()} を id・表示名だけ差し替えて複製する（一覧テストで複数件を区別するため）。 */
  private PlatformUser staffAuthorizedForCurrentStore(long id, String displayName) {
    PlatformUser user = authorizedReceptionist();
    user.setId(id);
    user.updateDisplayName(displayName);
    return user;
  }

  @Test
  void listReceptionistsReturnsEligibleStaffOrderedByDisplayName() {
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    PlatformUser first = staffAuthorizedForCurrentStore(10L, "あさひ");
    PlatformUser second = staffAuthorizedForCurrentStore(11L, "ひかり");
    when(platformUserRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(
            UserType.STAFF, STORE_ID))
        .thenReturn(List.of(first, second));

    List<OrderReceptionistResponse> result = service.listReceptionists();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo(10L);
    assertThat(result.get(0).getDisplayName()).isEqualTo("あさひ");
    assertThat(result.get(1).getId()).isEqualTo(11L);
    assertThat(result.get(1).getDisplayName()).isEqualTo("ひかり");
  }

  @Test
  void listReceptionistsExcludesDisabledStaff() {
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    PlatformUser disabled = staffAuthorizedForCurrentStore(10L, "停止済み");
    disabled.stop();
    when(platformUserRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(
            UserType.STAFF, STORE_ID))
        .thenReturn(List.of(disabled));

    assertThat(service.listReceptionists()).isEmpty();
  }

  @Test
  void listReceptionistsExcludesStaffAuthorizedForDifferentStore() {
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    PlatformUser otherStore =
        receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L));
    otherStore.setId(10L);
    when(platformUserRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(
            UserType.STAFF, STORE_ID))
        .thenReturn(List.of(otherStore));

    assertThat(service.listReceptionists()).isEmpty();
  }

  @Test
  void listReceptionistsExcludesCastRole() {
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    PlatformUser cast = receptionist(UserType.CAST, StoreScopeType.ALL_STORES, Set.of());
    cast.setId(10L);
    when(platformUserRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(
            UserType.STAFF, STORE_ID))
        .thenReturn(List.of(cast));

    assertThat(service.listReceptionists()).isEmpty();
  }

  @Test
  void listReceptionistsExcludesStaffWithoutOrderManagePermission() {
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    PlatformUser noPermission =
        PlatformUser.builder()
            .email("hq2@kizuna.test")
            .password("pw")
            .displayName("HQ系スタッフ2")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(31L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    noPermission.setId(10L);
    when(platformUserRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(
            UserType.STAFF, STORE_ID))
        .thenReturn(List.of(noPermission));

    assertThat(service.listReceptionists()).isEmpty();
  }
}
