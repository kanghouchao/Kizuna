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

import com.kizuna.cast.domain.Cast;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.order.api.dto.OrderCastCandidateResponse;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.api.dto.ReservationRequestUpdateRequest;
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
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
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
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock CustomerRepository customerRepository;
  @Mock CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock NominatableCastLookup nominatableCast;
  @Mock ConfirmedShiftLookupService confirmedShiftLookupService;
  @Mock PlatformUserRepository platformUserRepository;
  @Mock RoleRepository roleRepository;
  @Mock StoreContext storeContext;
  @Mock OrderMapper orderMapper;

  @InjectMocks OrderService service;

  @Captor ArgumentCaptor<Order> orderCaptor;
  @Captor ArgumentCaptor<Customer> customerCaptor;

  private static final long STORE_ID = 1L;

  /** 予約受付 inbox の並びの鍵になる受付時刻。 */
  private static final OffsetDateTime RECEIVED_AT =
      OffsetDateTime.parse("2026-08-04T10:00:00+09:00");

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

  /**
   * 述語が「成立する」と答えたときに返るキャスト。
   *
   * <p>成立の条件そのもの（店舗一致・在籍中）を固定するのは {@link NominatableCastLookupTest} で、ここは空か否かの翻訳だけを見る。
   */
  private static Cast nominatable(String castId) {
    Cast cast = Cast.builder().name("指名キャスト").status("ACTIVE").build();
    cast.setId(castId);
    cast.setStoreId(STORE_ID);
    return cast;
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
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
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
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
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
  void createRejectsACastThatIsNotNominatable() {
    // 候補に出さないだけでは、キャスト ID を直接送る要求を防げない。店舗が起こす受注は常に新しい指名を
    // 立てるため据え置きの余地が無く、無条件に要求する
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("retired");
    req.setReceptionistId(1L);

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(nominatableCast.find(STORE_ID, "retired")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(req))
        // 対象は店舗スタッフなので、列挙を防ぐ 404 ではなく理由と対処の分かる 400 で返す
        .isInstanceOf(ServiceException.class)
        .isNotInstanceOf(NotFoundException.class)
        .hasMessageContaining("在籍中のキャスト");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createRejectsReceptionistAuthorizedForDifferentStore() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");
    req.setReceptionistId(1L);

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
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
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
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
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
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
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
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
    when(nominatableCast.find(STORE_ID, "g2")).thenReturn(Optional.of(nominatable("g2")));
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
  void updateRejectsStatusChangeForReservationRequests() {
    // 申請の状態遷移の入口は確定・謝絶の専用操作ただ一つ — 汎用更新から遷移できると
    // 確定時の指名再検証・顧客の補完・謝絶の対象判定をすべて迂回できてしまう
    Order request = reservationRequest().build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);
    req.setStatus("CONFIRMED");

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("確定・謝絶の操作でのみ");
    assertThat(request.getStatus()).isEqualTo(OrderStatus.CREATED);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateAllowsLifecycleTransitionsAfterConfirmation() {
    // 受付経路と申請者スナップショットは確定後も残るため、「申請かどうか」だけで遷移を塞ぐと
    // 会員起点の受注が完了・キャンセルへ一切進めなくなる。ガードは未確定（CREATED）に限る
    Order confirmedOrder =
        reservationRequest().status(OrderStatus.CONFIRMED).receptionistId(3L).build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmedOrder));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(nominatableCast.find(STORE_ID, "g2")).thenReturn(Optional.of(nominatable("g2")));
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);
    req.setStatus("COMPLETED");

    service.update("o1", req);

    assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
  }

  @Test
  void updateAllowsFieldEditsOnReservationRequestsWithoutStatusChange() {
    // 状態に触れない編集（受付担当・キャストの補完など）は申請にも引き続き許す
    Order request = reservationRequest().build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(nominatableCast.find(STORE_ID, "g2")).thenReturn(Optional.of(nominatable("g2")));
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);

    service.update("o1", req);

    assertThat(request.getCastId()).isEqualTo("g2");
    assertThat(request.getStatus()).isEqualTo(OrderStatus.CREATED);
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
    when(nominatableCast.find(STORE_ID, "g2")).thenReturn(Optional.of(nominatable("g2")));
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
    when(nominatableCast.find(STORE_ID, "g2")).thenReturn(Optional.of(nominatable("g2")));
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
    when(nominatableCast.find(STORE_ID, "g2")).thenReturn(Optional.of(nominatable("g2")));
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
    when(nominatableCast.find(STORE_ID, "g2")).thenReturn(Optional.of(nominatable("g2")));
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);
    req.setStatus("GARBAGE");

    assertThatThrownBy(() -> service.update("o1", req)).isInstanceOf(ServiceException.class);
  }

  @Test
  void updateRejectsSwitchingToACastThatIsNotNominatable() {
    // 対象は店舗スタッフなので、列挙を防ぐ 404 ではなく理由と対処の分かる 400 で返す。
    // 成立しない理由（不在・他店舗・在籍停止）の判定は NominatableCastLookupTest が持つ
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(nominatableCast.find(STORE_ID, "none")).thenReturn(Optional.empty());
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("none");
    req.setReceptionistId(2L);

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(ServiceException.class)
        .isNotInstanceOf(NotFoundException.class)
        .hasMessageContaining("在籍中のキャスト");
    // 撥ねる要求は集約を触る前に止める（拒否の健全さをトランザクションの巻き戻しだけに委ねない）
    assertThat(existing.getCastId()).isNull();
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateLeavesAnUnchangedNominationAlone() {
    // この経路は指名済みの受注に cast_id の再送を必須にしている。据え置きにまで在籍中を要求すると、
    // 指名者が在籍停止になった確定済みの受注が人数・備考の修正も完了への遷移もできなくなる
    Order confirmed =
        Order.builder()
            .status(OrderStatus.CONFIRMED)
            .castId("g1")
            .receptionistId(3L)
            .pax(2)
            .build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(paxAndRemarksPatch(5, null));
    when(platformUserRepository.findById(3L)).thenReturn(Optional.of(authorizedReceptionist()));
    stubReservationRequestUpdateResponse();

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g1");
    req.setReceptionistId(3L);
    req.setPax(5);

    service.update("o1", req);

    assertThat(confirmed.getPax()).isEqualTo(5);
    assertThat(confirmed.getCastId()).isEqualTo("g1");
    verify(nominatableCast, never()).find(any(), anyString());
  }

  @Test
  void updateRejectsReceptionistAuthorizedForDifferentStore() {
    Order existing = Order.builder().status(OrderStatus.CREATED).build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
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

  /** 人数と備考だけを差し替える部分更新コマンド（汎用更新の典型的な編集）。 */
  private static OrderPatch paxAndRemarksPatch(Integer pax, String remarks) {
    return new OrderPatch(null, null, pax, null, null, null, null, null, null, null, remarks, null);
  }

  @Test
  void updateEditsConfirmedNominationFreeOrderWithoutSettingCast() {
    // 指名を外したまま確定した受注は、キャストを作り出さずに人数・備考を直せなければならない
    Order confirmed =
        reservationRequest().status(OrderStatus.CONFIRMED).receptionistId(3L).pax(2).build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(paxAndRemarksPatch(5, "人数を直した"));
    when(platformUserRepository.findById(3L)).thenReturn(Optional.of(authorizedReceptionist()));
    stubReservationRequestUpdateResponse();

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setReceptionistId(3L);
    req.setPax(5);
    req.setRemarks("人数を直した");

    service.update("o1", req);

    assertThat(confirmed.getPax()).isEqualTo(5);
    assertThat(confirmed.getRemarks()).isEqualTo("人数を直した");
    assertThat(confirmed.getCastId()).as("指名なしのままであること").isNull();
    verify(nominatableCast, never()).find(any(), anyString());
  }

  @Test
  void updateEditsOrderWhoseReceptionistWasNeverAssigned() {
    // 確定した実行者が受付候補の条件を満たさなければ受付担当は未設定のまま残る。その行も編集できなければならない
    Order confirmed = reservationRequest().status(OrderStatus.CONFIRMED).pax(2).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(paxAndRemarksPatch(4, null));
    stubReservationRequestUpdateResponse();

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setPax(4);

    service.update("o1", req);

    assertThat(confirmed.getPax()).isEqualTo(4);
    assertThat(confirmed.getReceptionistId()).as("未設定のままであること").isNull();
    verify(platformUserRepository, never()).findById(any());
  }

  @Test
  void updateRejectsRemovingAnExistingNomination() {
    // 店舗が起こした受注は必ず指名を持つ。省略で外せると、汎用更新が指名解除の裏口になる
    Order storeOrder = Order.builder().status(OrderStatus.CONFIRMED).castId("g1").pax(2).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(storeOrder));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setPax(9);

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("指名を外すことはできません");
    // 撥ねる要求は集約を触る前に止める（拒否の健全さをトランザクションの巻き戻しだけに委ねない）
    assertThat(storeOrder.getPax()).isEqualTo(2);
    assertThat(storeOrder.getCastId()).isEqualTo("g1");
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateRejectsRemovingAnExistingReceptionist() {
    Order storeOrder =
        Order.builder().status(OrderStatus.CONFIRMED).castId("g1").receptionistId(3L).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(storeOrder));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g1");
    req.setPax(9);

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("受付担当を外すことはできません");
    assertThat(storeOrder.getReceptionistId()).isEqualTo(3L);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateTreatsABlankCastIdAsAnOmittedNomination() {
    // 編集画面の未選択がそのまま空文字で乗ってくる。存在しないキャストとして 404 を返すより、
    // 指名なしの要求として同じ判定（外せない）に載せる方が呼び手にとって意味が通る
    Order storeOrder = Order.builder().status(OrderStatus.CONFIRMED).castId("g1").build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(storeOrder));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("");
    req.setPax(9);

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("指名を外すことはできません");
    verify(nominatableCast, never()).find(any(), anyString());
  }

  /** 予約受付 inbox の読み口が返す 1 行分の projection。 */
  private static OrderView pendingView(String id, OffsetDateTime createdAt) {
    OrderView view = mock(OrderView.class);
    lenient().when(view.getId()).thenReturn(id);
    lenient().when(view.getCreatedAt()).thenReturn(createdAt);
    return view;
  }

  @Test
  void listPendingReservationRequestsDelegatesFilteringToTheQuery() {
    OrderView view = pendingView("o1", RECEIVED_AT);
    OrderResponse res = OrderResponse.builder().id("o1").build();
    when(orderRepository.findPendingReservationRequestViews(any(Limit.class)))
        .thenReturn(List.of(view));
    when(orderMapper.toResponse(view)).thenReturn(res);

    assertThat(service.listPendingReservationRequests(null, 20).content()).containsExactly(res);
    // 受注一覧の先頭ページを取って手元で選り分ける実装だと、確定済みが積み上がった店舗で申請が窓から落ちる
    verify(orderRepository, never()).findAllViews(any(), any(Pageable.class));
  }

  @Test
  void listPendingReservationRequestsHandsBackTheCursorOfTheLastReturnedRequest() {
    // 上限より 1 件多く返るのが「続きがある」ことの現れ。3 件目は応答に載せない。
    List<OrderView> fetched =
        List.of(
            pendingView("o1", RECEIVED_AT),
            pendingView("o2", RECEIVED_AT.plusMinutes(1)),
            pendingView("o3", RECEIVED_AT.plusMinutes(2)));
    when(orderRepository.findPendingReservationRequestViews(Limit.of(3))).thenReturn(fetched);
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    CursorPage<OrderResponse> page = service.listPendingReservationRequests(null, 2);

    assertThat(page.content()).hasSize(2);
    // 続きの位置は返した最後の行を指す。余分に取った 3 件目を指すと、その行が飛ばされる。
    assertThat(PageCursor.decode(page.nextCursor()))
        .isEqualTo(new PageCursor(RECEIVED_AT.plusMinutes(1).toString(), "o2"));
  }

  @Test
  void listPendingReservationRequestsReportsNoCursorWhenNothingFollows() {
    List<OrderView> fetched = List.of(pendingView("o1", RECEIVED_AT));
    when(orderRepository.findPendingReservationRequestViews(Limit.of(3))).thenReturn(fetched);
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    assertThat(service.listPendingReservationRequests(null, 2).nextCursor()).isNull();
  }

  @Test
  void listPendingReservationRequestsResumesFromTheGivenCursorInsteadOfAnOffset() {
    String cursor = new PageCursor(RECEIVED_AT.toString(), "o1").encode();
    when(orderRepository.findPendingReservationRequestViewsAfter(
            eq(RECEIVED_AT), eq("o1"), any(Limit.class)))
        .thenReturn(List.of());

    assertThat(service.listPendingReservationRequests(cursor, 20).content()).isEmpty();
    // 位置を件数で指す読み口へ落ちると、手前の申請が処理で消えた分だけ境界の申請を飛ばす
    verify(orderRepository, never()).findPendingReservationRequestViews(any(Limit.class));
  }

  @Test
  void listPendingReservationRequestsRejectsAMalformedCursor() {
    assertThatThrownBy(() -> service.listPendingReservationRequests("not-a-cursor", 20))
        .isInstanceOf(ServiceException.class);
    verify(orderRepository, never()).findPendingReservationRequestViewsAfter(any(), any(), any());
  }

  @Test
  void listPendingReservationRequestsCapsTheRequestedSize() {
    when(orderRepository.findPendingReservationRequestViews(any(Limit.class)))
        .thenReturn(List.of());

    service.listPendingReservationRequests(null, 10_000);

    // 1 回の応答は抑える。続きはカーソルで辿れるので、抑えても到達性は落ちない。
    verify(orderRepository).findPendingReservationRequestViews(Limit.of(CursorPage.MAX_SIZE + 1));
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

  private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 8, 10);

  /** 指名付きの申請（利用日・受付担当あり）。 */
  private Order nominatedRequest() {
    Order request =
        reservationRequest().castId("cast-1").receptionistId(3L).businessDate(REQUEST_DATE).build();
    request.setStoreId(STORE_ID);
    return request;
  }

  @Test
  void confirmRejectsWhenNominatedCastIsNoLongerActive() {
    // 申請から確定までの間に在籍停止になった指名は、そのまま確定させない
    Order request = nominatedRequest();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(nominatableCast.find(STORE_ID, "cast-1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirm("o1", "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("在籍中でない");
    assertThat(request.getStatus()).isEqualTo(OrderStatus.CREATED);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void confirmRejectsWhenNominatedCastLostTheConfirmedShift() {
    // 確定シフトが取り消し・未確定化された指名も、そのまま確定させない
    Order request = nominatedRequest();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(nominatableCast.find(STORE_ID, "cast-1")).thenReturn(Optional.of(nominatable("cast-1")));
    when(confirmedShiftLookupService.hasConfirmedShift(STORE_ID, "cast-1", REQUEST_DATE))
        .thenReturn(false);

    assertThatThrownBy(() -> service.confirm("o1", "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("確定シフトが無い");
    assertThat(request.getStatus()).isEqualTo(OrderStatus.CREATED);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void confirmProceedsWhenNominationStillHolds() {
    Order request = nominatedRequest();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(nominatableCast.find(STORE_ID, "cast-1")).thenReturn(Optional.of(nominatable("cast-1")));
    when(confirmedShiftLookupService.hasConfirmedShift(STORE_ID, "cast-1", REQUEST_DATE))
        .thenReturn(true);
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.confirm("o1", "staff@kizuna.test");

    assertThat(request.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
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

  /** 編集後の応答組み立て（読み口 → DTO）だけを満たす stub。編集そのものの検証は集約の状態で行う。 */
  private void stubReservationRequestUpdateResponse() {
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());
  }

  private ReservationRequestUpdateRequest reservationRequestUpdate(
      Long receptionistId, String castId, Integer pax, String remarks) {
    ReservationRequestUpdateRequest req = new ReservationRequestUpdateRequest();
    req.setReceptionistId(receptionistId);
    req.setCastId(castId);
    req.setPax(pax);
    req.setRemarks(remarks);
    return req;
  }

  @Test
  void updateReservationRequestEditsNominationFreeRequestWithoutSettingCast() {
    // 指名なしの申請は、キャストを埋めずに人数・備考・受付担当を直せなければならない
    Order request = reservationRequest().pax(2).build();
    request.setStoreId(STORE_ID);
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));
    stubReservationRequestUpdateResponse();

    service.updateReservationRequest("o1", reservationRequestUpdate(2L, null, 4, "人数変更"));

    assertThat(request.getPax()).isEqualTo(4);
    assertThat(request.getRemarks()).isEqualTo("人数変更");
    assertThat(request.getReceptionistId()).isEqualTo(2L);
    assertThat(request.getCastId()).as("指名なしのままであること").isNull();
    verify(nominatableCast, never()).find(any(), anyString());
  }

  @Test
  void updateReservationRequestKeepsNominationWhenTheCastIsSentBack() {
    Order request = nominatedRequest();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(nominatableCast.find(STORE_ID, "cast-1")).thenReturn(Optional.of(nominatable("cast-1")));
    stubReservationRequestUpdateResponse();

    service.updateReservationRequest("o1", reservationRequestUpdate(null, "cast-1", 3, null));

    assertThat(request.getCastId()).isEqualTo("cast-1");
    assertThat(request.getPax()).isEqualTo(3);
    // 当日の確定シフトは確定時だけが見る。先の日付の申請は編集時点で未確定なのが通常のため
    verify(confirmedShiftLookupService, never()).hasConfirmedShift(any(), any(), any());
  }

  @Test
  void updateReservationRequestClearsNominationWhenTheCastIsOmitted() {
    // 無効になった指名を確定前に外す導線。省略が「変更しない」だと外す手段が無くなる
    Order request = nominatedRequest();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    stubReservationRequestUpdateResponse();

    service.updateReservationRequest("o1", reservationRequestUpdate(null, null, 2, null));

    assertThat(request.getCastId()).isNull();
    assertThat(request.getReceptionistId()).as("受付担当も同じく外せること").isNull();
    assertThat(request.getRequesterMemberCode())
        .as("申請者のスナップショットは指名解除で壊れないこと")
        .isEqualTo("123456789012");
    assertThat(request.getReceptionRoute()).isEqualTo(ReceptionRoute.WEB);
    verify(nominatableCast, never()).find(any(), anyString());
  }

  @Test
  void updateReservationRequestRejectsACastThatIsNotNominatable() {
    // 対象は店舗スタッフなので、確定時の再検証と同じく列挙を防ぐ 404 ではなく対処の分かる 400 で返す。
    // 成立しない理由（不在・他店舗・在籍停止）の判定は NominatableCastLookupTest が持つ
    Order request = nominatedRequest();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(nominatableCast.find(STORE_ID, "cast-2")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.updateReservationRequest(
                    "o1", reservationRequestUpdate(null, "cast-2", 2, null)))
        .isInstanceOf(ServiceException.class)
        .isNotInstanceOf(NotFoundException.class)
        .hasMessageContaining("指名を外してください");
    // 撥ねる要求は集約を触らない — 拒否の健全さをトランザクションの巻き戻しだけに委ねない
    assertThat(request.getCastId()).as("元の指名が残ること").isEqualTo("cast-1");
    verify(orderRepository, never()).save(any(Order.class));
    // 判定は申請自身の店舗で行う。周囲の店舗文脈へ暗黙に頼ると、平台経由の実行で別店舗の候補が通りうる
    verify(nominatableCast).find(STORE_ID, "cast-2");
    verify(storeContext, never()).getStoreId();
  }

  @Test
  void updateReservationRequestRejectsAlreadyProcessedRequests() {
    // 確定後は通常の受注として汎用更新が受け持つ。ここを通せば確定済みの受注から指名を外せてしまう
    Order confirmed = reservationRequest().status(OrderStatus.CONFIRMED).castId("cast-1").build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));

    assertThatThrownBy(
            () ->
                service.updateReservationRequest(
                    "o1", reservationRequestUpdate(null, null, 2, null)))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("編集できません");
    assertThat(confirmed.getCastId()).isEqualTo("cast-1");
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateReservationRequestRejectsStoreOriginatedOrders() {
    // 申請専用の収口。店舗が起こした受注は ID を知っていても可空の契約では変更させない
    Order storeOrder = Order.builder().status(OrderStatus.CREATED).castId("cast-1").build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(storeOrder));

    assertThatThrownBy(
            () ->
                service.updateReservationRequest(
                    "o1", reservationRequestUpdate(null, null, 2, null)))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("予約申請が見つかりません");
    assertThat(storeOrder.getCastId()).isEqualTo("cast-1");
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateReservationRequestRejectsReceptionistOfAnotherStore() {
    Order request = reservationRequest().build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(platformUserRepository.findById(2L))
        .thenReturn(
            Optional.of(receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L))));

    assertThatThrownBy(
            () ->
                service.updateReservationRequest("o1", reservationRequestUpdate(2L, null, 2, null)))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void deleteRemovesIfExists() {
    Order storeOrder = Order.builder().status(OrderStatus.CREATED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(storeOrder));
    service.delete("o1");
    verify(orderRepository).deleteById("o1");
  }

  @Test
  void deleteThrowsWhenMissing() {
    when(orderRepository.findById("nope")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete("nope")).isInstanceOf(NotFoundException.class);
  }

  @Test
  void deleteRejectsPendingReservationRequests() {
    // 未確定の申請を削除すると CANCELLED の記録が残らず会員の履歴からも消える — 謝絶へ誘導する
    Order pending = reservationRequest().build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.delete("o1"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("謝絶で扱ってください");
    verify(orderRepository, never()).deleteById(anyString());
  }

  @Test
  void deleteAllowsProcessedMemberOrders() {
    // 確定・謝絶を経た後の行は通常の受注として削除の管理操作を受け付ける
    Order cancelled = reservationRequest().status(OrderStatus.CANCELLED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(cancelled));
    service.delete("o1");
    verify(orderRepository).deleteById("o1");
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
  void listCastCandidatesReturnsIdAndNameOfTheStoresNominatableCasts() {
    // 応答は下拉に要る最小限だけ。キャスト管理の応答を流用すると招待状態やカスタム項目まで付いてくる
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(nominatableCast.searchCandidates(STORE_ID, "花"))
        .thenReturn(List.of(nominatable("cast-1")));

    List<OrderCastCandidateResponse> result = service.listCastCandidates("花");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo("cast-1");
    assertThat(result.get(0).getName()).isEqualTo("指名キャスト");
  }

  @Test
  void listCastCandidatesSharesThePredicateWithTheWriteSide() {
    // 候補一覧と書き込み時の指名検証が別の条件になると、候補に出るのに保存で撥ねられる選択が生まれる
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(nominatableCast.searchCandidates(STORE_ID, null)).thenReturn(List.of());

    assertThat(service.listCastCandidates(null)).isEmpty();

    verify(nominatableCast).searchCandidates(STORE_ID, null);
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
