package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerMapper;
import com.kizuna.customer.api.dto.CustomerMergeComparisonResponse;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerSummaryResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerCandidateRepository;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerListRepository;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerOrderCountView;
import com.kizuna.customer.domain.CustomerPatch;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.application.BusinessAudit;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  private static final Long STORE_ID = 7L;

  @Mock private CustomerContactService customerContactService;
  @Mock private CustomerContactRepository customerContactRepository;
  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerListRepository customerListRepository;
  @Mock private CustomerCandidateRepository candidateRepository;
  @Mock private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock private CustomerMergeRepository customerMergeRepository;
  @Mock private CustomerMapper customerMapper;
  @Mock private BusinessAudit audit;

  @InjectMocks private CustomerService customerService;

  @Test
  void list_returnsPage() {
    Customer c = Customer.builder().name("Test").build();
    c.setId("1");
    Page<CustomerListRepository.Row> page =
        new PageImpl<>(List.of(new CustomerListRepository.Row(c, null, null, false)));

    CustomerSummaryResponse resp = new CustomerSummaryResponse();
    resp.setName("Test");

    when(customerListRepository.findAll(
            eq(STORE_ID), ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toSummaryResponse(c)).thenReturn(resp);

    Page<CustomerSummaryResponse> result =
        customerService.list(STORE_ID, "test", "VIP", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test");
  }

  @Test
  void list_withoutFilters_returnsAll() {
    Customer c = Customer.builder().name("All").build();
    c.setId("1");
    Page<CustomerListRepository.Row> page =
        new PageImpl<>(List.of(new CustomerListRepository.Row(c, null, null, false)));

    CustomerSummaryResponse resp = new CustomerSummaryResponse();
    resp.setName("All");

    when(customerListRepository.findAll(
            eq(STORE_ID), ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toSummaryResponse(c)).thenReturn(resp);

    Page<CustomerSummaryResponse> result =
        customerService.list(STORE_ID, null, null, PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("All");
  }

  @Test
  void get_returnsResponse() {
    Customer c = new Customer();
    c.setId("c1");

    CustomerResponse resp = new CustomerResponse();
    resp.setId("c1");

    when(customerRepository.findResolvingMerge(STORE_ID, "c1")).thenReturn(Optional.of(c));
    when(customerMapper.toResponse(c)).thenReturn(resp);

    assertThat(customerService.get(STORE_ID, "c1").getId()).isEqualTo("c1");
  }

  @Test
  void get_throwsWhenNotFound() {
    when(customerRepository.findResolvingMerge(STORE_ID, "missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.get(STORE_ID, "missing"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  @Test
  void create_savesAndReturns() {
    CustomerCreateRequest req = new CustomerCreateRequest();
    req.setName("New");

    Customer customerEntity = Customer.builder().name("New").build();

    when(customerMapper.toEntity(req)).thenReturn(customerEntity);

    when(customerRepository.saveAndFlush(any()))
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

    CustomerResponse res = customerService.create(STORE_ID, req);
    assertThat(customerEntity.getStoreId()).isEqualTo(STORE_ID);
    assertThat(res.getId()).isEqualTo("new_id");
    assertThat(res.getName()).isEqualTo("New");
  }

  @Test
  void update_modifiesFields() {
    Customer c = new Customer();
    c.setId("c1");

    when(customerRepository.findByIdAndStoreId("c1", STORE_ID)).thenReturn(Optional.of(c));
    when(customerRepository.save(any())).thenReturn(c);

    CustomerUpdateRequest req = new CustomerUpdateRequest();
    req.setName("Updated");

    when(customerMapper.toPatch(req))
        .thenReturn(new CustomerPatch("Updated", null, null, null, null, null, null, null, null));

    CustomerResponse resp = new CustomerResponse();
    resp.setName("Updated");
    when(customerMapper.toResponse(c)).thenReturn(resp);

    CustomerResponse res = customerService.update(STORE_ID, "c1", req);
    assertThat(c.getName()).isEqualTo("Updated");
    assertThat(res.getName()).isEqualTo("Updated");
  }

  @Test
  void update_throwsWhenNotFound() {
    when(customerRepository.findByIdAndStoreId("missing", STORE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> customerService.update(STORE_ID, "missing", new CustomerUpdateRequest()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  @Test
  @DisplayName("一覧は行ごとに会員関連の有無を載せること")
  void list_decoratesMemberLink() {
    Customer linked = new Customer();
    linked.setId("c1");
    Customer unlinked = new Customer();
    unlinked.setId("c2");
    Page<CustomerListRepository.Row> page =
        new PageImpl<>(
            List.of(
                new CustomerListRepository.Row(linked, null, 0L, true),
                new CustomerListRepository.Row(unlinked, null, null, false)));

    CustomerSummaryResponse linkedResponse = new CustomerSummaryResponse();
    linkedResponse.setId("c1");
    CustomerSummaryResponse unlinkedResponse = new CustomerSummaryResponse();
    unlinkedResponse.setId("c2");

    when(customerListRepository.findAll(
            eq(STORE_ID), ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toSummaryResponse(linked)).thenReturn(linkedResponse);
    when(customerMapper.toSummaryResponse(unlinked)).thenReturn(unlinkedResponse);

    List<CustomerSummaryResponse> result =
        customerService.list(STORE_ID, null, null, PageRequest.of(0, 10)).getContent();

    assertThat(result.get(0).getMemberLinked()).isTrue();
    assertThat(result.get(1).getMemberLinked()).isFalse();
  }

  @Test
  @DisplayName("詳細は紐づけ済みなら会員コードを載せ、未紐づけでも member_linked が真偽値になること")
  void get_decoratesMemberLink() {
    Customer c = new Customer();
    c.setId("c1");
    CustomerResponse resp = new CustomerResponse();
    resp.setId("c1");

    when(customerRepository.findResolvingMerge(STORE_ID, "c1")).thenReturn(Optional.of(c));
    when(customerMapper.toResponse(c)).thenReturn(resp);
    when(customerMemberLinkRepository.findByStoreIdAndCustomerIdAndStatus(
            STORE_ID, "c1", LinkStatus.ACTIVE))
        .thenReturn(Optional.of(activeLink("c1", "123456789012")));

    CustomerResponse linked = customerService.get(STORE_ID, "c1");
    assertThat(linked.getMemberLinked()).isTrue();
    assertThat(linked.getLinkedMemberCode()).isEqualTo("123456789012");

    when(customerMemberLinkRepository.findByStoreIdAndCustomerIdAndStatus(
            STORE_ID, "c1", LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());

    CustomerResponse unlinked = customerService.get(STORE_ID, "c1");
    assertThat(unlinked.getMemberLinked()).isFalse();
    assertThat(unlinked.getLinkedMemberCode()).isNull();
  }

  @Test
  @DisplayName("詳細を旧 ID で引くと統合先の行が返り、統合済みであることと元の ID が載ること")
  void get_resolvesAMergedIdToTheSurvivingRow() {
    Customer surviving = new Customer();
    surviving.setId("c2");
    CustomerResponse resp = new CustomerResponse();
    resp.setId("c2");

    // 解決と取得を 1 文で行う。2 文に分けると、統合先を読んだ後に連鎖統合が確定した場合に
    // 既に墓標になった行を本体として返してしまう
    when(customerRepository.findResolvingMerge(STORE_ID, "c1")).thenReturn(Optional.of(surviving));
    when(customerMapper.toResponse(surviving)).thenReturn(resp);

    CustomerResponse result = customerService.get(STORE_ID, "c1");

    assertThat(result.getId()).isEqualTo("c2");
    assertThat(result.getMerged()).isTrue();
    assertThat(result.getMergedFromId()).isEqualTo("c1");
    verify(customerRepository, never()).findByIdAndStoreId(any(), any());
  }

  @Test
  @DisplayName("生きた行の詳細には統合の標識が載らないこと")
  void get_leavesTheMergeMarkOffALiveRow() {
    Customer c = new Customer();
    c.setId("c1");
    CustomerResponse resp = new CustomerResponse();
    resp.setId("c1");

    when(customerRepository.findResolvingMerge(STORE_ID, "c1")).thenReturn(Optional.of(c));
    when(customerMapper.toResponse(c)).thenReturn(resp);

    CustomerResponse result = customerService.get(STORE_ID, "c1");

    assertThat(result.getMerged()).isNull();
    assertThat(result.getMergedFromId()).isNull();
  }

  @Test
  @DisplayName("墓標への更新は 409 で撥ねられ、統合先を編集することが判ること")
  void update_rejectsTombstones() {
    Customer tombstone = new Customer();
    tombstone.setId("c1");
    when(customerRepository.findByIdAndStoreId("c1", STORE_ID)).thenReturn(Optional.of(tombstone));
    when(customerRepository.isMerged(STORE_ID, "c1")).thenReturn(true);

    assertThatThrownBy(() -> customerService.update(STORE_ID, "c1", new CustomerUpdateRequest()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合済みの顧客です。統合先の顧客を編集してください");
    verify(customerRepository, never()).save(any());
  }

  @Test
  @DisplayName("墓標の削除は、統合に関与した行の案内より先に統合済みとして撥ねられること")
  void delete_rejectsTombstonesBeforeTheInvolvementCheck() {
    when(customerRepository.findByIdAndStoreIdForUpdate("c1", STORE_ID))
        .thenReturn(Optional.of(aliveCustomer("c1")));
    when(customerRepository.isMerged(STORE_ID, "c1")).thenReturn(true);

    assertThatThrownBy(() -> customerService.delete(STORE_ID, "c1"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合済みの顧客です。統合先の顧客を編集してください");
    // 墓標は「統合に関与した行」でもあるので、後ろに置くと次の一手の違う案内に潰れる
    verify(customerMergeRepository, never()).existsInvolving(any(), any());
    verify(customerRepository, never()).delete(any(Customer.class));
  }

  private static CustomerMemberLink activeLink(String customerId, String memberCode) {
    return CustomerMemberLink.builder()
        .customerId(customerId)
        .memberId(7L)
        .memberCode(memberCode)
        .reason(LinkReason.MEMBER_CODE)
        .linkedBy(1L)
        .linkedAt(OffsetDateTime.parse("2026-07-01T10:00:00+09:00"))
        .build();
  }

  @Test
  void delete_removesIfExists() {
    Customer row = aliveCustomer("c1");
    when(customerRepository.findByIdAndStoreIdForUpdate("c1", STORE_ID))
        .thenReturn(Optional.of(row));
    customerService.delete(STORE_ID, "c1");
    verify(customerRepository).delete(row);
  }

  @Test
  @DisplayName("統合に関与した顧客の削除は 409 で撥ねられ、行が消えないこと")
  void delete_rejectsCustomersInvolvedInAMerge() {
    when(customerRepository.findByIdAndStoreIdForUpdate("c1", STORE_ID))
        .thenReturn(Optional.of(aliveCustomer("c1")));
    when(customerMergeRepository.existsInvolving(STORE_ID, "c1")).thenReturn(true);

    assertThatThrownBy(() -> customerService.delete(STORE_ID, "c1"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合");
    verify(customerRepository, never()).delete(any(Customer.class));
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(customerRepository.findByIdAndStoreIdForUpdate("missing", STORE_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.delete(STORE_ID, "missing"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  @Test
  @DisplayName("見比べる 2 行を、要求した並びのまま材料つきで返すこと")
  void mergeComparison_returnsBothRowsInTheRequestedOrderWithTheirMaterial() {
    List<String> requested = List.of("c2", "c1");
    Customer c1 = aliveCustomer("c1");
    Customer c2 = aliveCustomer("c2");
    // DB の並びは要求の並びと一致しない
    when(customerRepository.findByIdInAndStoreIdAndMergedIntoIdIsNull(requested, STORE_ID))
        .thenReturn(List.of(c1, c2));
    when(customerMemberLinkRepository.findByStoreIdAndCustomerIdInAndStatus(
            STORE_ID, requested, LinkStatus.ACTIVE))
        .thenReturn(List.of(activeLink("c1", "123456789012")));
    when(customerMergeRepository.countOrdersByCustomerId(STORE_ID, requested))
        .thenReturn(List.of(new OrderCountView("c2", 3)));
    // 材料が食い違えば写像は素通しの null になり、下の containsExactly が落ちる
    when(customerMapper.toComparisonResponse(c2, false, 3L, List.of()))
        .thenReturn(comparison("c2"));
    when(customerMapper.toComparisonResponse(c1, true, 0L, List.of())).thenReturn(comparison("c1"));

    List<CustomerMergeComparisonResponse> rows =
        customerService.mergeComparison(STORE_ID, requested);

    // 並びが要求と食い違うと、画面の左右が入れ替わって「残す行」の選択が別人を指す
    assertThat(rows).containsExactly(comparison("c2"), comparison("c1"));
  }

  @Test
  @DisplayName("同じ顧客を 2 つ指定した見比べは要求誤りとして撥ねること")
  void mergeComparison_rejectsTheSameCustomerTwice() {
    // 文言は見比べの言葉で述べる。この端点は統合しないので、統合の文言を借りると事実でない案内になる
    assertThatThrownBy(() -> customerService.mergeComparison(STORE_ID, List.of("c1", "c1")))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("同じ顧客どうしは見比べられません");
    verify(customerRepository, never()).findByIdInAndStoreIdAndMergedIntoIdIsNull(any(), any());
  }

  @Test
  @DisplayName("2 件でない指定は要求誤りとして撥ねること")
  void mergeComparison_rejectsAnythingButAPair() {
    // 3 行以上を一度に畳む導線は持たない（ADR 0010）。読み口の側でも 2 行に閉じる
    assertThatThrownBy(() -> customerService.mergeComparison(STORE_ID, List.of("c1", "c2", "c3")))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> customerService.mergeComparison(STORE_ID, List.of("c1")))
        .isInstanceOf(ServiceException.class);
    verify(customerRepository, never()).findByIdInAndStoreIdAndMergedIntoIdIsNull(any(), any());
  }

  @Test
  @DisplayName("片方が当店の生きた行でなければ 404 になること")
  void mergeComparison_rejectsWhenEitherRowIsNotAlive() {
    // 墓標・他店舗・不存在はどれもここに落ちる
    when(customerRepository.findByIdInAndStoreIdAndMergedIntoIdIsNull(
            List.of("c1", "gone"), STORE_ID))
        .thenReturn(List.of(aliveCustomer("c1")));

    assertThatThrownBy(() -> customerService.mergeComparison(STORE_ID, List.of("c1", "gone")))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  /** 受注件数の読み側 projection の最小の実装。 */
  private record OrderCountView(String customerId, long orderCount)
      implements CustomerOrderCountView {
    @Override
    public String getCustomerId() {
      return customerId;
    }

    @Override
    public long getOrderCount() {
      return orderCount;
    }
  }

  private static Customer aliveCustomer(String id) {
    Customer customer = Customer.builder().name(id).build();
    customer.setId(id);
    return customer;
  }

  /** 写像の結果は同一性だけを見るので、識別のつく id 以外は空でよい。 */
  private static CustomerMergeComparisonResponse comparison(String id) {
    return new CustomerMergeComparisonResponse(
        id, null, List.of(), null, null, null, null, null, null, null, false, 0L);
  }
}
