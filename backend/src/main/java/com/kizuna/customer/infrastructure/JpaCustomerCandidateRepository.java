package com.kizuna.customer.infrastructure;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerCandidateRepository;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactSearch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaCustomerCandidateRepository implements CustomerCandidateRepository {
  private final EntityManager em;

  @Override
  public Page<Group> groups(Long storeId, String search, ContactType type, Pageable pageable) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var contact = query.from(CustomerContact.class);
    var customer = query.from(Customer.class);
    var predicates = new ArrayList<Predicate>();
    predicates.add(live(cb, contact, customer, storeId));
    if (type != null) predicates.add(cb.equal(contact.get("type"), type));
    if (search != null && !search.isBlank())
      predicates.add(CustomerContactSearch.matches(contact, cb, search));
    // 列の文字列表現で並べ、enum の宣言順と DB の文字列順の差を持ち込まない。
    var typeKey = contact.get("type").as(String.class);
    var total = cb.countDistinct(customer.get("id"));
    query
        .select(cb.tuple(contact.get("type"), contact.get("value"), total))
        .where(predicates.toArray(Predicate[]::new))
        .groupBy(contact.get("type"), contact.get("value"))
        .having(cb.ge(total, 2))
        .orderBy(cb.asc(typeKey), cb.asc(contact.get("value")));
    var rows =
        page(em.createQuery(query), pageable).getResultList().stream()
            .map(
                row ->
                    new Group(
                        row.get(0, ContactType.class),
                        row.get(1, String.class),
                        row.get(2, Long.class)))
            .toList();
    return new PageImpl<>(rows, pageable, count(query));
  }

  @Override
  public List<Match> members(Long storeId, List<Group> groups) {
    if (groups.isEmpty()) return List.of();
    var cb = em.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var contact = query.from(CustomerContact.class);
    var customer = query.from(Customer.class);
    var keys =
        groups.stream()
            .map(
                group ->
                    cb.and(
                        cb.equal(contact.get("type"), group.type()),
                        cb.equal(contact.get("value"), group.value())))
            .toArray(Predicate[]::new);
    query
        .select(cb.tuple(contact.get("type"), contact.get("value"), customer))
        .distinct(true)
        .where(live(cb, contact, customer, storeId), cb.or(keys))
        .orderBy(cb.asc(customer.get("id")));
    return em.createQuery(query).getResultList().stream().map(this::match).toList();
  }

  @Override
  public Page<Customer> members(Long storeId, ContactType type, String value, Pageable pageable) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery(Customer.class);
    var customer = query.from(Customer.class);
    var contact = query.from(CustomerContact.class);
    query
        .select(customer)
        .distinct(true)
        .where(
            live(cb, contact, customer, storeId),
            cb.equal(contact.get("type"), type),
            cb.equal(contact.get("value"), value))
        .orderBy(cb.asc(customer.get("id")));
    return new PageImpl<>(
        page(em.createQuery(query), pageable).getResultList(), pageable, count(query));
  }

  private static <T> TypedQuery<T> page(TypedQuery<T> query, Pageable pageable) {
    return query.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
  }

  // group by / distinct の結果行を数えるため、Hibernate が問い合わせを副問い合わせで包んで数える形を使う。
  // 同じ述語から数えるので、件数と行が別々の絞り込みでずれることがない。
  private long count(CriteriaQuery<?> query) {
    return em.createQuery(((JpaCriteriaQuery<?>) query).createCountQuery()).getSingleResult();
  }

  private Predicate live(
      CriteriaBuilder cb, Root<CustomerContact> contact, Root<Customer> customer, Long storeId) {
    return cb.and(
        cb.equal(customer.get("storeId"), storeId),
        cb.equal(contact.get("customerId"), customer.get("id")),
        cb.equal(contact.get("storeId"), customer.get("storeId")),
        cb.isFalse(contact.get("deleted")),
        cb.isNull(customer.get("mergedIntoId")));
  }

  private Match match(Tuple row) {
    return new Match(
        row.get(0, ContactType.class), row.get(1, String.class), row.get(2, Customer.class));
  }
}
