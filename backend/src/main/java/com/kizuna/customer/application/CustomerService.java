package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerMapper;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScoped;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

  /** LIKE パターンのエスケープ規則。派生クエリが内部で使うものと同一で、手書きの cb.like にも同じ規則を適用する。 */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  private static final String MERGED_CUSTOMER_UNDELETABLE =
      "統合に関与した顧客は削除できません。統合履歴と旧 ID の解決の根拠になります";

  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final CustomerMergeRepository customerMergeRepository;
  private final CustomerMapper customerMapper;

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<CustomerResponse> list(
      String search, String rank, String classification, Pageable pageable) {
    Specification<Customer> spec = searchSpec(search, rank, classification);
    Page<Customer> page = customerRepository.findAll(spec, pageable);
    // 会員紐づけは本ページ分だけを 1 回で引く（行ごとの追加問い合わせを作らない）。
    List<String> ids = page.getContent().stream().map(Customer::getId).toList();
    Map<String, String> activeCodes =
        ids.isEmpty()
            ? Map.of()
            : customerMemberLinkRepository
                .findByCustomerIdInAndStatus(ids, LinkStatus.ACTIVE)
                .stream()
                .collect(
                    Collectors.toMap(
                        CustomerMemberLink::getCustomerId, CustomerMemberLink::getMemberCode));
    return page.map(
        customer ->
            withMemberLink(customerMapper.toResponse(customer), activeCodes.get(customer.getId())));
  }

  /**
   * 検索語は 名前・電話番号・LINE ID を横断し、rank / classification は完全一致の絞り込み。 null の条件は述語を生成しない（JPQL の ":param is
   * null or ..." パターンは PostgreSQL の null パラメータ型推論で 500 になるため Specification で組み立てる）。
   */
  private static Specification<Customer> searchSpec(
      String search, String rank, String classification) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (search != null) {
        char escape = LIKE_ESCAPE.getEscapeCharacter();
        String pattern = "%" + LIKE_ESCAPE.escape(search.toLowerCase()) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, escape),
                cb.like(root.get("phoneNumber"), "%" + LIKE_ESCAPE.escape(search) + "%", escape),
                cb.like(cb.lower(root.get("lineId")), pattern, escape)));
      }
      if (rank != null) {
        predicates.add(cb.equal(root.get("rank"), rank));
      }
      if (classification != null) {
        predicates.add(cb.equal(root.get("classification"), classification));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CustomerResponse get(String id) {
    return customerRepository
        .findById(id)
        .map(customerMapper::toResponse)
        .map(response -> withMemberLink(response, activeMemberCodeOf(id)))
        .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
  }

  @StoreScoped
  @Transactional
  public CustomerResponse create(CustomerCreateRequest request) {
    // store_id は StoreScopeStampListener が @PrePersist で採番する
    Customer customer = customerMapper.toEntity(request);
    // 作成直後の顧客は定義上まだ会員と紐づいていない
    return withMemberLink(customerMapper.toResponse(customerRepository.save(customer)), null);
  }

  @StoreScoped
  @Transactional
  public CustomerResponse update(String id, CustomerUpdateRequest request) {
    Customer customer =
        customerRepository.findById(id).orElseThrow(() -> new NotFoundException("顧客が見つかりません"));

    customer.apply(customerMapper.toPatch(request));

    return withMemberLink(
        customerMapper.toResponse(customerRepository.save(customer)), activeMemberCodeOf(id));
  }

  /**
   * 顧客を削除する。統合に関与した行は存続行・被統合行のいずれも削除できない — 統合履歴が誤統合の唯一の修復根拠であり、旧 ID の解決も
   * 墓標が残っていて初めて届くため、誤削除で消えるほうが重い（ADR 0010）。
   */
  @StoreScoped
  @Transactional
  public void delete(String id) {
    if (!customerRepository.existsById(id)) {
      throw new NotFoundException("顧客が見つかりません");
    }
    if (customerMergeRepository.existsInvolving(id)) {
      throw new ConflictException(MERGED_CUSTOMER_UNDELETABLE);
    }
    try {
      customerRepository.deleteById(id);
      // DELETE を今この場へ流す。トランザクション境界の commit まで遅れると、外部キー違反が
      // この catch を素通りして全域ハンドラの兜底（500）へ落ちる。
      customerRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      // 事前判定と削除の間に統合が確定した競合の最終防波堤。統合に関与した行は履歴の 2 本と、存続行なら
      // 墓標からの自己参照にも指されており、どれが先に違反として現れるかは DB の検査順に依るので 3 本とも
      // 同じ案内へ写す。写像を持たない他の整合性違反（受注が残っている等）は translate が元の例外を返し、
      // 従来どおり大きく失敗する。
      throw IntegrityViolations.translate(
          ex,
          Map.of(
              DbConstraint.FK_T_CUSTOMER_MERGES_SURVIVING,
              () -> new ConflictException(MERGED_CUSTOMER_UNDELETABLE),
              DbConstraint.FK_T_CUSTOMER_MERGES_MERGED,
              () -> new ConflictException(MERGED_CUSTOMER_UNDELETABLE),
              DbConstraint.FK_T_CUSTOMERS_MERGED_INTO,
              () -> new ConflictException(MERGED_CUSTOMER_UNDELETABLE)));
    }
  }

  private String activeMemberCodeOf(String customerId) {
    return customerMemberLinkRepository
        .findByCustomerIdAndStatus(customerId, LinkStatus.ACTIVE)
        .map(CustomerMemberLink::getMemberCode)
        .orElse(null);
  }

  /** 会員紐づけの投影を載せる。memberLinked は関連状態の有無そのものなので、常に真偽値が入る。 */
  private static CustomerResponse withMemberLink(CustomerResponse response, String memberCode) {
    response.setMemberLinked(memberCode != null);
    response.setLinkedMemberCode(memberCode);
    return response;
  }
}
