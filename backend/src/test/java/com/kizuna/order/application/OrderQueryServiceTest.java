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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.CastProfile;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.domain.OrderApplicationView;
import com.kizuna.order.domain.OrderCourses;
import com.kizuna.order.domain.OrderQueryCriteria;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderSortKey;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.OrderView;
import com.kizuna.order.infrastructure.OrderSearchQuery;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.application.ReceptionistEligibilityService;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock OrderApplicationRepository orderApplicationRepository;
  @Mock OrderSearchQuery orderSearchQuery;
  @Mock CustomerRepository customerRepository;
  @Mock CustomerContactRepository customerContactRepository;
  @Mock NominatableCastLookup nominatableCast;
  @Mock GuestApplicationConsent guestConsent;
  @Mock BusinessContactPermissions businessContactPermissions;
  @Mock BusinessDateService businessDateService;
  @Mock private OrderSpecialServices specialServices;
  private final UserRepository userRepository = Mockito.mock(UserRepository.class);
  private final RoleRepository roleRepository = Mockito.mock(RoleRepository.class);

  @Spy
  private ReceptionistEligibilityService receptionistEligibilityService =
      new ReceptionistEligibilityService(userRepository, roleRepository);

  @InjectMocks OrderQueryService queryService;

  private static final long STORE_ID = 7L;

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

  /** 確定・謝絶の判定に使う「現在の営業日」。申請の希望日はこの日を基準に失効を導出する。 */
  private static final LocalDate CURRENT_BUSINESS_DATE = LocalDate.of(2026, 8, 10);

  private void stubBusinessDate() {
    when(businessDateService.currentBusinessDate()).thenReturn(CURRENT_BUSINESS_DATE);
  }

  private User receptionist(UserType userType, StoreScopeType scopeType, Set<Long> storeIds) {
    return User.builder()
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
  private User authorizedReceptionist() {
    return receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(STORE_ID));
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

  private static OrderApplicationView applicationView(String id, LocalDate businessDate) {
    OrderApplicationView view = mock(OrderApplicationView.class);
    lenient().when(view.getId()).thenReturn(id);
    lenient().when(view.getBusinessDate()).thenReturn(businessDate);
    lenient().when(view.getStatus()).thenReturn(OrderApplicationStatus.PENDING);
    return view;
  }

  /** {@link #authorizedReceptionist()} を id・表示名だけ差し替えて複製する（一覧テストで複数件を区別するため）。 */
  private User staffAuthorizedForCurrentStore(long id, String displayName) {
    User user = authorizedReceptionist();
    user.setId(id);
    user.updateDisplayName(displayName);
    return user;
  }

  @Test
  void listReturnsPageOfOrderResponses() {
    OrderView view = mock(OrderView.class);
    Page<OrderView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 10), 1);

    when(orderRepository.findAllViews(eq(STORE_ID), eq(null), any(Pageable.class)))
        .thenReturn(page);

    Page<OrderView> result = queryService.list(STORE_ID, null, PageRequest.of(0, 10));
    assertThat(result.getContent()).containsExactly(view);
  }

  @Test
  void listFiltersByCustomerId() {
    OrderView view = mock(OrderView.class);
    Page<OrderView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 10), 1);

    when(orderRepository.findAllViews(eq(STORE_ID), eq("c1"), any(Pageable.class)))
        .thenReturn(page);

    Page<OrderView> result = queryService.list(STORE_ID, "c1", PageRequest.of(0, 10));
    assertThat(result.getContent()).containsExactly(view);
  }

  @Test
  void getReturnsOrderResponseOrThrows() {
    OrderView view = mock(OrderView.class);

    // 詳細は読み口（表示名の join）と集約（明細の行）の 2 本から組む
    when(orderRepository.findByIdAndStoreId("o1", STORE_ID))
        .thenReturn(
            Optional.of(
                Order.builder()
                    .status(OrderStatus.CONFIRMED)
                    .course(OrderCourses.course("基本", 60, 0))
                    .build()));
    when(orderRepository.findViewById(eq(STORE_ID), nullable(String.class)))
        .thenReturn(Optional.of(view));
    when(orderRepository.findByIdAndStoreId("o2", STORE_ID)).thenReturn(Optional.empty());

    assertThat(queryService.get(STORE_ID, "o1").view()).isSameAs(view);
    assertThatThrownBy(() -> queryService.get(STORE_ID, "o2"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void customerCandidatesDoNotReadContactsWithoutTheCustomerManagePermission() {
    Customer customer = Customer.builder().name("山田").build();
    customer.setId("c1");
    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(customer)));

    var page = queryService.customerCandidates(STORE_ID, "山田", false, PageRequest.of(0, 20));

    assertThat(page.getContent()).extracting(OrderCustomerCandidate::phoneNumber).containsNull();
    verifyNoInteractions(customerContactRepository);
  }

  @Test
  void customerCandidatesCarryThePreferredPhoneWithTheCustomerManagePermission() {
    Customer customer = Customer.builder().name("山田").build();
    customer.setId("c1");
    Pageable pageable = PageRequest.of(1, 20, Sort.by("id"));
    when(customerRepository.findAll(ArgumentMatchers.<Specification<Customer>>any(), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(customer), pageable, 21));
    when(customerContactRepository
            .findByStoreIdAndCustomerIdInAndPreferredTrueAndDeletedFalseOrderByIdAsc(
                STORE_ID, List.of("c1")))
        .thenReturn(List.of());

    var page = queryService.customerCandidates(STORE_ID, "山田", true, pageable);

    assertThat(page.getContent()).extracting(OrderCustomerCandidate::id).containsExactly("c1");
    assertThat(page.getTotalElements()).isEqualTo(21);
    verify(customerContactRepository)
        .findByStoreIdAndCustomerIdInAndPreferredTrueAndDeletedFalseOrderByIdAsc(
            STORE_ID, List.of("c1"));
  }

  @Test
  void listWorkQueueDelegatesFilteringToTheQuery() {
    OrderView view = queueView("o1", 2);
    Pageable pageable = PageRequest.of(0, 20);
    when(orderSearchQuery.findIds(eq(STORE_ID), any(OrderQueryCriteria.class), eq(pageable)))
        .thenReturn(List.of("o1"));
    when(orderSearchQuery.count(eq(STORE_ID), any(OrderQueryCriteria.class))).thenReturn(1L);
    when(orderRepository.findViewsByIds(STORE_ID, List.of("o1"))).thenReturn(List.of(view));

    assertThat(queryService.listWorkQueue(STORE_ID, queueCriteria(), pageable).getContent())
        .containsExactly(view);
    // 受注一覧の先頭ページを取って手元で選り分ける実装だと、完了が積み上がった店舗で未対応が窓から落ちる
    verify(orderRepository, never()).findAllViews(eq(STORE_ID), any(), any(Pageable.class));
  }

  @Test
  void listWorkQueueKeepsTheOrderTheQueryDecided() {
    // 行の中身は id の集合で引き直すため、返る順は問い合わせの並びと一致しない。並べ直さないと
    // 画面の並び替えが効かないまま「並び替えたつもり」になる
    List<OrderView> views = List.of(queueView("o1", 1), queueView("o2", 2));
    when(orderSearchQuery.findIds(eq(STORE_ID), any(OrderQueryCriteria.class), any()))
        .thenReturn(List.of("o2", "o1"));
    when(orderRepository.findViewsByIds(STORE_ID, List.of("o2", "o1"))).thenReturn(views);

    assertThat(
            queryService
                .listWorkQueue(STORE_ID, queueCriteria(), PageRequest.of(0, 20))
                .getContent())
        .extracting(OrderView::getId)
        .containsExactly("o2", "o1");
  }

  @Test
  void listWorkQueueFailsLoudWhenTheBodyReadDropsARow() {
    // 黙って取り落とすと件数の減った 1 ページとして正常に見え、対応が要る受注が画面から消える
    List<OrderView> incomplete = List.of(queueView("o1", 1));
    when(orderSearchQuery.findIds(eq(STORE_ID), any(OrderQueryCriteria.class), any()))
        .thenReturn(List.of("o1", "o2"));
    when(orderRepository.findViewsByIds(STORE_ID, List.of("o1", "o2"))).thenReturn(incomplete);

    assertThatThrownBy(
            () -> queryService.listWorkQueue(STORE_ID, queueCriteria(), PageRequest.of(0, 20)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void listArchiveCarriesTheTotalCountForThePager() {
    OrderQueryCriteria criteria =
        new OrderQueryCriteria(
            Set.of(OrderStatus.COMPLETED), null, null, OrderSortKey.BUSINESS_DATE, true);
    Pageable pageable = PageRequest.of(0, 20);
    List<OrderView> views = List.of(queueView("o1", 1));
    when(orderSearchQuery.findIds(STORE_ID, criteria, pageable)).thenReturn(List.of("o1"));
    when(orderRepository.findViewsByIds(STORE_ID, List.of("o1"))).thenReturn(views);
    when(orderSearchQuery.count(STORE_ID, criteria)).thenReturn(137L);

    Page<OrderView> page = queryService.listArchive(STORE_ID, criteria, pageable);

    assertThat(page.getContent()).hasSize(1);
    // 総件数が無いとページャは最終ページを出せない
    assertThat(page.getTotalElements()).isEqualTo(137L);
  }

  @Test
  void listApplicationsDelegatesTheStatusFilterToTheQuery() {
    stubBusinessDate();
    Pageable pageable = PageRequest.of(0, 20, Sort.by("businessDate", "id"));
    when(orderApplicationRepository.findViews(
            STORE_ID, Set.of(OrderApplicationStatus.PENDING), pageable))
        .thenReturn(Page.empty(pageable));

    Page<OrderApplicationItem> page =
        queryService.listApplications(STORE_ID, Set.of(OrderApplicationStatus.PENDING), pageable);

    assertThat(page.getContent()).isEmpty();
    verify(orderApplicationRepository)
        .findViews(STORE_ID, Set.of(OrderApplicationStatus.PENDING), pageable);
  }

  @Test
  void listApplicationsDerivesExpiryForStalePendingRows() {
    stubBusinessDate();
    OrderApplicationView stale = applicationView("a1", CURRENT_BUSINESS_DATE.minusDays(1));
    OrderApplicationView fresh = applicationView("a2", CURRENT_BUSINESS_DATE);
    when(orderApplicationRepository.findViews(eq(STORE_ID), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(stale, fresh)));

    Page<OrderApplicationItem> page =
        queryService.listApplications(
            STORE_ID, Set.of(OrderApplicationStatus.PENDING), PageRequest.of(0, 20));

    assertThat(page.getContent())
        .extracting(OrderApplicationItem::expired)
        .containsExactly(true, false);
  }

  @Test
  void listReceptionistsReturnsEligibleStaffOrderedByDisplayName() {
    User first = staffAuthorizedForCurrentStore(10L, "あさひ");
    User second = staffAuthorizedForCurrentStore(11L, "ひかり");
    when(userRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(UserType.STAFF, STORE_ID))
        .thenReturn(List.of(first, second));

    List<OrderReceptionistCandidate> result = queryService.listReceptionists(STORE_ID);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo(10L);
    assertThat(result.get(0).displayName()).isEqualTo("あさひ");
    assertThat(result.get(1).id()).isEqualTo(11L);
    assertThat(result.get(1).displayName()).isEqualTo("ひかり");
  }

  @Test
  void listReceptionistsExcludesDisabledStaff() {
    User disabled = staffAuthorizedForCurrentStore(10L, "停止済み");
    disabled.stop();
    when(userRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(UserType.STAFF, STORE_ID))
        .thenReturn(List.of(disabled));

    assertThat(queryService.listReceptionists(STORE_ID)).isEmpty();
  }

  @Test
  void listReceptionistsExcludesStaffAuthorizedForDifferentStore() {
    User otherStore = receptionist(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(2L));
    otherStore.setId(10L);
    when(userRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(UserType.STAFF, STORE_ID))
        .thenReturn(List.of(otherStore));

    assertThat(queryService.listReceptionists(STORE_ID)).isEmpty();
  }

  @Test
  void listReceptionistsExcludesCastRole() {
    User cast = receptionist(UserType.CAST, StoreScopeType.ALL_STORES, Set.of());
    cast.setId(10L);
    when(userRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(UserType.STAFF, STORE_ID))
        .thenReturn(List.of(cast));

    assertThat(queryService.listReceptionists(STORE_ID)).isEmpty();
  }

  @Test
  void listCastCandidatesReturnsIdAndNameOfTheStoresNominatableCasts() {
    // 応答は下拉に要る最小限だけ。キャスト管理の応答を流用すると招待状態やカスタム項目まで付いてくる
    when(nominatableCast.searchCandidates(STORE_ID, "花"))
        .thenReturn(List.of(CastProfile.builder().enrollmentId("cast-1").name("指名キャスト").build()));

    List<OrderCastCandidate> result = queryService.listCastCandidates(STORE_ID, "花");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo("cast-1");
    assertThat(result.get(0).name()).isEqualTo("指名キャスト");
  }

  @Test
  void listCastCandidatesSharesThePredicateWithTheWriteSide() {
    // 候補一覧と書き込み時の指名検証が別の条件になると、候補に出るのに保存で撥ねられる選択が生まれる
    when(nominatableCast.searchCandidates(STORE_ID, null)).thenReturn(List.of());

    assertThat(queryService.listCastCandidates(STORE_ID, null)).isEmpty();

    verify(nominatableCast).searchCandidates(STORE_ID, null);
  }

  @Test
  void listReceptionistsExcludesStaffWithoutOrderManagePermission() {
    User noPermission =
        User.builder()
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
    when(userRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(UserType.STAFF, STORE_ID))
        .thenReturn(List.of(noPermission));

    assertThat(queryService.listReceptionists(STORE_ID)).isEmpty();
  }

  @Test
  void groupReadsRunInASingleSnapshot() throws NoSuchMethodException {
    // 群読み口は 1 回の応答を作るのに複数の文を投げる。既定の READ COMMITTED では文ごとに断面を
    // 取り直すため、文の間に他者の commit が挟まると同じ応答の中で違う世界を見る（完了済みが作業キューに
    // 混じる / 総数が中身と食い違う）。宣言が外れると黙って戻る。
    // 断面が実際に保たれることは OrderGroupReadSnapshotIT が本物の PostgreSQL で見る
    Method workQueue =
        OrderQueryService.class.getMethod(
            "listWorkQueue", Long.class, OrderQueryCriteria.class, Pageable.class);
    Method archive =
        OrderQueryService.class.getMethod(
            "listArchive", Long.class, OrderQueryCriteria.class, Pageable.class);

    for (Method method : List.of(workQueue, archive)) {
      Transactional tx = method.getAnnotation(Transactional.class);
      assertThat(tx).as("%s に @Transactional があること", method.getName()).isNotNull();
      assertThat(tx.isolation())
          .as("%s が 1 つの断面を要求すること", method.getName())
          .isEqualTo(Isolation.REPEATABLE_READ);
    }
  }
}
