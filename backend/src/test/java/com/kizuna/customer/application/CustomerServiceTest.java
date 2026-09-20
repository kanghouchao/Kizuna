package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

  @Mock private CustomerContactService customerContactService;
  @Mock private CustomerContactRepository customerContactRepository;
  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerCandidateRepository candidateRepository;
  @Mock private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock private CustomerMergeRepository customerMergeRepository;
  @Mock private CustomerMapper customerMapper;

  @InjectMocks private CustomerService customerService;

  @Test
  void list_returnsPage() {
    Customer c = Customer.builder().name("Test").build();
    c.setId("1");
    Page<Customer> page = new PageImpl<>(List.of(c));

    CustomerSummaryResponse resp = new CustomerSummaryResponse();
    resp.setName("Test");

    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toSummaryResponse(c)).thenReturn(resp);

    Page<CustomerSummaryResponse> result =
        customerService.list("test", "VIP", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test");
  }

  @Test
  void list_withoutFilters_returnsAll() {
    Customer c = Customer.builder().name("All").build();
    c.setId("1");
    Page<Customer> page = new PageImpl<>(List.of(c));

    CustomerSummaryResponse resp = new CustomerSummaryResponse();
    resp.setName("All");

    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toSummaryResponse(c)).thenReturn(resp);

    Page<CustomerSummaryResponse> result = customerService.list(null, null, PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("All");
  }

  @Test
  void get_returnsResponse() {
    Customer c = new Customer();
    c.setId("c1");

    CustomerResponse resp = new CustomerResponse();
    resp.setId("c1");

    when(customerRepository.findResolvingMerge("c1")).thenReturn(Optional.of(c));
    when(customerMapper.toResponse(c)).thenReturn(resp);

    assertThat(customerService.get("c1").getId()).isEqualTo("c1");
  }

  @Test
  void get_throwsWhenNotFound() {
    when(customerRepository.findResolvingMerge("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.get("missing"))
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

    CustomerResponse res = customerService.create(req);
    assertThat(res.getId()).isEqualTo("new_id");
    assertThat(res.getName()).isEqualTo("New");
  }

  @Test
  void update_modifiesFields() {
    Customer c = new Customer();
    c.setId("c1");

    when(customerRepository.findById("c1")).thenReturn(Optional.of(c));
    when(customerRepository.save(any())).thenReturn(c);

    CustomerUpdateRequest req = new CustomerUpdateRequest();
    req.setName("Updated");

    when(customerMapper.toPatch(req))
        .thenReturn(new CustomerPatch("Updated", null, null, null, null, null, null, null));

    CustomerResponse resp = new CustomerResponse();
    resp.setName("Updated");
    when(customerMapper.toResponse(c)).thenReturn(resp);

    CustomerResponse res = customerService.update("c1", req);
    assertThat(c.getName()).isEqualTo("Updated");
    assertThat(res.getName()).isEqualTo("Updated");
  }

  @Test
  void update_throwsWhenNotFound() {
    when(customerRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.update("missing", new CustomerUpdateRequest()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  @Test
  @DisplayName("一覧は本ページ分の紐づけを 1 回で引き、行ごとに有無を載せること")
  void list_decoratesMemberLink() {
    Customer linked = new Customer();
    linked.setId("c1");
    Customer unlinked = new Customer();
    unlinked.setId("c2");
    Page<Customer> page = new PageImpl<>(List.of(linked, unlinked));

    CustomerSummaryResponse linkedResponse = new CustomerSummaryResponse();
    linkedResponse.setId("c1");
    CustomerSummaryResponse unlinkedResponse = new CustomerSummaryResponse();
    unlinkedResponse.setId("c2");

    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toSummaryResponse(linked)).thenReturn(linkedResponse);
    when(customerMapper.toSummaryResponse(unlinked)).thenReturn(unlinkedResponse);
    when(customerMemberLinkRepository.findByCustomerIdInAndStatus(
            List.of("c1", "c2"), LinkStatus.ACTIVE))
        .thenReturn(List.of(activeLink("c1", "123456789012")));

    List<CustomerSummaryResponse> result =
        customerService.list(null, null, PageRequest.of(0, 10)).getContent();

    assertThat(result.get(0).getMemberLinked()).isTrue();
    assertThat(result.get(1).getMemberLinked()).isFalse();
    verify(customerMemberLinkRepository).findByCustomerIdInAndStatus(any(), any());
  }

  @Test
  @DisplayName("詳細は紐づけ済みなら会員コードを載せ、未紐づけでも member_linked が真偽値になること")
  void get_decoratesMemberLink() {
    Customer c = new Customer();
    c.setId("c1");
    CustomerResponse resp = new CustomerResponse();
    resp.setId("c1");

    when(customerRepository.findResolvingMerge("c1")).thenReturn(Optional.of(c));
    when(customerMapper.toResponse(c)).thenReturn(resp);
    when(customerMemberLinkRepository.findByCustomerIdAndStatus("c1", LinkStatus.ACTIVE))
        .thenReturn(Optional.of(activeLink("c1", "123456789012")));

    CustomerResponse linked = customerService.get("c1");
    assertThat(linked.getMemberLinked()).isTrue();
    assertThat(linked.getLinkedMemberCode()).isEqualTo("123456789012");

    when(customerMemberLinkRepository.findByCustomerIdAndStatus("c1", LinkStatus.ACTIVE))
        .thenReturn(Optional.empty());

    CustomerResponse unlinked = customerService.get("c1");
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
    when(customerRepository.findResolvingMerge("c1")).thenReturn(Optional.of(surviving));
    when(customerMapper.toResponse(surviving)).thenReturn(resp);

    CustomerResponse result = customerService.get("c1");

    assertThat(result.getId()).isEqualTo("c2");
    assertThat(result.getMerged()).isTrue();
    assertThat(result.getMergedFromId()).isEqualTo("c1");
    verify(customerRepository, never()).findById(any());
  }

  @Test
  @DisplayName("生きた行の詳細には統合の標識が載らないこと")
  void get_leavesTheMergeMarkOffALiveRow() {
    Customer c = new Customer();
    c.setId("c1");
    CustomerResponse resp = new CustomerResponse();
    resp.setId("c1");

    when(customerRepository.findResolvingMerge("c1")).thenReturn(Optional.of(c));
    when(customerMapper.toResponse(c)).thenReturn(resp);

    CustomerResponse result = customerService.get("c1");

    assertThat(result.getMerged()).isNull();
    assertThat(result.getMergedFromId()).isNull();
  }

  @Test
  @DisplayName("墓標への更新は 409 で撥ねられ、統合先を編集することが判ること")
  void update_rejectsTombstones() {
    Customer tombstone = new Customer();
    tombstone.setId("c1");
    when(customerRepository.findById("c1")).thenReturn(Optional.of(tombstone));
    when(customerRepository.isMerged("c1")).thenReturn(true);

    assertThatThrownBy(() -> customerService.update("c1", new CustomerUpdateRequest()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合済みの顧客です。統合先の顧客を編集してください");
    verify(customerRepository, never()).save(any());
  }

  @Test
  @DisplayName("墓標の削除は、統合に関与した行の案内より先に統合済みとして撥ねられること")
  void delete_rejectsTombstonesBeforeTheInvolvementCheck() {
    when(customerRepository.findByIdForUpdate("c1")).thenReturn(Optional.of(aliveCustomer("c1")));
    when(customerRepository.isMerged("c1")).thenReturn(true);

    assertThatThrownBy(() -> customerService.delete("c1"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合済みの顧客です。統合先の顧客を編集してください");
    // 墓標は「統合に関与した行」でもあるので、後ろに置くと次の一手の違う案内に潰れる
    verify(customerMergeRepository, never()).existsInvolving(any());
    verify(customerRepository, never()).deleteById(any());
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
    when(customerRepository.findByIdForUpdate("c1")).thenReturn(Optional.of(aliveCustomer("c1")));
    customerService.delete("c1");
    verify(customerRepository).deleteById("c1");
  }

  @Test
  @DisplayName("統合に関与した顧客の削除は 409 で撥ねられ、行が消えないこと")
  void delete_rejectsCustomersInvolvedInAMerge() {
    when(customerRepository.findByIdForUpdate("c1")).thenReturn(Optional.of(aliveCustomer("c1")));
    when(customerMergeRepository.existsInvolving("c1")).thenReturn(true);

    assertThatThrownBy(() -> customerService.delete("c1"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合");
    verify(customerRepository, never()).deleteById("c1");
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(customerRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.delete("missing"))
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
    when(customerRepository.findByIdInAndMergedIntoIdIsNull(requested)).thenReturn(List.of(c1, c2));
    when(customerMemberLinkRepository.findByCustomerIdInAndStatus(requested, LinkStatus.ACTIVE))
        .thenReturn(List.of(activeLink("c1", "123456789012")));
    when(customerMergeRepository.countOrdersByCustomerId(requested))
        .thenReturn(List.of(new OrderCountView("c2", 3)));
    // 材料が食い違えば写像は素通しの null になり、下の containsExactly が落ちる
    when(customerMapper.toComparisonResponse(c2, false, 3L, List.of()))
        .thenReturn(comparison("c2"));
    when(customerMapper.toComparisonResponse(c1, true, 0L, List.of())).thenReturn(comparison("c1"));

    List<CustomerMergeComparisonResponse> rows = customerService.mergeComparison(requested);

    // 並びが要求と食い違うと、画面の左右が入れ替わって「残す行」の選択が別人を指す
    assertThat(rows).containsExactly(comparison("c2"), comparison("c1"));
  }

  @Test
  @DisplayName("同じ顧客を 2 つ指定した見比べは要求誤りとして撥ねること")
  void mergeComparison_rejectsTheSameCustomerTwice() {
    // 文言は見比べの言葉で述べる。この端点は統合しないので、統合の文言を借りると事実でない案内になる
    assertThatThrownBy(() -> customerService.mergeComparison(List.of("c1", "c1")))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("同じ顧客どうしは見比べられません");
    verify(customerRepository, never()).findByIdInAndMergedIntoIdIsNull(any());
  }

  @Test
  @DisplayName("2 件でない指定は要求誤りとして撥ねること")
  void mergeComparison_rejectsAnythingButAPair() {
    // 3 行以上を一度に畳む導線は持たない（ADR 0010）。読み口の側でも 2 行に閉じる
    assertThatThrownBy(() -> customerService.mergeComparison(List.of("c1", "c2", "c3")))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> customerService.mergeComparison(List.of("c1")))
        .isInstanceOf(ServiceException.class);
    verify(customerRepository, never()).findByIdInAndMergedIntoIdIsNull(any());
  }

  @Test
  @DisplayName("片方が当店の生きた行でなければ 404 になること")
  void mergeComparison_rejectsWhenEitherRowIsNotAlive() {
    // 墓標・他店舗・不存在はどれもここに落ちる（店舗の絞り込みは storeFilter が担う）
    when(customerRepository.findByIdInAndMergedIntoIdIsNull(List.of("c1", "gone")))
        .thenReturn(List.of(aliveCustomer("c1")));

    assertThatThrownBy(() -> customerService.mergeComparison(List.of("c1", "gone")))
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
