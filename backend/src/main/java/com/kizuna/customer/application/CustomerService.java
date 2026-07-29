package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerMapper;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScoped;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

  /** LIKE パターンのエスケープ文字。検索語中のメタ文字を字面へ戻すために使う。 */
  private static final char LIKE_ESCAPE = '\\';

  private final CustomerRepository customerRepository;
  private final CustomerMapper customerMapper;

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<CustomerResponse> list(
      String search, String rank, String classification, Pageable pageable) {
    Specification<Customer> spec =
        searchSpec(blankToNull(search), blankToNull(rank), blankToNull(classification));
    return customerRepository.findAll(spec, pageable).map(customerMapper::toResponse);
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
        String pattern = "%" + escapeLike(search.toLowerCase()) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                cb.like(root.get("phoneNumber"), "%" + escapeLike(search) + "%", LIKE_ESCAPE),
                cb.like(cb.lower(root.get("lineId")), pattern, LIKE_ESCAPE)));
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

  private static String blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

  /**
   * 検索語中の LIKE メタ文字を字面へ戻す。
   *
   * <p>エスケープしないと {@code _} が 1 文字ワイルドカードとして働き、{@code a_b} の検索が {@code acb} のような別人まで拾う。エスケープ文字自身を先に
   * 二重化しないと、末尾の {@code \} が後続の {@code %} を打ち消す。
   */
  private static String escapeLike(String value) {
    return value
        .replace(String.valueOf(LIKE_ESCAPE), String.valueOf(LIKE_ESCAPE) + LIKE_ESCAPE)
        .replace("%", LIKE_ESCAPE + "%")
        .replace("_", LIKE_ESCAPE + "_");
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CustomerResponse get(String id) {
    return customerRepository
        .findById(id)
        .map(customerMapper::toResponse)
        .orElseThrow(() -> new NotFoundException("顧客が見つかりません: " + id));
  }

  @StoreScoped
  @Transactional
  public CustomerResponse create(CustomerCreateRequest request) {
    // store_id は StoreScopeStampListener が @PrePersist で採番する
    Customer customer = customerMapper.toEntity(request);
    return customerMapper.toResponse(customerRepository.save(customer));
  }

  @StoreScoped
  @Transactional
  public CustomerResponse update(String id, CustomerUpdateRequest request) {
    Customer customer =
        customerRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("顧客が見つかりません: " + id));

    customer.apply(customerMapper.toPatch(request));

    return customerMapper.toResponse(customerRepository.save(customer));
  }

  @StoreScoped
  @Transactional
  public void delete(String id) {
    if (!customerRepository.existsById(id)) {
      throw new NotFoundException("顧客が見つかりません: " + id);
    }
    customerRepository.deleteById(id);
  }
}
