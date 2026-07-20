package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerMapper;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.store.domain.StoreRepository;
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

  private final CustomerRepository customerRepository;
  private final CustomerMapper customerMapper;
  private final StoreContext storeContext;
  private final StoreRepository storeRepository;

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
        String pattern = "%" + search.toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(root.get("phoneNumber"), "%" + search + "%"),
                cb.like(cb.lower(root.get("lineId")), pattern)));
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

  @StoreScoped
  @Transactional(readOnly = true)
  public CustomerResponse get(String id) {
    return customerRepository
        .findById(id)
        .map(customerMapper::toResponse)
        .orElseThrow(() -> new ServiceException("顧客が見つかりません: " + id));
  }

  @StoreScoped
  @Transactional
  public CustomerResponse create(CustomerCreateRequest request) {
    Customer customer = customerMapper.toEntity(request);

    customer.setStoreId(
        storeRepository
            .findById(storeContext.getStoreId())
            .orElseThrow(() -> new ServiceException("店舗が見つかりません"))
            .getId());

    return customerMapper.toResponse(customerRepository.save(customer));
  }

  @StoreScoped
  @Transactional
  public CustomerResponse update(String id, CustomerUpdateRequest request) {
    Customer customer =
        customerRepository
            .findById(id)
            .orElseThrow(() -> new ServiceException("顧客が見つかりません: " + id));

    customer.apply(customerMapper.toPatch(request));

    return customerMapper.toResponse(customerRepository.save(customer));
  }

  @StoreScoped
  @Transactional
  public void delete(String id) {
    if (!customerRepository.existsById(id)) {
      throw new ServiceException("顧客が見つかりません: " + id);
    }
    customerRepository.deleteById(id);
  }
}
