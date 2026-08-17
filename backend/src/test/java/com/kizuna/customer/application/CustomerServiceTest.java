package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerDuplicateGroupResponse;
import com.kizuna.customer.api.dto.CustomerMapper;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerSummaryResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerDuplicateGroupView;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerPatch;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock private CustomerMergeRepository customerMergeRepository;
  @Mock private CustomerMapper customerMapper;

  /** 1 ページの要求件数。上限そのものではなく、呼出側が渡す値の扱いを見る。 */
  private static final int PAGE_SIZE = 20;

  @InjectMocks private CustomerService customerService;

  @Test
  void list_returnsPage() {
    Customer c = Customer.builder().name("Test").build();
    Page<Customer> page = new PageImpl<>(List.of(c));

    CustomerSummaryResponse resp = new CustomerSummaryResponse();
    resp.setName("Test");

    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toSummaryResponse(c)).thenReturn(resp);

    Page<CustomerSummaryResponse> result =
        customerService.list("test", "GOLD", "VIP", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test");
  }

  @Test
  void list_withoutFilters_returnsAll() {
    Customer c = Customer.builder().name("All").build();
    Page<Customer> page = new PageImpl<>(List.of(c));

    CustomerSummaryResponse resp = new CustomerSummaryResponse();
    resp.setName("All");

    when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(), any(PageRequest.class)))
        .thenReturn(page);
    when(customerMapper.toSummaryResponse(c)).thenReturn(resp);

    Page<CustomerSummaryResponse> result =
        customerService.list(null, null, null, PageRequest.of(0, 10));
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

    when(customerRepository.save(any()))
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
        .thenReturn(
            new CustomerPatch(
                "Updated", null, null, null, null, null, null, null, null, null, null, null));

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
        customerService.list(null, null, null, PageRequest.of(0, 10)).getContent();

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
    when(customerRepository.existsById("c1")).thenReturn(true);
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
    when(customerRepository.existsById("c1")).thenReturn(true);
    customerService.delete("c1");
    verify(customerRepository).deleteById("c1");
  }

  @Test
  @DisplayName("統合に関与した顧客の削除は 409 で撥ねられ、行が消えないこと")
  void delete_rejectsCustomersInvolvedInAMerge() {
    when(customerRepository.existsById("c1")).thenReturn(true);
    when(customerMergeRepository.existsInvolving("c1")).thenReturn(true);

    assertThatThrownBy(() -> customerService.delete("c1"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合");
    verify(customerRepository, never()).deleteById("c1");
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(customerRepository.existsById("missing")).thenReturn(false);

    assertThatThrownBy(() -> customerService.delete("missing"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }

  /**
   * カーソルの組み立てそのものは統合テストでは固定しない。上限を超える重複を実データで起こすと、同じ店舗の台帳を共有する
   * 他のテストの候補まで押し出して、無関係なテストが候補を見失う。何件返して続きをどう名乗るかはサービス自身の責務なので、ここで持つ。
   */
  @Test
  @DisplayName("要求件数を超えた分は返さず、続きの位置を名乗ること")
  void listDuplicateCandidates_reportsTheNextCursorInsteadOfSilentlyCutting() {
    List<CustomerDuplicateGroupView> groups =
        IntStream.rangeClosed(1, PAGE_SIZE + 1)
            .mapToObj(i -> (CustomerDuplicateGroupView) new GroupView("0900000" + i, 2))
            .toList();
    when(customerRepository.findDuplicatePhoneNumbers(any(Limit.class))).thenReturn(groups);
    // 要求件数に収まる番号だけが引き直され、超過分の行は取りに行かない
    List<String> keptPhoneNumbers =
        groups.subList(0, PAGE_SIZE).stream()
            .map(CustomerDuplicateGroupView::getPhoneNumber)
            .toList();
    when(customerRepository.findByPhoneNumberInAndMergedIntoIdIsNullOrderByPhoneNumberAscIdAsc(
            keptPhoneNumbers))
        .thenReturn(keptPhoneNumbers.stream().flatMap(CustomerServiceTest::duplicatePair).toList());
    when(customerMemberLinkRepository.findByCustomerIdInAndStatus(any(), any()))
        .thenReturn(List.of());
    when(customerMergeRepository.countOrdersByCustomerId(any())).thenReturn(List.of());
    // 写像の結果は数えるだけなので mapper は素通し（既定の null）でよい。ここで見たいのは行の中身
    // ではなく、何グループを返して続きをどう名乗るか。

    CursorPage<CustomerDuplicateGroupResponse> page =
        customerService.listDuplicateCandidates(null, PAGE_SIZE);

    assertThat(page.content()).hasSize(PAGE_SIZE);
    // 続きを名乗らないと、番号を共有する同伴者のような正当な偽陽性が先頭を占めたとき、
    // 以降の真の重複が一生画面に出ない
    assertThat(PageCursor.decodeKey(page.nextCursor()))
        .isEqualTo(keptPhoneNumbers.get(PAGE_SIZE - 1));
  }

  @Test
  @DisplayName("続きが無いときは次の位置を名乗らないこと")
  void listDuplicateCandidates_reportsNoCursorWhenEverythingFits() {
    when(customerRepository.findDuplicatePhoneNumbers(any(Limit.class))).thenReturn(List.of());

    CursorPage<CustomerDuplicateGroupResponse> page =
        customerService.listDuplicateCandidates(null, PAGE_SIZE);

    assertThat(page.content()).isEmpty();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  @DisplayName("カーソルを渡された取得は、その位置より後ろだけを引くこと")
  void listDuplicateCandidates_readsOnlyBeyondTheCursor() {
    when(customerRepository.findDuplicatePhoneNumbersAfter(eq("090-1111-2222"), any(Limit.class)))
        .thenReturn(List.of());

    CursorPage<CustomerDuplicateGroupResponse> page =
        customerService.listDuplicateCandidates(PageCursor.encodeKey("090-1111-2222"), PAGE_SIZE);

    assertThat(page.content()).isEmpty();
    // 先頭からの取得へ落とすと、続きを求めた呼出側に 1 ページ目が返って取りこぼしが成功に見える
    verify(customerRepository, never()).findDuplicatePhoneNumbers(any(Limit.class));
  }

  @Test
  @DisplayName("重複候補の読み口が 1 つの断面を要求すること")
  void duplicateCandidatesRunInASingleSnapshot() throws NoSuchMethodException {
    // 見出し・行・紐づけ・受注件数と 4 回問い合わせる群読み口。既定の READ COMMITTED では文ごとに
    // 断面を取り直すため、間に他者の commit が挟まると total だけ古いまま行が増え、上限に収まると
    // 数えたグループが上限を超えて返る。断面が実際に保たれることは OrderGroupReadSnapshotIT が
    // 本物の PostgreSQL で見る
    Method method =
        CustomerService.class.getMethod("listDuplicateCandidates", String.class, int.class);

    Transactional tx = method.getAnnotation(Transactional.class);
    assertThat(tx).as("@Transactional があること").isNotNull();
    assertThat(tx.isolation()).as("1 つの断面を要求すること").isEqualTo(Isolation.REPEATABLE_READ);
  }

  /** 読み側 projection の最小の実装。件数は行を引く前に判る（{@code having} が既に数えている）。 */
  private record GroupView(String phoneNumber, long total) implements CustomerDuplicateGroupView {
    @Override
    public String getPhoneNumber() {
      return phoneNumber;
    }

    @Override
    public long getTotal() {
      return total;
    }
  }

  /** グループを成す最小の形（同じ番号の 2 行）。 */
  private static Stream<Customer> duplicatePair(String phoneNumber) {
    return Stream.of(
        Customer.builder().phoneNumber(phoneNumber).build(),
        Customer.builder().phoneNumber(phoneNumber).build());
  }
}
