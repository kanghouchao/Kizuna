package com.kizuna.customer.infrastructure;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerCandidateRepository;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactSearch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaCustomerCandidateRepository implements CustomerCandidateRepository {
  private final EntityManager em;

  @Override
  public List<Group> groups(
      String search, ContactType type, ContactType afterType, String afterValue, int limit) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var contact = query.from(CustomerContact.class);
    var customer = query.from(Customer.class);
    var predicates = new ArrayList<Predicate>();
    predicates.add(live(cb, contact, customer));
    if (type != null) predicates.add(cb.equal(contact.get("type"), type));
    if (search != null && !search.isBlank())
      predicates.add(CustomerContactSearch.matches(contact, cb, search));
    // 列の文字列表現で比較し、enum の宣言順と DB の文字列順の差を持ち込まない。
    var typeKey = contact.get("type").as(String.class);
    if (afterType != null)
      predicates.add(
          cb.or(
              cb.greaterThan(typeKey, afterType.name()),
              cb.and(
                  cb.equal(contact.get("type"), afterType),
                  cb.greaterThan(contact.get("value"), afterValue))));
    var total = cb.countDistinct(customer.get("id"));
    query
        .select(cb.tuple(contact.get("type"), contact.get("value"), total))
        .where(predicates.toArray(Predicate[]::new))
        .groupBy(contact.get("type"), contact.get("value"))
        .having(cb.ge(total, 2))
        .orderBy(cb.asc(typeKey), cb.asc(contact.get("value")));
    return em.createQuery(query).setMaxResults(limit).getResultList().stream()
        .map(
            row ->
                new Group(
                    row.get(0, ContactType.class),
                    row.get(1, String.class),
                    row.get(2, Long.class)))
        .toList();
  }

  @Override
  public List<Match> members(List<Group> groups) {
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
        .where(live(cb, contact, customer), cb.or(keys))
        .orderBy(cb.asc(customer.get("id")));
    return em.createQuery(query).getResultList().stream().map(this::match).toList();
  }

  @Override
  public List<Customer> members(ContactType type, String value, String afterId, int limit) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery(Customer.class);
    var customer = query.from(Customer.class);
    var contact = query.from(CustomerContact.class);
    query
        .select(customer)
        .distinct(true)
        .where(
            live(cb, contact, customer),
            cb.equal(contact.get("type"), type),
            cb.equal(contact.get("value"), value),
            cb.greaterThan(customer.get("id"), afterId))
        .orderBy(cb.asc(customer.get("id")));
    return em.createQuery(query).setMaxResults(limit).getResultList();
  }

  private Predicate live(
      CriteriaBuilder cb, Root<CustomerContact> contact, Root<Customer> customer) {
    return cb.and(
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
