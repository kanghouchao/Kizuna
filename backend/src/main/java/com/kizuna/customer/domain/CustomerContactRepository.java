package com.kizuna.customer.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CustomerContactRepository
    extends JpaRepository<CustomerContact, String>, JpaSpecificationExecutor<CustomerContact> {
  List<CustomerContact> findByCustomerIdAndDeletedFalseAndIdGreaterThanOrderByIdAsc(
      String customerId, String id, Limit limit);

  @Query(
      """
      select distinct c.type as type, c.value as value,
        c.businessStatus as businessStatus, c.marketingStatus as marketingStatus
      from com.kizuna.customer.domain.CustomerContact c
      where c.customerId = :customerId and c.deleted = false and c.value in :values
      """)
  List<ContactRestrictionView> findRestrictions(String customerId, Collection<String> values);

  List<CustomerContact> findByCustomerIdAndTypeAndValueAndDeletedFalse(
      String customerId, ContactType type, String value);

  Optional<CustomerContact> findByIdAndCustomerIdAndDeletedFalse(String id, String customerId);

  List<CustomerContact> findByCustomerIdInAndPreferredTrueAndDeletedFalseOrderByIdAsc(
      Collection<String> ids);

  List<CustomerContact> findByCustomerIdOrderByIdAsc(String customerId);

  boolean existsByCustomerIdOrOriginCustomerId(String customerId, String originCustomerId);

  @Query(
      "select c from com.kizuna.customer.domain.CustomerContact c where c.customerId = :customerId and c.deleted = false and c.preferred = true and c.type = :type")
  Optional<CustomerContact> findPreferred(String customerId, ContactType type);
}
