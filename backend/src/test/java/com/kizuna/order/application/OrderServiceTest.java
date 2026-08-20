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
import com.kizuna.customer.application.CustomerReferenceResolver;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.order.api.dto.OrderApplicationConfirmationRequest;
import com.kizuna.order.api.dto.OrderApplicationDeclineRequest;
import com.kizuna.order.api.dto.OrderApplicationResponse;
import com.kizuna.order.api.dto.OrderArchiveResponse;
import com.kizuna.order.api.dto.OrderCancellationRequest;
import com.kizuna.order.api.dto.OrderCastCandidateResponse;
import com.kizuna.order.api.dto.OrderCompletionPreviewResponse;
import com.kizuna.order.api.dto.OrderCompletionRequest;
import com.kizuna.order.api.dto.OrderCompletionResponse;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderSummaryResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.api.dto.OrderWorkQueueResponse;
import com.kizuna.order.domain.IllegalOrderStateTransitionException;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.domain.OrderApplicationView;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionSource;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderPatch;
import com.kizuna.order.domain.OrderQueryCriteria;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderReceiptTokenStatus;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderSortKey;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.OrderView;
import com.kizuna.order.domain.ReceptionRoute;
import com.kizuna.order.infrastructure.OrderSearchQuery;
import com.kizuna.order.infrastructure.OrderSearchQuery.OrderedRow;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.settings.application.BusinessDateService;
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
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock OrderApplicationRepository orderApplicationRepository;
  @Mock OrderSearchQuery orderSearchQuery;
  @Mock CustomerRepository customerRepository;
  @Mock CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock CustomerReferenceResolver customerReferenceResolver;
  @Mock OrderAttributionRepository orderAttributionRepository;
  @Mock OrderReceiptTokenRepository orderReceiptTokenRepository;
  @Mock ReceiptTokenGenerator receiptTokenGenerator;
  @Mock NominatableCastLookup nominatableCast;
  @Mock ConfirmedShiftLookupService confirmedShiftLookupService;
  @Mock PointLedgerService pointLedgerService;
  @Mock PlatformUserRepository platformUserRepository;
  @Mock RoleRepository roleRepository;
  @Mock StoreContext storeContext;
  @Mock BusinessDateService businessDateService;
  @Mock OrderMapper orderMapper;

  @InjectMocks OrderService service;

  @Captor ArgumentCaptor<Order> orderCaptor;
  @Captor ArgumentCaptor<Customer> customerCaptor;
  @Captor ArgumentCaptor<CustomerMemberLink> linkCaptor;

  private static final long STORE_ID = 1L;

  /** 作成・確定の実行者。受付担当を明示する要求では補完へ到達しない。 */
  private static final String ACTOR_EMAIL = "staff@kizuna.test";

  private OrderPatch emptyPatch() {
    return patchWith(builder -> builder);
  }

  /** 部分更新コマンドを 1〜2 項目だけ埋めて作る。項目数が多く、位置引数で並べると どの null が何なのか読めなくなるためのテスト用ヘルパー。 */
  private OrderPatch patchWith(UnaryOperator<PatchDraft> draft) {
    return draft.apply(new PatchDraft(null, null, null)).toPatch();
  }

  /** {@link #patchWith} が埋める項目。テストが実際に使うものだけを持つ。 */
  private record PatchDraft(LocalDate businessDate, Integer pax, String discountName) {

    PatchDraft pax(Integer value) {
      return new PatchDraft(businessDate, value, discountName);
    }

    PatchDraft discountName(String value) {
      return new PatchDraft(businessDate, pax, value);
    }

    OrderPatch toPatch() {
      return new OrderPatch(
          businessDate,
          null,
          null,
          pax,
          null,
          null,
          null,
          discountName,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }
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
    OrderSummaryResponse res = OrderSummaryResponse.builder().id("o1").build();
    Page<OrderView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 10), 1);

    when(orderRepository.findAllViews(eq(null), any(Pageable.class))).thenReturn(page);
    when(orderMapper.toSummaryResponse(view)).thenReturn(res);

    Page<OrderSummaryResponse> result = service.list(null, PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getId()).isEqualTo("o1");
  }

  @Test
  void listFiltersByCustomerId() {
    OrderView view = mock(OrderView.class);
    OrderSummaryResponse res = OrderSummaryResponse.builder().id("o1").build();
    Page<OrderView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 10), 1);

    when(orderRepository.findAllViews(eq("c1"), any(Pageable.class))).thenReturn(page);
    when(orderMapper.toSummaryResponse(view)).thenReturn(res);

    Page<OrderSummaryResponse> result = service.list("c1", PageRequest.of(0, 10));
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
    OrderResponse res = OrderResponse.builder().status("CONFIRMED").build();

    when(storeContext.getStoreId()).thenReturn(1L);
    when(orderMapper.toEntity(req)).thenReturn(entity);
    // 指定された顧客は共有の解決口を通って書き込み先になる（そこで行が押さえられる）
    when(customerReferenceResolver.resolveForWrite("c1")).thenReturn("c1");
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
    when(platformUserRepository.findById(1L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    when(orderMapper.toResponse(any(OrderView.class))).thenReturn(res);

    service.create(req, ACTOR_EMAIL);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isEqualTo("c1");
    assertThat(orderCaptor.getValue().getCastId()).isEqualTo("g1");
    assertThat(orderCaptor.getValue().getReceptionistId()).isEqualTo(1L);
  }

  @Test
  void createRejectsACustomerThatCannotBeResolved() {
    // 不在の顧客も他店舗の顧客も、解決口が同じ 404 に落とす（存在の有無は漏れない）
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCustomerId("missing");
    req.setCastId("g1");
    req.setReceptionistId(1L);

    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(customerReferenceResolver.resolveForWrite("missing"))
        .thenThrow(new NotFoundException("顧客が見つかりません"));

    assertThatThrownBy(() -> service.create(req, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
    verify(orderRepository, never()).save(any(Order.class));
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
    when(customerRepository.findAliveIdsByPhoneNumberAndStoreId("09012345678", 1L))
        .thenReturn(List.of());
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
    when(platformUserRepository.findById(1L)).thenReturn(Optional.of(authorizedReceptionist()));

    when(orderMapper.toCustomer(req)).thenReturn(newCustomer);

    when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    stubRowResponses();

    service.create(req, ACTOR_EMAIL);

    verify(customerRepository).save(customerCaptor.capture());
    assertThat(customerCaptor.getValue().getPhoneNumber()).isEqualTo("09012345678");
    // 起こしたばかりの行は他の経路の書き換えに晒されていないので、解決を経ずに着ける
    verifyNoInteractions(customerReferenceResolver);
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
    when(customerRepository.findAliveIdsByPhoneNumberAndStoreId("09012345678", STORE_ID))
        .thenReturn(List.of("c1"));
    // 照合は行を押さえない問い合わせなので、着ける前に共有の解決口を通る
    when(customerReferenceResolver.resolveForWrite("c1")).thenReturn("c1");
    stubCreateHappyPath();

    service.create(req, ACTOR_EMAIL);

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
    when(customerRepository.findAliveIdsByPhoneNumberAndStoreId("09012345678", STORE_ID))
        .thenReturn(List.of("c1", "c2"));
    stubCreateHappyPath();

    service.create(req, ACTOR_EMAIL);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isNull();
    // 顧客を起こして重複を増やすこともしない
    verify(customerRepository, never()).save(any());
    // どの行にも着けない以上、一致行のどれも押さえない
    verifyNoInteractions(customerReferenceResolver);
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

    service.create(req, ACTOR_EMAIL);

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

  /** 顧客照合の後に続く検証（指名・受付担当）と応答の組み立てを通す stub。 */
  private void stubCreateHappyPath() {
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
    when(platformUserRepository.findById(1L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    stubRowResponses();
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

    assertThatThrownBy(() -> service.create(req, ACTOR_EMAIL))
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

    assertThatThrownBy(() -> service.create(req, ACTOR_EMAIL))
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

    assertThatThrownBy(() -> service.create(req, ACTOR_EMAIL))
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

    assertThatThrownBy(() -> service.create(req, ACTOR_EMAIL))
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

    assertThatThrownBy(() -> service.create(req, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("受付担当者が見つかりません");
    verify(orderRepository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = ReceptionRoute.class,
      names = {"MEMBER_WEB", "GUEST_WEB"})
  void createRejectsEveryWebApplicationReceptionRoute(ReceptionRoute route) {
    // Web 申請の経路は申請の確定だけが書く値。代理入力で経路記録を偽装させない
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");
    req.setReceptionistId(1L);
    req.setReceptionRoute(route);

    assertThatThrownBy(() -> service.create(req, ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("Web 申請");
    // 台帳を触るより先に撥ねる（拒否の健全さを巻き戻しに委ねない）
    verifyNoInteractions(orderMapper, customerRepository, customerReferenceResolver);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createAcceptsPhoneAsTheReceptionRoute() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");
    req.setReceptionistId(1L);
    req.setReceptionRoute(ReceptionRoute.PHONE);

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    stubCreateHappyPath();

    service.create(req, ACTOR_EMAIL);

    verify(orderRepository).save(any(Order.class));
  }

  @Test
  void createAssignsTheActorAsReceptionistWhenTheSlotIsOmitted() {
    // 受付担当は既定で実行者本人。毎回自分を探して選ぶ手間を省く
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");

    PlatformUser actor = authorizedReceptionist();
    actor.setId(7L);
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
    when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(actor));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    stubRowResponses();

    service.create(req, ACTOR_EMAIL);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getReceptionistId()).isEqualTo(7L);
  }

  @Test
  void createRejectsAnOmittedReceptionistWhenTheActorIsNotEligible() {
    // 受付候補でない実行者（店舗を授権する HQ 管理者など）は黙って未設定にせず撥ねる。
    // 確定操作が未設定を許すのは会員の申請が既に成立しているからで、こちらは受注をこれから起こす
    OrderCreateRequest req = new OrderCreateRequest();
    req.setCastId("g1");

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
    when(nominatableCast.find(STORE_ID, "g1")).thenReturn(Optional.of(nominatable("g1")));
    when(platformUserRepository.findByEmail(ACTOR_EMAIL))
        .thenReturn(
            Optional.of(receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L))));

    assertThatThrownBy(() -> service.create(req, ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .isNotInstanceOf(NotFoundException.class)
        .hasMessageContaining("受付担当を指定してください");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void updateModifiesAssociations() {
    Order existing = Order.builder().status(OrderStatus.CONFIRMED).build();

    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(nominatableCast.find(STORE_ID, "g2")).thenReturn(Optional.of(nominatable("g2")));
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    stubRowResponses();

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);

    service.update("o1", req);

    assertThat(existing.getCastId()).isEqualTo("g2");
    assertThat(existing.getReceptionistId()).isEqualTo(2L);
  }

  @Test
  void updateRejectsTerminalOrders() {
    // 汎用更新は終端状態を全拒する。受注には変更履歴が無いので、ここを開けておくことは
    // 「誰が・いつ・何を」のどれも残さずに確定した記録を動かす裏口になる（ADR 0013）
    for (OrderStatus terminal : List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED)) {
      Order order = Order.builder().status(terminal).pax(2).build();
      when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

      OrderUpdateRequest req = new OrderUpdateRequest();
      req.setPax(9);

      assertThatThrownBy(() -> service.update("o1", req))
          .as("状態 %s の受注が編集を撥ねること", terminal)
          .isInstanceOf(ServiceException.class)
          .hasMessageContaining("完了・取消済み");
      assertThat(order.getPax()).as("撥ねた要求が内容を書き換えないこと").isEqualTo(2);
    }
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateAppliesPatchFields() {
    Order existing = Order.builder().status(OrderStatus.CONFIRMED).build();

    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(patchWith(builder -> builder.discountName("新しい割引名")));
    when(nominatableCast.find(STORE_ID, "g2")).thenReturn(Optional.of(nominatable("g2")));
    when(platformUserRepository.findById(2L)).thenReturn(Optional.of(authorizedReceptionist()));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    stubRowResponses();

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setCastId("g2");
    req.setReceptionistId(2L);

    service.update("o1", req);

    assertThat(existing.getDiscountName()).isEqualTo("新しい割引名");
  }

  private OrderCancellationRequest cancellationRequest(String reason) {
    OrderCancellationRequest req = new OrderCancellationRequest();
    req.setReason(reason);
    return req;
  }

  @Test
  void cancelRecordsTheReasonActorAndTime() {
    // 汎用更新から状態を動かす裏口を閉じた代わりの専用の口。「取消できること」はここへ移設した
    Order confirmed = Order.builder().status(OrderStatus.CONFIRMED).build();
    PlatformUser actor = authorizedReceptionist();
    actor.setId(7L);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));
    when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(actor));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

    service.cancel("o1", cancellationRequest("客都合。当日夕方に体調不良の連絡あり"), ACTOR_EMAIL);

    assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(confirmed.getCancelledReason()).isEqualTo("客都合。当日夕方に体調不良の連絡あり");
    assertThat(confirmed.getCancelledBy()).isEqualTo(7L);
    assertThat(confirmed.getCancelledAt()).isNotNull();
  }

  @Test
  void cancelRejectsAnOrderThatIsNotConfirmed() {
    // 「不正な遷移が撥ねられること」の移設先。未処理の予約申請は申請側の謝絶が、誤完了の救済は別の経路が受け持つ
    for (OrderStatus status : List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED)) {
      Order order = Order.builder().status(status).build();
      when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
      PlatformUser actor = authorizedReceptionist();
      actor.setId(7L);
      lenient()
          .when(platformUserRepository.findByEmail(ACTOR_EMAIL))
          .thenReturn(Optional.of(actor));

      assertThatThrownBy(() -> service.cancel("o1", cancellationRequest("理由"), ACTOR_EMAIL))
          .as("状態 %s からの取消が拒否されること", status)
          .isInstanceOf(IllegalOrderStateTransitionException.class);
      assertThat(order.getStatus()).isEqualTo(status);
    }
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void cancelFailsWhenTheActorIsNoLongerAPlatformUser() {
    // 実行者不明のまま取消を通すと、失効した認証セッションによる操作が記録から区別できなくなる
    Order confirmed = Order.builder().status(OrderStatus.CONFIRMED).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));
    when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancel("o1", cancellationRequest("理由"), ACTOR_EMAIL))
        .isInstanceOf(StaleSessionException.class);
    assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateCorrectsTheContactOfAnUnlinkedOrder() {
    // 顧客の着いていない受注は録入された連絡先が唯一の名乗りなので、誤記はここでしか直せない
    Order existing = Order.builder().status(OrderStatus.CONFIRMED).contactName("誤記の名前").build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class))).thenReturn(emptyPatch());
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    stubRowResponses();

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setContactName("正しい名前");
    req.setContactPhoneNumber("09099998888");

    service.update("o1", req);

    assertThat(existing.getContactName()).isEqualTo("正しい名前");
    assertThat(existing.getContactPhoneNumber()).isEqualTo("09099998888");
  }

  @Test
  void updateRejectsContactCorrectionOnALinkedOrder() {
    // 顧客が着いていれば名乗りの正本は台帳の行。黙って捨てると送り手は直ったと誤解したまま誤記が残る
    Order linked = Order.builder().status(OrderStatus.CONFIRMED).customerId("c1").pax(2).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(linked));

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setContactName("受注側から書こうとした名前");

    assertThatThrownBy(() -> service.update("o1", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("顧客詳細");
    assertThat(linked.getContactName()).isNull();
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateLeavesALinkedOrderEditableWhenNoContactIsSent() {
    // 連絡先を送らない編集まで巻き添えで撥ねない（顧客が着いた受注の人数・備考は直せる必要がある）
    Order linked = Order.builder().status(OrderStatus.CONFIRMED).customerId("c1").pax(2).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(linked));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(patchWith(builder -> builder.pax(9)));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    stubRowResponses();

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setPax(9);

    service.update("o1", req);

    assertThat(linked.getPax()).isEqualTo(9);
  }

  @Test
  void updateRejectsSwitchingToACastThatIsNotNominatable() {
    // 対象は店舗スタッフなので、列挙を防ぐ 404 ではなく理由と対処の分かる 400 で返す。
    // 成立しない理由（不在・他店舗・在籍停止）の判定は NominatableCastLookupTest が持つ
    Order existing = Order.builder().status(OrderStatus.CONFIRMED).build();
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
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(paxAndRemarksPatch(5, null));
    stubWriteBackResponse();

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
  void updateLeavesAnUnchangedReceptionistAlone() {
    // 指名と同じ理由。この経路は受付担当の付いた受注に receptionist_id の再送を必須にしているため、
    // 据え置きにまで適格を要求すると、担当者が退職・権限剥奪・他店異動になった受注が人数・備考の
    // 修正もできなくなる。据え置かれた受付担当は割り当てた時点で検証済みで、FK も掛かっている
    Order confirmed =
        Order.builder().status(OrderStatus.CONFIRMED).receptionistId(3L).pax(2).build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(paxAndRemarksPatch(5, null));
    stubWriteBackResponse();

    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setReceptionistId(3L);
    req.setPax(5);

    service.update("o1", req);

    assertThat(confirmed.getPax()).isEqualTo(5);
    assertThat(confirmed.getReceptionistId()).isEqualTo(3L);
    // 適格の問い合わせそのものへ行かないこと。「適格な利用者を返す」で緑にすると、
    // 適格でなくなった担当者で撥ねる退行を見逃す
    verify(platformUserRepository, never()).findById(anyLong());
  }

  @Test
  void updateRejectsReceptionistAuthorizedForDifferentStore() {
    Order existing = Order.builder().status(OrderStatus.CONFIRMED).build();
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
    Order existing = Order.builder().status(OrderStatus.CONFIRMED).build();
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
    return new OrderPatch(
        null, null, null, pax, null, null, null, null, null, null, null, null, null, remarks, null);
  }

  @Test
  void updateEditsConfirmedNominationFreeOrderWithoutSettingCast() {
    // 指名を外したまま確定した受注は、キャストを作り出さずに人数・備考を直せなければならない
    Order confirmed =
        Order.builder()
            .status(OrderStatus.CONFIRMED)
            .receptionRoute(ReceptionRoute.MEMBER_WEB)
            .requesterMemberCode("123456789012")
            .receptionistId(3L)
            .pax(2)
            .build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(paxAndRemarksPatch(5, "人数を直した"));
    stubWriteBackResponse();

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
    Order confirmed =
        Order.builder()
            .status(OrderStatus.CONFIRMED)
            .receptionRoute(ReceptionRoute.MEMBER_WEB)
            .requesterMemberCode("123456789012")
            .pax(2)
            .build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(confirmed));
    when(orderMapper.toPatch(any(OrderUpdateRequest.class)))
        .thenReturn(paxAndRemarksPatch(4, null));
    stubWriteBackResponse();

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

  /** 作業キューの読み口が返す 1 行分の projection。並びの鍵になる項目だけを埋める。 */
  private static OrderView queueView(String id, Integer pax) {
    OrderView view = mock(OrderView.class);
    lenient().when(view.getId()).thenReturn(id);
    lenient().when(view.getPax()).thenReturn(pax);
    return view;
  }

  private static OrderQueryCriteria queueCriteria() {
    return new OrderQueryCriteria(
        Set.of(OrderStatus.CONFIRMED), null, null, OrderSortKey.PAX, false);
  }

  @Test
  void listWorkQueueDelegatesFilteringToTheQuery() {
    OrderView view = queueView("o1", 2);
    OrderWorkQueueResponse res = OrderWorkQueueResponse.builder().id("o1").build();
    when(orderSearchQuery.findRows(
            any(OrderQueryCriteria.class), nullable(PageCursor.class), anyInt()))
        .thenReturn(List.of(new OrderedRow("o1", 2)));
    when(orderRepository.findViewsByIds(List.of("o1"))).thenReturn(List.of(view));
    when(orderMapper.toWorkQueueResponse(view)).thenReturn(res);

    assertThat(service.listWorkQueue(queueCriteria(), null, 20).content()).containsExactly(res);
    // 受注一覧の先頭ページを取って手元で選り分ける実装だと、完了が積み上がった店舗で未対応が窓から落ちる
    verify(orderRepository, never()).findAllViews(any(), any(Pageable.class));
  }

  @Test
  void listWorkQueueKeepsTheOrderTheQueryDecided() {
    // 行の中身は id の集合で引き直すため、返る順は問い合わせの並びと一致しない。並べ直さないと
    // 画面の並び替えが効かないまま「並び替えたつもり」になる
    List<OrderView> views = List.of(queueView("o1", 1), queueView("o2", 2));
    when(orderSearchQuery.findRows(
            any(OrderQueryCriteria.class), nullable(PageCursor.class), anyInt()))
        .thenReturn(List.of(new OrderedRow("o2", 2), new OrderedRow("o1", 1)));
    when(orderRepository.findViewsByIds(List.of("o2", "o1"))).thenReturn(views);
    when(orderMapper.toWorkQueueResponse(any(OrderView.class)))
        .thenAnswer(
            i ->
                OrderWorkQueueResponse.builder()
                    .id(i.getArgument(0, OrderView.class).getId())
                    .build());

    assertThat(service.listWorkQueue(queueCriteria(), null, 20).content())
        .extracting(OrderWorkQueueResponse::getId)
        .containsExactly("o2", "o1");
  }

  @Test
  void listWorkQueueHandsBackTheCursorOfTheLastReturnedRow() {
    // 上限より 1 件多く返るのが「続きがある」ことの現れ。3 件目は応答に載せない。
    List<OrderView> views = List.of(queueView("o1", 1), queueView("o2", 2), queueView("o3", 3));
    when(orderSearchQuery.findRows(
            any(OrderQueryCriteria.class), nullable(PageCursor.class), eq(3)))
        .thenReturn(
            List.of(new OrderedRow("o1", 1), new OrderedRow("o2", 2), new OrderedRow("o3", 3)));
    when(orderRepository.findViewsByIds(List.of("o1", "o2", "o3"))).thenReturn(views);
    stubRowResponses();

    CursorPage<OrderWorkQueueResponse> page = service.listWorkQueue(queueCriteria(), null, 2);

    assertThat(page.content()).hasSize(2);
    // 続きの位置は返した最後の行を指す。余分に取った 3 件目を指すと、その行が飛ばされる。
    assertThat(PageCursor.decode(page.nextCursor())).isEqualTo(new PageCursor("2", "o2"));
  }

  @Test
  void listWorkQueueCursorCarriesTheSentinelForAnUnsetSortKey() {
    // 並び替えの鍵が未設定の行でも続きの位置を作れなければ、その行の次から先へ進めなくなる
    List<OrderView> views = List.of(queueView("o1", null), queueView("o2", 3));
    when(orderSearchQuery.findRows(
            any(OrderQueryCriteria.class), nullable(PageCursor.class), eq(2)))
        .thenReturn(List.of(new OrderedRow("o1", Integer.MAX_VALUE), new OrderedRow("o2", 3)));
    when(orderRepository.findViewsByIds(List.of("o1", "o2"))).thenReturn(views);
    stubRowResponses();

    CursorPage<OrderWorkQueueResponse> page = service.listWorkQueue(queueCriteria(), null, 1);

    assertThat(PageCursor.decode(page.nextCursor()))
        .isEqualTo(new PageCursor(String.valueOf(Integer.MAX_VALUE), "o1"));
  }

  @Test
  void groupReadsRunInASingleSnapshot() throws NoSuchMethodException {
    // 群読み口は 1 回の応答を作るのに複数の文を投げる。既定の READ COMMITTED では文ごとに断面を
    // 取り直すため、文の間に他者の commit が挟まると同じ応答の中で違う世界を見る（続きが行を飛ばす /
    // 完了済みが作業キューに混じる / 総数が中身と食い違う）。宣言が外れると 3 つとも黙って戻る。
    // 断面が実際に保たれることは OrderGroupReadSnapshotIT が本物の PostgreSQL で見る
    Method workQueue =
        OrderService.class.getMethod(
            "listWorkQueue", OrderQueryCriteria.class, String.class, int.class);
    Method archive =
        OrderService.class.getMethod("listArchive", OrderQueryCriteria.class, Pageable.class);

    for (Method method : List.of(workQueue, archive)) {
      Transactional tx = method.getAnnotation(Transactional.class);
      assertThat(tx).as("%s に @Transactional があること", method.getName()).isNotNull();
      assertThat(tx.isolation())
          .as("%s が 1 つの断面を要求すること", method.getName())
          .isEqualTo(Isolation.REPEATABLE_READ);
    }
  }

  @Test
  void listWorkQueueBuildsTheCursorFromTheOrderingQueryNotTheBody() {
    // 2 本の問い合わせは READ COMMITTED では別の断面を見る。間に他の操作者が境界の行の鍵を書き換えると、
    // 本体の側から組んだ続きは新しい値の後ろから始まり、間に挟まる受注を丸ごと飛ばす（人数 1 の行が
    // 100 に直されれば 1〜100 が続きに現れない）。並びを決めた側の鍵だけがその穴を塞ぐ
    List<OrderView> rewritten = List.of(queueView("o1", 100), queueView("o2", 200));
    when(orderSearchQuery.findRows(
            any(OrderQueryCriteria.class), nullable(PageCursor.class), anyInt()))
        .thenReturn(List.of(new OrderedRow("o1", 1), new OrderedRow("o2", 2)));
    when(orderRepository.findViewsByIds(List.of("o1", "o2"))).thenReturn(rewritten);
    stubRowResponses();

    CursorPage<OrderWorkQueueResponse> page = service.listWorkQueue(queueCriteria(), null, 1);

    assertThat(PageCursor.decode(page.nextCursor())).isEqualTo(new PageCursor("1", "o1"));
  }

  @Test
  void listWorkQueueFailsLoudWhenTheBodyReadDropsARow() {
    // 黙って取り落とすと「上限より 1 件多く取れたか」で判る続きの有無がその 1 件ぶん狂い、
    // 続きがあるのに「もう無い」と返る。呼出側はそこで読むのをやめ、受注が画面から永久に消える
    List<OrderView> incomplete = List.of(queueView("o1", 1));
    when(orderSearchQuery.findRows(
            any(OrderQueryCriteria.class), nullable(PageCursor.class), anyInt()))
        .thenReturn(List.of(new OrderedRow("o1", 1), new OrderedRow("o2", 2)));
    when(orderRepository.findViewsByIds(List.of("o1", "o2"))).thenReturn(incomplete);

    assertThatThrownBy(() -> service.listWorkQueue(queueCriteria(), null, 1))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void listWorkQueueReportsNoCursorWhenNothingFollows() {
    List<OrderView> views = List.of(queueView("o1", 1));
    when(orderSearchQuery.findRows(
            any(OrderQueryCriteria.class), nullable(PageCursor.class), eq(3)))
        .thenReturn(List.of(new OrderedRow("o1", 1)));
    when(orderRepository.findViewsByIds(List.of("o1"))).thenReturn(views);
    stubRowResponses();

    assertThat(service.listWorkQueue(queueCriteria(), null, 2).nextCursor()).isNull();
  }

  @Test
  void listWorkQueueResumesFromTheGivenCursorInsteadOfAnOffset() {
    String cursor = new PageCursor("2", "o1").encode();
    when(orderSearchQuery.findRows(any(OrderQueryCriteria.class), any(PageCursor.class), anyInt()))
        .thenReturn(List.of());

    assertThat(service.listWorkQueue(queueCriteria(), cursor, 20).content()).isEmpty();
    // 位置を件数で指すと、手前の行が処理で消えた分だけ境界の受注を飛ばす
    verify(orderSearchQuery)
        .findRows(any(OrderQueryCriteria.class), eq(new PageCursor("2", "o1")), anyInt());
  }

  @Test
  void listWorkQueueRejectsAMalformedCursor() {
    assertThatThrownBy(() -> service.listWorkQueue(queueCriteria(), "not-a-cursor", 20))
        .isInstanceOf(ServiceException.class);
    verifyNoInteractions(orderSearchQuery);
  }

  @Test
  void listWorkQueueCapsTheRequestedSize() {
    when(orderSearchQuery.findRows(
            any(OrderQueryCriteria.class), nullable(PageCursor.class), anyInt()))
        .thenReturn(List.of());

    service.listWorkQueue(queueCriteria(), null, 10_000);

    // 1 回の応答は抑える。続きはカーソルで辿れるので、抑えても到達性は落ちない。
    verify(orderSearchQuery)
        .findRows(
            any(OrderQueryCriteria.class), nullable(PageCursor.class), eq(CursorPage.MAX_SIZE + 1));
  }

  @Test
  void listArchiveCarriesTheTotalCountForThePager() {
    OrderQueryCriteria criteria =
        new OrderQueryCriteria(
            Set.of(OrderStatus.COMPLETED), null, null, OrderSortKey.BUSINESS_DATE, true);
    Pageable pageable = PageRequest.of(0, 20);
    List<OrderView> views = List.of(queueView("o1", 1));
    when(orderSearchQuery.findIds(criteria, pageable)).thenReturn(List.of("o1"));
    when(orderRepository.findViewsByIds(List.of("o1"))).thenReturn(views);
    stubRowResponses();
    when(orderSearchQuery.count(criteria)).thenReturn(137L);

    Page<OrderArchiveResponse> page = service.listArchive(criteria, pageable);

    assertThat(page.getContent()).hasSize(1);
    // 総件数が無いとページャは最終ページを出せない（作業キューと違い位置をページ番号で指すため）
    assertThat(page.getTotalElements()).isEqualTo(137L);
  }

  /** 確定・謝絶の判定に使う「現在の営業日」。申請の希望日はこの日を基準に失効を導出する。 */
  private static final LocalDate CURRENT_BUSINESS_DATE = LocalDate.of(2026, 8, 10);

  /** 確定・謝絶の対象となる未処理の予約申請（希望日は当日 = 失効していない）。 */
  private static OrderApplication.OrderApplicationBuilder pendingApplication() {
    return OrderApplication.builder()
        .status(OrderApplicationStatus.PENDING)
        .businessDate(CURRENT_BUSINESS_DATE)
        .requesterMemberCode("123456789012");
  }

  private static OrderApplicationConfirmationRequest confirmation() {
    OrderApplicationConfirmationRequest request = new OrderApplicationConfirmationRequest();
    request.setBusinessDate(CURRENT_BUSINESS_DATE);
    request.setPax(2);
    return request;
  }

  private static OrderApplicationDeclineRequest declineWith(String reason) {
    OrderApplicationDeclineRequest request = new OrderApplicationDeclineRequest();
    request.setReason(reason);
    return request;
  }

  private void stubBusinessDate() {
    when(businessDateService.currentBusinessDate()).thenReturn(CURRENT_BUSINESS_DATE);
  }

  /** 生成される受注の保存を成功させ、Snowflake 採番の代わりに固定 id を与える。 */
  private void stubOrderCreation() {
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            i -> {
              Order order = i.getArgument(0);
              order.setId("order-1");
              return order;
            });
    when(orderApplicationRepository.save(any(OrderApplication.class)))
        .thenAnswer(i -> i.getArgument(0));
    when(orderRepository.findViewById("order-1")).thenReturn(Optional.of(mock(OrderView.class)));
    lenient()
        .when(orderMapper.toResponse(any(OrderView.class)))
        .thenReturn(OrderResponse.builder().id("order-1").build());
  }

  private void stubConfirmActor() {
    PlatformUser actor = authorizedReceptionist();
    actor.setId(7L);
    when(platformUserRepository.findByEmail("staff@kizuna.test")).thenReturn(Optional.of(actor));
  }

  @Test
  void confirmCreatesAConfirmedOrderAndWritesItBackToTheApplication() {
    OrderApplication application =
        pendingApplication()
            .requesterMemberId(100L)
            .requesterDeclaredName("名乗り太郎")
            .castId("cast-希望")
            .remarks("窓際の席を希望")
            .build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    when(customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            STORE_ID, 100L, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    stubConfirmActor();
    Customer provisioned = Customer.builder().name("名乗り太郎").build();
    provisioned.setId("cust-new");
    when(customerRepository.save(any(Customer.class))).thenReturn(provisioned);
    stubOrderCreation();

    service.confirmApplication("a1", confirmation(), "staff@kizuna.test");

    verify(orderRepository).save(orderCaptor.capture());
    Order created = orderCaptor.getValue();
    assertThat(created.getStatus()).as("受注は出生即 CONFIRMED であること").isEqualTo(OrderStatus.CONFIRMED);
    assertThat(created.getReceptionRoute())
        .as("会員ポータル由来の申請の確定は MEMBER_WEB を名乗ること")
        .isEqualTo(ReceptionRoute.MEMBER_WEB);
    assertThat(created.getRequesterMemberId()).isEqualTo(100L);
    assertThat(created.getRequesterMemberCode()).isEqualTo("123456789012");
    assertThat(created.getRequesterDeclaredName()).isEqualTo("名乗り太郎");
    assertThat(created.getBusinessDate()).as("受注の内容は確定内容から取ること").isEqualTo(CURRENT_BUSINESS_DATE);
    assertThat(created.getPax()).isEqualTo(2);
    assertThat(created.getCastId()).as("確定内容が指名を持たなければ受注も指名なしであること").isNull();

    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.CONFIRMED);
    assertThat(application.getOrderId()).as("申請行へ生成した受注の id が回写されること").isEqualTo("order-1");
    assertThat(application.getProcessedBy()).isEqualTo(7L);
    assertThat(application.getCastId()).as("申請原文は確定で書き換わらないこと").isEqualTo("cast-希望");
    assertThat(application.getRemarks()).isEqualTo("窓際の席を希望");
  }

  @Test
  void confirmRecordsGuestWebForAnApplicationWithoutAMemberCode() {
    // 受付経路は入口を写す。会員コードのスナップショットを持たない申請が公開店面のゲスト申請である
    OrderApplication application =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(CURRENT_BUSINESS_DATE)
            .contactName("ゲスト花子")
            .contactPhoneNumber("09000000000")
            .build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    stubConfirmActor();
    stubOrderCreation();

    service.confirmApplication("a1", confirmation(), "staff@kizuna.test");

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getReceptionRoute()).isEqualTo(ReceptionRoute.GUEST_WEB);
  }

  @Test
  void confirmRecordsMemberWebEvenAfterTheMemberRowWasDeleted() {
    // 会員行の削除で requesterMemberId は欠落するが、会員コードは残る。入口の記録がゲストへ倒れてはならない
    OrderApplication application = pendingApplication().build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    stubConfirmActor();
    stubOrderCreation();

    service.confirmApplication("a1", confirmation(), "staff@kizuna.test");

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getReceptionRoute()).isEqualTo(ReceptionRoute.MEMBER_WEB);
  }

  // ==================== ゲスト申請の確定時の顧客化（人工判断） ====================

  private static OrderApplication.OrderApplicationBuilder pendingGuestApplication() {
    return OrderApplication.builder()
        .status(OrderApplicationStatus.PENDING)
        .businessDate(CURRENT_BUSINESS_DATE)
        .contactName("ゲスト花子")
        .contactPhoneNumber("09000000000");
  }

  @Test
  void confirmLinksTheExistingCustomerNamedByTheStaff() {
    OrderApplication application = pendingGuestApplication().build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    when(customerReferenceResolver.resolveForWrite("cust-既存")).thenReturn("cust-既存");
    stubConfirmActor();
    stubOrderCreation();

    OrderApplicationConfirmationRequest request = confirmation();
    request.setCustomerId("cust-既存");
    service.confirmApplication("a1", request, "staff@kizuna.test");

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isEqualTo("cust-既存");
    // 電話番号での自動照合は行わない（機械が 1 行を選ぶことが誤帰属の入口になる）
    verify(customerRepository, never()).findAliveIdsByPhoneNumberAndStoreId(anyString(), anyLong());
  }

  @Test
  void confirmCreatesTheLedgerRowTheStaffFilledIn() {
    OrderApplication application = pendingGuestApplication().build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    Customer created = Customer.builder().name("ゲスト花子").build();
    created.setId("cust-new");
    when(customerRepository.save(any(Customer.class))).thenReturn(created);
    stubConfirmActor();
    stubOrderCreation();

    OrderApplicationConfirmationRequest request = confirmation();
    OrderApplicationConfirmationRequest.NewCustomer newCustomer =
        new OrderApplicationConfirmationRequest.NewCustomer();
    newCustomer.setName("ゲスト花子");
    newCustomer.setPhoneNumber("09000000000");
    request.setNewCustomer(newCustomer);

    service.confirmApplication("a1", request, "staff@kizuna.test");

    verify(customerRepository).save(customerCaptor.capture());
    assertThat(customerCaptor.getValue().getName()).isEqualTo("ゲスト花子");
    assertThat(customerCaptor.getValue().getPhoneNumber()).isEqualTo("09000000000");
    assertThat(customerCaptor.getValue().getRank()).as("台帳行のランクは既定を明示すること").isEqualTo("SILVER");
    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isEqualTo("cust-new");
  }

  @Test
  void confirmKeepsTheGuestContactOnTheOrderWhenNoCustomerIsChosen() {
    // 顧客を選ばない確定も正規（無帰属受注）。写さないと折返し先がどこにも残らない
    OrderApplication application = pendingGuestApplication().build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    stubConfirmActor();
    stubOrderCreation();

    service.confirmApplication("a1", confirmation(), "staff@kizuna.test");

    verify(orderRepository).save(orderCaptor.capture());
    Order created = orderCaptor.getValue();
    assertThat(created.getCustomerId()).isNull();
    assertThat(created.getContactName()).isEqualTo("ゲスト花子");
    assertThat(created.getContactPhoneNumber()).isEqualTo("09000000000");
    verify(customerRepository, never()).save(any(Customer.class));
  }

  @Test
  void confirmRejectsChoosingBothAnExistingCustomerAndANewOne() {
    OrderApplication application = pendingGuestApplication().build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));

    OrderApplicationConfirmationRequest request = confirmation();
    request.setCustomerId("cust-既存");
    OrderApplicationConfirmationRequest.NewCustomer newCustomer =
        new OrderApplicationConfirmationRequest.NewCustomer();
    newCustomer.setName("ゲスト花子");
    request.setNewCustomer(newCustomer);

    assertThatThrownBy(() -> service.confirmApplication("a1", request, "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("どちらか一方");
    // 検証は書き換えより先に済ませる（拒否の健全さを巻き戻しに委ねない）
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void confirmRejectsAStaffChosenCustomerOnAMemberApplication() {
    // 会員の顧客は「今の関連」だけが決める一本道。店員が別の行を選べると完了時のポイントが別会員へ積まれる
    OrderApplication application = pendingApplication().requesterMemberId(100L).build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));

    OrderApplicationConfirmationRequest request = confirmation();
    request.setCustomerId("cust-別人");

    assertThatThrownBy(() -> service.confirmApplication("a1", request, "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("会員の申請では顧客を選べません");
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void confirmAssignsActorAsReceptionistWhenSlotIsEmpty() {
    OrderApplication application = pendingApplication().build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    stubConfirmActor();
    stubOrderCreation();

    service.confirmApplication("a1", confirmation(), "staff@kizuna.test");

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getReceptionistId()).isEqualTo(7L);
  }

  @Test
  void confirmLeavesReceptionistEmptyWhenActorIsNotEligible() {
    OrderApplication application = pendingApplication().build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    PlatformUser otherStoreActor =
        receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L));
    otherStoreActor.setId(7L);
    when(platformUserRepository.findByEmail("staff@kizuna.test"))
        .thenReturn(Optional.of(otherStoreActor));
    stubOrderCreation();

    service.confirmApplication("a1", confirmation(), "staff@kizuna.test");

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getReceptionistId()).as("適格でない実行者は受付担当に据えないこと").isNull();
    assertThat(application.getProcessedBy()).as("受付担当に据えなくても実行者としては残ること").isEqualTo(7L);
  }

  @Test
  void confirmRejectsAnExplicitReceptionistAuthorizedForADifferentStore() {
    // 確定内容の受付担当も作成・更新と同一の適格条件で検証する。素通しすると確定操作が
    // 受付担当の適格条件を迂回する入口になる
    OrderApplication application = pendingApplication().build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    PlatformUser otherStoreReceptionist =
        receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L));
    when(platformUserRepository.findById(9L)).thenReturn(Optional.of(otherStoreReceptionist));

    OrderApplicationConfirmationRequest request = confirmation();
    request.setReceptionistId(9L);

    assertThatThrownBy(() -> service.confirmApplication("a1", request, "staff@kizuna.test"))
        .isInstanceOf(NotFoundException.class);
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void confirmLinksCustomerThroughTheCurrentLink() {
    // 会員の顧客参照は確定の時点の「今の関連」で決める。申請は顧客を持たないので、ここが唯一の着地点
    OrderApplication application = pendingApplication().requesterMemberId(100L).build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
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
    // 関連の照会は行を押さえないため、着ける前に共有の解決口を通る
    when(customerReferenceResolver.resolveForWrite("cust-1")).thenReturn("cust-1");
    stubConfirmActor();
    stubOrderCreation();

    service.confirmApplication("a1", confirmation(), "staff@kizuna.test");

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isEqualTo("cust-1");
    verify(customerRepository, never()).save(any(Customer.class));
    verify(customerMemberLinkRepository, never()).saveAndFlush(any(CustomerMemberLink.class));
  }

  @Test
  void confirmProvisionsALedgerRowAndLinkWhenTheStoreHasNone() {
    // 会員コードを読ませずに申請だけで来店する経路。ここで整備しないと、完了時の会員解決
    // （顧客 → 有効な関連 → 会員）が空振りしてポイントが記帳されない。
    OrderApplication application =
        pendingApplication().requesterMemberId(100L).requesterDeclaredName("名乗り太郎").build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    when(customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            STORE_ID, 100L, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    stubConfirmActor();
    Customer provisioned = Customer.builder().name("名乗り太郎").build();
    provisioned.setId("cust-new");
    when(customerRepository.save(any(Customer.class))).thenReturn(provisioned);
    stubOrderCreation();

    service.confirmApplication("a1", confirmation(), "staff@kizuna.test");

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

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).as("受注が整備された顧客に着くこと").isEqualTo("cust-new");
    // 起こしたばかりの行は他の経路の書き換えに晒されていないので、解決を経ずに着ける
    verifyNoInteractions(customerReferenceResolver);
  }

  @Test
  void confirmStillProvisionsWhenTheApplicationCarriesNoDeclaredName() {
    // 名乗る名前を持たない申請でも整備は止めない。氏名の空欄は店舗が台帳で直せるが、
    // 整備を諦めた受注は完了してもポイントが記帳されず、会員に取り戻す経路が無い。
    OrderApplication application = pendingApplication().requesterMemberId(100L).build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    when(customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            STORE_ID, 100L, LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    stubConfirmActor();
    Customer provisioned = Customer.builder().build();
    provisioned.setId("cust-new");
    when(customerRepository.save(any(Customer.class))).thenReturn(provisioned);
    stubOrderCreation();

    service.confirmApplication("a1", confirmation(), "staff@kizuna.test");

    verify(customerRepository).save(customerCaptor.capture());
    assertThat(customerCaptor.getValue().getName()).as("名乗りが無ければ氏名は空のままであること").isNull();
    verify(customerMemberLinkRepository).saveAndFlush(linkCaptor.capture());
    assertThat(linkCaptor.getValue().getReason()).isEqualTo(LinkReason.MEMBER_REQUEST);
    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId())
        .as("記帳先が決まるよう受注は顧客に着くこと")
        .isEqualTo("cust-new");
  }

  @Test
  void confirmProvisionsNothingForAnApplicationWithoutARequester() {
    // 会員行が消えて申請者の会員 ID が欠落した申請は、関連の会員参照を作れない。整備を諦めて
    // 顧客未設定のまま確定させる（無帰属受注は正規の状態）。
    OrderApplication application = pendingApplication().build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    stubConfirmActor();
    stubOrderCreation();

    service.confirmApplication("a1", confirmation(), "staff@kizuna.test");

    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.CONFIRMED);
    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCustomerId()).isNull();
    verify(customerRepository, never()).save(any(Customer.class));
    verify(customerMemberLinkRepository, never()).saveAndFlush(any(CustomerMemberLink.class));
  }

  @Test
  void confirmRejectsWhenNominatedCastIsNoLongerActive() {
    // 申請から確定までの間に在籍停止になった指名は、そのまま確定させない
    OrderApplication application = pendingApplication().castId("cast-1").build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(nominatableCast.find(STORE_ID, "cast-1")).thenReturn(Optional.empty());

    OrderApplicationConfirmationRequest request = confirmation();
    request.setCastId("cast-1");

    assertThatThrownBy(() -> service.confirmApplication("a1", request, "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("在籍中でない");
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void confirmRejectsWhenNominatedCastLostTheConfirmedShift() {
    // 確定シフトが取り消し・未確定化された指名も、そのまま確定させない
    OrderApplication application = pendingApplication().castId("cast-1").build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(nominatableCast.find(STORE_ID, "cast-1")).thenReturn(Optional.of(nominatable("cast-1")));
    when(confirmedShiftLookupService.hasConfirmedShift(STORE_ID, "cast-1", CURRENT_BUSINESS_DATE))
        .thenReturn(false);

    OrderApplicationConfirmationRequest request = confirmation();
    request.setCastId("cast-1");

    assertThatThrownBy(() -> service.confirmApplication("a1", request, "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("確定シフトが無い");
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void confirmAssignsTheCastWhenNominationStillHolds() {
    OrderApplication application = pendingApplication().castId("cast-1").build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
    when(nominatableCast.find(STORE_ID, "cast-1")).thenReturn(Optional.of(nominatable("cast-1")));
    when(confirmedShiftLookupService.hasConfirmedShift(STORE_ID, "cast-1", CURRENT_BUSINESS_DATE))
        .thenReturn(true);
    stubConfirmActor();
    stubOrderCreation();

    OrderApplicationConfirmationRequest request = confirmation();
    request.setCastId("cast-1");

    service.confirmApplication("a1", request, "staff@kizuna.test");

    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.CONFIRMED);
    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getCastId()).isEqualTo("cast-1");
  }

  @Test
  void confirmRejectsAnExpiredApplication() {
    // 希望日を過ぎた申請は失効。バッチで終端へ送らない代わりに、操作の入口で撥ねる
    OrderApplication application =
        pendingApplication().businessDate(CURRENT_BUSINESS_DATE.minusDays(1)).build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.confirmApplication("a1", confirmation(), "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("失効");
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void confirmRejectsAnAlreadyProcessedApplication() {
    OrderApplication application =
        pendingApplication().status(OrderApplicationStatus.WITHDRAWN).build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.confirmApplication("a1", confirmation(), "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void confirmThrowsWhenApplicationMissing() {
    when(orderApplicationRepository.findById("nope")).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> service.confirmApplication("nope", confirmation(), "staff@kizuna.test"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void declineRecordsTheReasonActorAndTime() {
    OrderApplication application = pendingApplication().build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    stubConfirmActor();
    when(orderApplicationRepository.save(any(OrderApplication.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.declineApplication("a1", declineWith("満席のためお受けできません"), "staff@kizuna.test");

    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.DECLINED);
    assertThat(application.getDeclinedReason()).isEqualTo("満席のためお受けできません");
    assertThat(application.getProcessedBy()).isEqualTo(7L);
    assertThat(application.getProcessedAt()).isNotNull();
  }

  @Test
  void declineProvisionsNothing() {
    // 整備は確定の効果であって申請の効果ではない。謝絶で台帳に行が生えると、来なかった客が
    // 店舗の台帳に会員として積み上がる。
    OrderApplication application =
        pendingApplication().requesterMemberId(100L).requesterDeclaredName("名乗り太郎").build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    stubConfirmActor();
    when(orderApplicationRepository.save(any(OrderApplication.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.declineApplication("a1", declineWith("満席"), "staff@kizuna.test");

    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.DECLINED);
    verify(customerRepository, never()).save(any(Customer.class));
    verify(customerMemberLinkRepository, never()).saveAndFlush(any(CustomerMemberLink.class));
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void declineRejectsAnAlreadyProcessedApplication() {
    OrderApplication application =
        pendingApplication().status(OrderApplicationStatus.CONFIRMED).build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    stubConfirmActor();

    assertThatThrownBy(
            () -> service.declineApplication("a1", declineWith("理由"), "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class);
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.CONFIRMED);
    verify(orderApplicationRepository, never()).save(any(OrderApplication.class));
  }

  @Test
  void declineRejectsAnExpiredApplication() {
    OrderApplication application =
        pendingApplication().businessDate(CURRENT_BUSINESS_DATE.minusDays(1)).build();
    application.setStoreId(STORE_ID);
    stubBusinessDate();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(application));
    stubConfirmActor();

    assertThatThrownBy(
            () -> service.declineApplication("a1", declineWith("理由"), "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("失効");
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    verify(orderApplicationRepository, never()).save(any(OrderApplication.class));
  }

  @Test
  void listApplicationsDelegatesTheStatusFilterToTheQuery() {
    stubBusinessDate();
    when(orderApplicationRepository.findViews(
            eq(Set.of(OrderApplicationStatus.PENDING)), any(Limit.class)))
        .thenReturn(List.of());

    CursorPage<OrderApplicationResponse> page =
        service.listApplications(Set.of(OrderApplicationStatus.PENDING), null, 20);

    assertThat(page.content()).isEmpty();
    verify(orderApplicationRepository)
        .findViews(eq(Set.of(OrderApplicationStatus.PENDING)), any(Limit.class));
  }

  @Test
  void listApplicationsResumesFromTheGivenCursor() {
    stubBusinessDate();
    when(orderApplicationRepository.findViewsAfter(
            eq(Set.of(OrderApplicationStatus.PENDING)),
            eq(LocalDate.parse("2026-08-04")),
            eq("a1"),
            any(Limit.class)))
        .thenReturn(List.of());

    service.listApplications(
        Set.of(OrderApplicationStatus.PENDING), new PageCursor("2026-08-04", "a1").encode(), 20);

    verify(orderApplicationRepository)
        .findViewsAfter(
            eq(Set.of(OrderApplicationStatus.PENDING)),
            eq(LocalDate.parse("2026-08-04")),
            eq("a1"),
            any(Limit.class));
  }

  @Test
  void listApplicationsDerivesExpiryForStalePendingRows() {
    stubBusinessDate();
    OrderApplicationView stale = applicationView("a1", CURRENT_BUSINESS_DATE.minusDays(1));
    OrderApplicationView fresh = applicationView("a2", CURRENT_BUSINESS_DATE);
    when(orderApplicationRepository.findViews(any(), any(Limit.class)))
        .thenReturn(List.of(stale, fresh));

    CursorPage<OrderApplicationResponse> page =
        service.listApplications(Set.of(OrderApplicationStatus.PENDING), null, 20);

    assertThat(page.content())
        .extracting(OrderApplicationResponse::isExpired)
        .containsExactly(true, false);
  }

  @Test
  void listApplicationsHandsBackTheCursorOfTheLastReturnedRow() {
    stubBusinessDate();
    List<OrderApplicationView> fetched =
        List.of(
            applicationView("a1", CURRENT_BUSINESS_DATE),
            applicationView("a2", CURRENT_BUSINESS_DATE.plusDays(1)));
    when(orderApplicationRepository.findViews(any(), eq(Limit.of(2)))).thenReturn(fetched);

    CursorPage<OrderApplicationResponse> page =
        service.listApplications(Set.of(OrderApplicationStatus.PENDING), null, 1);

    assertThat(page.content()).hasSize(1);
    assertThat(PageCursor.decode(page.nextCursor()))
        .isEqualTo(new PageCursor(CURRENT_BUSINESS_DATE.toString(), "a1"));
  }

  private static OrderApplicationView applicationView(String id, LocalDate businessDate) {
    OrderApplicationView view = mock(OrderApplicationView.class);
    lenient().when(view.getId()).thenReturn(id);
    lenient().when(view.getBusinessDate()).thenReturn(businessDate);
    lenient().when(view.getStatus()).thenReturn(OrderApplicationStatus.PENDING);
    return view;
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
    stubWriteBackResponse();

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
    stubWriteBackResponse();

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
    stubWriteBackResponse();

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
    stubReceiptTokenIssuance();
    stubWriteBackResponse();

    service.complete("o1", completion(12000, null), "staff@kizuna.test");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.getAutoGrantPoints()).isZero();
    // 台帳へは何も積まない（付与予定額の算定で規則は読むが、書き込みは起きない）
    verify(pointLedgerService, never()).grantForOrder(anyLong(), any(), any(), anyInt(), any());
    verify(pointLedgerService, never()).useForOrder(anyLong(), any(), any(), anyInt(), any());
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
    stubWriteBackResponse();

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
    stubWriteBackResponse();

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
    stubReceiptTokenIssuance();
    stubWriteBackResponse();

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
    stubReceiptTokenIssuance();
    stubWriteBackResponse();

    service.complete("o1", completion(12000, null), "staff@kizuna.test");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    verifyNoInteractions(orderAttributionRepository);
    verify(pointLedgerService, never()).grantForOrder(anyLong(), any(), any(), anyInt(), any());
  }

  private OrderAttribution savedAttribution() {
    ArgumentCaptor<OrderAttribution> captor = ArgumentCaptor.forClass(OrderAttribution.class);
    verify(orderAttributionRepository).save(captor.capture());
    return captor.getValue();
  }

  // ==================== 伝票トークンの発行 ====================

  private static final String RAW_TOKEN = "raw-receipt-token";
  private static final String TOKEN_DIGEST = "digest-of-the-raw-receipt-token";

  /** 発行される生値とダイジェスト。乱数と鍵派生そのものは {@code ReceiptTokenGeneratorTest} が固定する。 */
  private void stubReceiptTokenIssuance() {
    lenient()
        .when(receiptTokenGenerator.generate())
        .thenReturn(new ReceiptTokenGenerator.GeneratedToken(RAW_TOKEN, TOKEN_DIGEST));
  }

  private OrderReceiptToken savedReceiptToken() {
    ArgumentCaptor<OrderReceiptToken> captor = ArgumentCaptor.forClass(OrderReceiptToken.class);
    verify(orderReceiptTokenRepository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void completeOfAnOrderThatReachedNoMemberIssuesAReceiptToken() {
    // 事後帰属の証明は所持だけ（受注 ID は列挙できるので証明にならない）。ここで発行しないと、
    // 会員と分からないまま完了した来店を本人が取り戻す経路が無い
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    when(customerMemberLinkRepository.findByCustomerIdAndStatus("cust-1", LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(pointLedgerService.previewGrant(12000)).thenReturn(120);
    stubReceiptTokenIssuance();
    stubWriteBackResponse();

    OrderCompletionResponse response =
        service.complete("o1", completion(12000, null), "staff@kizuna.test");

    OrderReceiptToken token = savedReceiptToken();
    assertThat(token.getOrderId()).isEqualTo("o1");
    // 保存するのはダイジェストだけ。生値はこの応答にしか現れない
    assertThat(token.getTokenDigest()).isEqualTo(TOKEN_DIGEST);
    assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.ISSUED);
    assertThat(token.getExpiresAt()).isEqualTo(token.getIssuedAt().plusDays(90));
    assertThat(response.receiptToken()).isEqualTo(RAW_TOKEN);
  }

  @Test
  void completeFreezesThePlannedPointsAtTheCompletionTimeRule() {
    // 申領時点の設定を読むと、同じ会計が申領の早い遅いで別のポイントになる
    Order order = Order.builder().status(OrderStatus.CONFIRMED).castId("cast-1").build();
    order.setStoreId(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    when(pointLedgerService.previewGrant(12000)).thenReturn(120);
    stubReceiptTokenIssuance();
    stubWriteBackResponse();

    service.complete("o1", completion(12000, null), "staff@kizuna.test");

    assertThat(savedReceiptToken().getPlannedPoints()).isEqualTo(120);
  }

  @Test
  void completeOfAZeroFeeOrderStillIssuesAReceiptToken() {
    // 付与が 0 でも来店の事実は取り戻せなければならない（申領の効果は来店の可視化に閉じる）
    Order order = Order.builder().status(OrderStatus.CONFIRMED).castId("cast-1").build();
    order.setStoreId(STORE_ID);
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    when(pointLedgerService.previewGrant(0)).thenReturn(0);
    stubReceiptTokenIssuance();
    stubWriteBackResponse();

    OrderCompletionResponse response =
        service.complete("o1", completion(0, null), "staff@kizuna.test");

    assertThat(savedReceiptToken().getPlannedPoints()).isZero();
    assertThat(response.receiptToken()).isEqualTo(RAW_TOKEN);
  }

  @Test
  void completeOfAMemberOrderIssuesNoReceiptToken() {
    // 会員へ帰属した完了に事後帰属の余地は無い。発行すると、その受注を別の会員が申領しに来る口を開ける
    Order order = confirmedOrderWithCustomer();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
    stubActiveLink(MEMBER_ID);
    stubActor();
    stubWriteBackResponse();

    OrderCompletionResponse response =
        service.complete("o1", completion(12000, null), "staff@kizuna.test");

    verifyNoInteractions(orderReceiptTokenRepository, receiptTokenGenerator);
    assertThat(response.receiptToken()).isNull();
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
    Order cancelled = Order.builder().status(OrderStatus.CANCELLED).customerId("cust-1").build();
    when(orderRepository.findById("o1")).thenReturn(Optional.of(cancelled));

    assertThatThrownBy(() -> service.complete("o1", completion(12000, 300), "staff@kizuna.test"))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
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
    stubWriteBackResponse();

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
  private void stubWriteBackResponse() {
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    lenient()
        .when(orderRepository.findViewById(nullable(String.class)))
        .thenReturn(Optional.of(mock(OrderView.class)));
    stubRowResponses();
  }

  /**
   * 応答の組み立て（読み口 → DTO）だけを満たす stub。どの型で返るかは操作ごとに違い、結果を読まない操作（取消・謝絶）は 写像自体を呼ばないため lenient
   * で置く。操作そのものの検証は集約の状態で行う。
   */
  private void stubRowResponses() {
    lenient()
        .when(orderMapper.toResponse(any(OrderView.class)))
        .thenReturn(OrderResponse.builder().build());
    lenient()
        .when(orderMapper.toWorkQueueResponse(any(OrderView.class)))
        .thenReturn(OrderWorkQueueResponse.builder().build());
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
