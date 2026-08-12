package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.Cast;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.order.api.dto.OrderCastCandidateResponse;
import com.kizuna.order.api.dto.OrderCompletionPreviewResponse;
import com.kizuna.order.api.dto.OrderCompletionRequest;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.api.dto.ReservationRequestUpdateRequest;
import com.kizuna.order.domain.IllegalOrderStateTransitionException;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionSource;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderPatch;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.OrderView;
import com.kizuna.order.domain.ReceptionRoute;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
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
import org.mockito.InOrder;
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
  @Mock OrderAttributionRepository orderAttributionRepository;
  @Mock NominatableCastLookup nominatableCast;
  @Mock ConfirmedShiftLookupService confirmedShiftLookupService;
  @Mock PointLedgerService pointLedgerService;
  @Mock PlatformUserRepository platformUserRepository;
  @Mock RoleRepository roleRepository;
  @Mock StoreContext storeContext;
  @Mock OrderMapper orderMapper;

  @InjectMocks OrderService service;

  @Captor ArgumentCaptor<Order> orderCaptor;
  @Captor ArgumentCaptor<Customer> customerCaptor;
  @Captor ArgumentCaptor<CustomerMemberLink> linkCaptor;

  private static final long STORE_ID = 1L;

  /** 予約受付 inbox の並びの鍵になる受付時刻。 */
  private static final OffsetDateTime RECEIVED_AT =
      OffsetDateTime.parse("2026-08-04T10:00:00+09:00");

  private OrderPatch emptyPatch() {
    return new OrderPatch(null, null, null, null, null, null, null, null, null, null);
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
    // 保存で採番される id（@SnowflakeId）。受注はこの id で顧客に着く
    newCustomer.setId("c-new");

    when(storeContext.getStoreId()).thenReturn(1L);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(customerRepository.findByPhoneNumberAndStoreId("09012345678", 1L)).thenReturn(List.of());
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
    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isEqualTo("c-new");
    // 台帳に行を起こした以上、受注側の写しは要らない
    assertThat(orderCaptor.getValue().getContactName()).isNull();
    assertThat(orderCaptor.getValue().getContactPhoneNumber()).isNull();
  }

  @Test
  void createLinksTheOnlyCustomerMatchingThePhone() {
    OrderCreateRequest req = phoneOrderRequest("09012345678", "常連さん");

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(customerRepository.findByPhoneNumberAndStoreId("09012345678", STORE_ID))
        .thenReturn(List.of(customerWithId("c1")));
    stubCreateHappyPath();

    service.create(req);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isEqualTo("c1");
    // 台帳の行が連絡先を持つので、受注側の写しは残さない
    assertThat(orderCaptor.getValue().getContactName()).isNull();
    verify(customerRepository, never()).save(any());
  }

  @Test
  void createLeavesTheCustomerUnsetWhenThePhoneMatchesSeveral() {
    // 同店同号は正規に起こりうる（同伴者の連絡先共有・移行データ）。一致行に会員関連付きの行が
    // あり得る以上、機械が 1 行を選ぶのは誤帰属の入口なので、自動照合を断念して顧客未設定で成立させる
    OrderCreateRequest req = phoneOrderRequest("09012345678", "重複照合の来客");

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(customerRepository.findByPhoneNumberAndStoreId("09012345678", STORE_ID))
        .thenReturn(List.of(customerWithId("c1"), customerWithId("c2")));
    stubCreateHappyPath();

    service.create(req);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isNull();
    // 顧客を起こして重複を増やすこともしない
    verify(customerRepository, never()).save(any());
    // 顧客未設定で成立させる以上、録入された連絡先は受注側に残さないと消える
    assertThat(orderCaptor.getValue().getContactName()).isEqualTo("重複照合の来客");
    assertThat(orderCaptor.getValue().getContactPhoneNumber()).isEqualTo("09012345678");
  }

  @Test
  void createKeepsTheReportedContactWhenNoPhoneIsGiven() {
    // 電話番号を録入しなければ照合も顧客の作成も起きない。名前だけの録入もこの経路を通るので、
    // 写しの条件は「照合が複数一致したか」ではなく「顧客が着いたか」で決める
    OrderCreateRequest req = phoneOrderRequest(null, "電話番号なしの来客");

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    stubCreateHappyPath();

    service.create(req);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isNull();
    assertThat(orderCaptor.getValue().getContactName()).isEqualTo("電話番号なしの来客");
    assertThat(orderCaptor.getValue().getContactPhoneNumber()).isNull();
    verifyNoInteractions(customerRepository);
  }

  private OrderCreateRequest phoneOrderRequest(String phoneNumber, String customerName) {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setPhoneNumber(phoneNumber);
    req.setCustomerName(customerName);
    req.setCastId("g1");
    req.setReceptionistId(1L);
    return req;
  }

  private Customer customerWithId(String id) {
    Customer customer = Customer.builder().phoneNumber("09012345678").build();
    customer.setId(id);
    return customer;
  }

  /** 顧客照合の後に続く検証（指名・受付担当）と応答の組み立てを通す stub。 */
  private void stubCreateHappyPath() {
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
    when(platformUserRepository.findById(1L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());
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
  void updateAllowsCancellationAfterConfirmation() {
    // 受付経路と申請者スナップショットは確定後も残るため、「申請かどうか」だけで遷移を塞ぐと
    // 会員起点の受注がキャンセルへ一切進めなくなる。ガードは未確定（CREATED）に限る
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
    req.setStatus("CANCELLED");

    service.update("o1", req);

    assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void updateRejectsTransitionToCompleted() {
    // 完了は会計金額の確定とポイント台帳への記帳と不可分。汎用更新から遷移できると、会計もポイントも
    // 入らないまま完了した受注が成立する
    Order confirmedOrder = Order.builder().status(OrderStatus.CONFIRMED).castId("g1").build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmedOrder));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g1");
    req.setStatus("COMPLETED");

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("完了処理でのみ");
    assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(orderRepository, never()).save(any(Order.class));
    verify(pointLedgerService, never()).grantForOrder(anyLong(), any(), any(), anyInt(), any());
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
        .thenReturn(new OrderPatch(null, null, null, null, null, null, "新しい割引名", null, null, null));
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
    // 遷移表そのものの判定が汎用更新に残っていること（完了の専用ガードとは別経路）。
    // 完了で試すと専用ガードが先に撥ねてしまい、遷移表を通らなくなる
    Order existing = Order.builder().status(OrderStatus.CANCELLED).build();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(nominatableCast.find(STORE_ID, "g2")).thenReturn(Optional.of(nominatable("g2")));
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);
    req.setStatus("CONFIRMED");

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThat(existing.getStatus()).isEqualTo(OrderStatus.CANCELLED);
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
    return new OrderPatch(null, null, pax, null, null, null, null, null, remarks, null);
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
            .reason(LinkReason.MEMBER_CODE)
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
    verify(customerRepository, never()).save(any(Customer.class));
    verify(customerMemberLinkRepository, never()).saveAndFlush(any(CustomerMemberLink.class));
  }

  @Test
  void confirmProvisionsALedgerRowAndLinkWhenTheStoreHasNone() {
    // 会員コードを読ませずに申請だけで来店する経路。ここで整備しないと、完了時の会員解決
    // （顧客 → 有効な関連 → 会員）が空振りしてポイントが記帳されない。
    Order request =
        reservationRequest()
            .requesterMemberId(100L)
            .requesterDeclaredName("名乗り太郎")
            .receptionistId(3L)
            .build();
    request.setStoreId(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            STORE_ID, 100L, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    PlatformUser actor = authorizedReceptionist();
    actor.setId(7L);
    when(platformUserRepository.findByEmail("staff@kizuna.test")).thenReturn(Optional.of(actor));
    Customer provisioned = Customer.builder().name("名乗り太郎").build();
    provisioned.setId("cust-new");
    when(customerRepository.save(any(Customer.class))).thenReturn(provisioned);
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.confirm("o1", "staff@kizuna.test");

    verify(customerRepository).save(customerCaptor.capture());
    Customer created = customerCaptor.getValue();
    assertThat(created.getName()).as("台帳行の氏名は本人が名乗った名前であること").isEqualTo("名乗り太郎");
    assertThat(created.getPhoneNumber()).as("申請は電話番号を運ばないこと").isNull();
    assertThat(created.getRank()).as("他の台帳行の作成経路と同じ既定ランクを持つこと").isEqualTo("SILVER");

    verify(customerMemberLinkRepository).saveAndFlush(linkCaptor.capture());
    CustomerMemberLink link = linkCaptor.getValue();
    assertThat(link.getCustomerId()).isEqualTo("cust-new");
    assertThat(link.getMemberId()).isEqualTo(100L);
    assertThat(link.getMemberCode()).as("関連の会員コードは申請時のスナップショットを写すこと").isEqualTo("123456789012");
    assertThat(link.getReason()).isEqualTo(LinkReason.MEMBER_REQUEST);
    assertThat(link.getLinkedBy()).as("確定した実行者が関連の実行者になること").isEqualTo(7L);

    assertThat(request.getCustomerId()).as("受注が整備された顧客に着くこと").isEqualTo("cust-new");
  }

  @Test
  void confirmStillProvisionsWhenTheRequestCarriesNoDeclaredName() {
    // 名乗る名前を持たない申請（この欄の導入前に起きた未確定の申請）でも整備は止めない。氏名の空欄は
    // 店舗が台帳で直せるが、整備を諦めた受注は完了してもポイントが記帳されず、会員に取り戻す経路が無い。
    Order request = reservationRequest().requesterMemberId(100L).receptionistId(3L).build();
    request.setStoreId(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            STORE_ID, 100L, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    PlatformUser actor = authorizedReceptionist();
    actor.setId(7L);
    when(platformUserRepository.findByEmail("staff@kizuna.test")).thenReturn(Optional.of(actor));
    Customer provisioned = Customer.builder().build();
    provisioned.setId("cust-new");
    when(customerRepository.save(any(Customer.class))).thenReturn(provisioned);
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.confirm("o1", "staff@kizuna.test");

    verify(customerRepository).save(customerCaptor.capture());
    assertThat(customerCaptor.getValue().getName()).as("名乗りが無ければ氏名は空のままであること").isNull();
    verify(customerMemberLinkRepository).saveAndFlush(linkCaptor.capture());
    assertThat(linkCaptor.getValue().getReason()).isEqualTo(LinkReason.MEMBER_REQUEST);
    assertThat(request.getCustomerId()).as("記帳先が決まるよう受注は顧客に着くこと").isEqualTo("cust-new");
  }

  @Test
  void confirmProvisionsNothingForAStoreOriginatedRequestWithoutARequester() {
    // 会員行が消えて申請者の会員 ID が欠落した申請は、関連の会員参照を作れない。整備を諦めて
    // 顧客未設定のまま確定させる（無帰属受注は正規の状態）。
    Order request = reservationRequest().receptionistId(3L).build();
    request.setStoreId(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.confirm("o1", "staff@kizuna.test");

    assertThat(request.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(request.getCustomerId()).isNull();
    verify(customerRepository, never()).save(any(Customer.class));
    verify(customerMemberLinkRepository, never()).saveAndFlush(any(CustomerMemberLink.class));
  }

  @Test
  void declineProvisionsNothing() {
    // 整備は確定の効果であって申請の効果ではない。謝絶で台帳に行が生えると、来なかった客が
    // 店舗の台帳に会員として積み上がる。
    Order request =
        reservationRequest()
            .requesterMemberId(100L)
            .requesterDeclaredName("名乗り太郎")
            .receptionistId(3L)
            .build();
    request.setStoreId(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(request));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("o1")).thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(OrderResponse.builder().build());

    service.decline("o1");

    assertThat(request.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(customerRepository, never()).save(any(Customer.class));
    verify(customerMemberLinkRepository, never()).saveAndFlush(any(CustomerMemberLink.class));
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

  private static final long MEMBER_ID = 100L;
  private static final long ACTOR_ID = 7L;

  /** 完了の対象になる確定済みの受注（顧客つき）。 */
  private static Order confirmedOrderWithCustomer() {
    Order order =
        Order.builder().status(OrderStatus.CONFIRMED).customerId("cust-1").castId("cast-1").build();
    order.setStoreId(STORE_ID);
    return order;
  }

  /** 顧客に張られた有効な会員紐づけ。 */
  private static CustomerMemberLink activeLink(Long memberId) {
    return CustomerMemberLink.builder()
        .customerId("cust-1")
        .memberId(memberId)
        .memberCode("123456789012")
        .reason(LinkReason.MEMBER_CODE)
        .linkedBy(3L)
        .linkedAt(OffsetDateTime.now())
        .build();
  }

  private void stubLink(CustomerMemberLink link) {
    when(customerMemberLinkRepository.findByCustomerIdAndStatus("cust-1", LinkStatus.ACTIVE))
        .thenReturn(Optional.of(link));
  }

  private void stubActiveLink(Long memberId) {
    stubLink(activeLink(memberId));
  }

  private void stubActor() {
    PlatformUser actor = authorizedReceptionist();
    actor.setId(ACTOR_ID);
    when(platformUserRepository.findByEmail("staff@kizuna.test")).thenReturn(Optional.of(actor));
  }

  private static OrderCompletionRequest completion(Integer totalFee, Integer usePoints) {
    OrderCompletionRequest request = new OrderCompletionRequest();
    request.setTotalFee(totalFee);
    request.setUsePoints(usePoints);
    return request;
  }

  @Test
  void completeUsesPointsBeforeGrantingThem() {
    // 順序が逆だと、その受注の付与で同じ受注の利用を賄えてしまう
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);
    stubActor();
    when(pointLedgerService.grantForOrder(MEMBER_ID, "o1", STORE_ID, 12000, ACTOR_ID))
        .thenReturn(120);
    stubReservationRequestUpdateResponse();

    service.complete("o1", completion(12000, 300), "staff@kizuna.test");

    InOrder inOrder = inOrder(pointLedgerService);
    inOrder.verify(pointLedgerService).useForOrder(MEMBER_ID, "o1", STORE_ID, 300, ACTOR_ID);
    inOrder.verify(pointLedgerService).grantForOrder(MEMBER_ID, "o1", STORE_ID, 12000, ACTOR_ID);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.getTotalFee()).isEqualTo(12000);
    assertThat(order.getUsedPoints()).isEqualTo(300);
    // 実際に付与された数を受注へ書く。要求された金額から再計算すると、設定変更で台帳とずれる
    assertThat(order.getAutoGrantPoints()).isEqualTo(120);
  }

  @Test
  void completeWithoutPointUsageDoesNotTouchTheUsageLedger() {
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);
    stubActor();
    when(pointLedgerService.grantForOrder(MEMBER_ID, "o1", STORE_ID, 12000, ACTOR_ID))
        .thenReturn(120);
    stubReservationRequestUpdateResponse();

    service.complete("o1", completion(12000, null), "staff@kizuna.test");

    verify(pointLedgerService, never()).useForOrder(anyLong(), any(), any(), anyInt(), any());
    assertThat(order.getUsedPoints()).isZero();
  }

  @Test
  void completeRejectsPointUsageOnANonMemberOrder() {
    // 非会員には台帳そのものが存在しない。利用の指定は黙って 0 に丸めず撥ねる
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    when(customerMemberLinkRepository.findByCustomerIdAndStatus("cust-1", LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.complete("o1", completion(12000, 300), "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("非会員の受注ではポイントを利用できません");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verifyNoInteractions(pointLedgerService);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void completeRejectsPointUsageBeyondTheTotalFee() {
    // 請求を超える割引に相当する利用を台帳へ残さない。完了は取り消せないので、積んでから気づいても戻せない
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);

    assertThatThrownBy(() -> service.complete("o1", completion(0, 100), "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("利用ポイントは会計金額を超えられません");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verifyNoInteractions(pointLedgerService);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void completeAcceptsPointUsageEqualToTheTotalFee() {
    // 全額のポイント払いは通す。境界を閉じると、残高で払い切れる会計だけが完了できなくなる
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);
    stubActor();
    stubReservationRequestUpdateResponse();

    service.complete("o1", completion(300, 300), "staff@kizuna.test");

    verify(pointLedgerService).useForOrder(MEMBER_ID, "o1", STORE_ID, 300, ACTOR_ID);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.getUsedPoints()).isEqualTo(300);
  }

  @Test
  void completeOfANonMemberOrderGrantsNothing() {
    Order order = Order.builder().status(OrderStatus.CONFIRMED).castId("cast-1").build();
    order.setStoreId(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubReservationRequestUpdateResponse();

    service.complete("o1", completion(12000, null), "staff@kizuna.test");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.getAutoGrantPoints()).isZero();
    verifyNoInteractions(pointLedgerService);
    // 顧客が未設定なら押さえる行も引く紐づけも無い
    verify(customerRepository, never()).findByIdForUpdate(any());
    verify(customerMemberLinkRepository, never()).findByCustomerIdAndStatus(any(), any());
  }

  @Test
  void completeRecordsTheAttributionOfAMemberOrder() {
    // 来店履歴は帰属記録だけから読む。完了時に記録が生まれないと、その来店は会員から永久に見えない
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);
    stubActor();
    stubReservationRequestUpdateResponse();

    service.complete("o1", completion(12000, null), "staff@kizuna.test");

    OrderAttribution attribution = savedAttribution();
    assertThat(attribution.getOrderId()).isEqualTo("o1");
    assertThat(attribution.getMemberId()).isEqualTo(MEMBER_ID);
    // 会員コードは帰属時点のスナップショット。会員行が消えた後も誰の来店だったかを読めるようにする
    assertThat(attribution.getMemberCode()).isEqualTo("123456789012");
    assertThat(attribution.getSource()).isEqualTo(OrderAttributionSource.COMPLETION);
    assertThat(attribution.getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
    assertThat(attribution.getAttributedAt()).isNotNull();
  }

  @Test
  void completeRecordsTheAttributionEvenWhenNothingIsGranted() {
    // 帰属は来店可視性の事実でポイントとは独立している。0 円完了は台帳へ行を書かないが記録は生まれる
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);
    stubActor();
    stubReservationRequestUpdateResponse();

    service.complete("o1", completion(0, null), "staff@kizuna.test");

    assertThat(order.getAutoGrantPoints()).isZero();
    assertThat(savedAttribution().getOrderId()).isEqualTo("o1");
  }

  @Test
  void completeOfANonMemberOrderRecordsNoAttribution() {
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    when(customerMemberLinkRepository.findByCustomerIdAndStatus("cust-1", LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    stubReservationRequestUpdateResponse();

    service.complete("o1", completion(12000, null), "staff@kizuna.test");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    verifyNoInteractions(orderAttributionRepository);
  }

  @Test
  void completeOfAnOrderKnownOnlyByItsRequesterRecordsNoAttribution() {
    // 申請者の記録は申請の出所であって帰属ではない。顧客 → 有効な関連 → 会員の一本道が会員に達しない限り
    // 帰属は生まれない（申請者へ fallback すると、台帳行の無い来店ポイントが生まれる）
    Order order =
        Order.builder()
            .status(OrderStatus.CONFIRMED)
            .castId("cast-1")
            .requesterMemberId(MEMBER_ID)
            .requesterMemberCode("123456789012")
            .build();
    order.setStoreId(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubReservationRequestUpdateResponse();

    service.complete("o1", completion(12000, null), "staff@kizuna.test");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    verifyNoInteractions(orderAttributionRepository);
    verifyNoInteractions(pointLedgerService);
  }

  private OrderAttribution savedAttribution() {
    ArgumentCaptor<OrderAttribution> captor = ArgumentCaptor.forClass(OrderAttribution.class);
    verify(orderAttributionRepository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void completeTreatsALinkWhoseMemberIsGoneAsNonMember() {
    // 会員行が消えると FK の SET NULL で紐づけの会員 ID が欠落する。残高の所在が辿れない以上、
    // 紐づけが無いのと同じに扱う（利用の指定は撥ね、付与も起こさない）
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    // 集約の構築時検証は会員 ID を必須にしており、この状態は FK の SET NULL による読み込みでしか生じない
    CustomerMemberLink detached = mock(CustomerMemberLink.class);
    when(detached.getMemberId()).thenReturn(null);
    stubLink(detached);

    assertThatThrownBy(() -> service.complete("o1", completion(12000, 300), "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("非会員の受注ではポイントを利用できません");
    verifyNoInteractions(pointLedgerService);
  }

  @Test
  void completeFailsWhenTheActorIsNoLongerAPlatformUser() {
    // 追記型の台帳では実行者 null が「機構が起こした仕訳」の形。失効した認証セッションによる人手の完了を
    // その形で残さず、台帳を触る前に落とす
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);
    when(platformUserRepository.findByEmail("staff@kizuna.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.complete("o1", completion(12000, 300), "staff@kizuna.test"))
        .isInstanceOf(StaleSessionException.class);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verifyNoInteractions(pointLedgerService);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void completeRejectsAnOrderThatIsNotConfirmed() {
    // 検証は台帳を触るより先。撥ねる要求が仕訳を積んだ後だと、拒否の健全さが巻き戻しだけに掛かる
    Order created = Order.builder().status(OrderStatus.CREATED).customerId("cust-1").build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(created));

    assertThatThrownBy(() -> service.complete("o1", completion(12000, 300), "staff@kizuna.test"))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThat(created.getStatus()).isEqualTo(OrderStatus.CREATED);
    verifyNoInteractions(pointLedgerService);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void completeThrowsWhenOrderMissing() {
    when(orderRepository.findById("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.complete("nope", completion(12000, null), "staff@kizuna.test"))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(pointLedgerService);
  }

  @Test
  void completeResolvesTheMemberUnderTheCustomerRowLock() {
    // 完了は顧客行を押さえてから紐づけを解決する。押さえずに解決すると並行する紐づけの解除・変更とは
    // 何も競合せずに双方が commit でき、受注は取り消せないまま利用と付与だけがずれた会員に残る
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);
    stubActor();
    stubReservationRequestUpdateResponse();

    service.complete("o1", completion(12000, 300), "staff@kizuna.test");

    InOrder inOrder = inOrder(customerRepository, customerMemberLinkRepository);
    inOrder.verify(customerRepository).findByIdForUpdate("cust-1");
    // 紐づけ自体はロック取得後の新しい問い合わせで引く。置換の commit 後ならその新しい行が必ず見える
    inOrder
        .verify(customerMemberLinkRepository)
        .findByCustomerIdAndStatus("cust-1", LinkStatus.ACTIVE);
  }

  @Test
  void completionPreviewResolvesTheMemberWithoutTheCustomerRowLock() {
    // 事前計算は台帳へ積まない読み口。行を押さえると、画面を開いただけの照会が並行する紐づけ解除を
    // コミットまで待たせる
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);

    service.completionPreview("o1", 12000);

    verify(customerMemberLinkRepository).findByCustomerIdAndStatus("cust-1", LinkStatus.ACTIVE);
    verify(customerRepository, never()).findByIdForUpdate(any());
  }

  @Test
  void completionPreviewOfALinkedOrderCarriesTheBalance() {
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);
    when(pointLedgerService.balance(MEMBER_ID)).thenReturn(800L);
    when(pointLedgerService.usageUnit()).thenReturn(100);
    when(pointLedgerService.previewGrant(12000)).thenReturn(120);

    OrderCompletionPreviewResponse preview = service.completionPreview("o1", 12000);

    assertThat(preview.isMemberLinked()).isTrue();
    assertThat(preview.getPointBalance()).isEqualTo(800);
    assertThat(preview.getUsageUnit()).isEqualTo(100);
    // 見込みは確定と同じサービスから引く。独自に計算すると設定変更のたびに結果が食い違う
    assertThat(preview.getGrantPoints()).isEqualTo(120);
  }

  @Test
  void completionPreviewOfAnUnlinkedOrderOmitsTheBalance() {
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    when(customerMemberLinkRepository.findByCustomerIdAndStatus("cust-1", LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(pointLedgerService.usageUnit()).thenReturn(100);

    OrderCompletionPreviewResponse preview = service.completionPreview("o1", 12000);

    assertThat(preview.isMemberLinked()).isFalse();
    assertThat(preview.getPointBalance()).as("非会員に残高は存在しない").isNull();
    // 確定は非会員へ付与しない。見込みが付与を返すと、画面の予定と確定の結果が食い違う
    assertThat(preview.getGrantPoints()).isZero();
    verify(pointLedgerService, never()).previewGrant(anyInt());
    verify(pointLedgerService, never()).balance(anyLong());
  }

  @Test
  void completionPreviewRejectsANegativeFee() {
    // 会計金額は要求パラメータのため契約の下限を持てない。素通りすると負の付与が見込みとして返る
    assertThatThrownBy(() -> service.completionPreview("o1", -1))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("会計金額は 0 以上");
    verifyNoInteractions(pointLedgerService);
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
  void deleteRejectsCompletedOrders() {
    // 完了済みの受注はポイント台帳の仕訳が order_id で参照している。削除すると FK の SET NULL で
    // 付与・利用の根拠だけが静かに失われる
    Order completed = Order.builder().status(OrderStatus.COMPLETED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(completed));

    assertThatThrownBy(() -> service.delete("o1"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("完了済みの受注は削除できません");
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
