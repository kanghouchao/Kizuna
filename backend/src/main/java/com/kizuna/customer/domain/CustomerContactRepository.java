package com.kizuna.customer.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CustomerContactRepository
    extends StoreScopedRepository<CustomerContact>, JpaSpecificationExecutor<CustomerContact> {
  @Query(
      """
      select count(c) > 0 from com.kizuna.customer.domain.CustomerContact c
      join com.kizuna.customer.domain.Customer customer on customer.id = c.customerId
      where c.storeId = :storeId and customer.storeId = c.storeId
        and c.type = :type and c.value = :value and c.deleted = false
        and c.businessStatus = com.kizuna.customer.domain.ContactPermissionStatus.DENIED
        and customer.mergedIntoId is null
      """)
  boolean hasBusinessDenial(Long storeId, ContactType type, String value);

  List<CustomerContact> findByStoreIdAndCustomerIdAndDeletedFalseAndIdGreaterThanOrderByIdAsc(
      Long storeId, String customerId, String id, Limit limit);

  @Query(
      """
      select distinct c.type as type, c.value as value,
        c.businessStatus as businessStatus, c.marketingStatus as marketingStatus
      from com.kizuna.customer.domain.CustomerContact c
      where c.storeId = :storeId and c.customerId = :customerId and c.deleted = false
        and c.value in :values
      """)
  List<ContactRestrictionView> findRestrictions(
      Long storeId, String customerId, Collection<String> values);

  List<CustomerContact> findByStoreIdAndCustomerIdAndTypeAndValueAndDeletedFalse(
      Long storeId, String customerId, ContactType type, String value);

  Optional<CustomerContact> findByIdAndStoreIdAndCustomerIdAndDeletedFalse(
      String id, Long storeId, String customerId);

  List<CustomerContact> findByStoreIdAndCustomerIdInAndPreferredTrueAndDeletedFalseOrderByIdAsc(
      Long storeId, Collection<String> ids);

  List<CustomerContact> findByStoreIdAndCustomerIdOrderByIdAsc(Long storeId, String customerId);

  /** その顧客を現在の持ち主か出生時の持ち主とする連絡先があるか。 */
  @Query(
      """
      select count(c) > 0 from com.kizuna.customer.domain.CustomerContact c
      where c.storeId = :storeId
        and (c.customerId = :customerId or c.originCustomerId = :customerId)
      """)
  boolean existsReferencing(Long storeId, String customerId);

  @Query(
      "select c from com.kizuna.customer.domain.CustomerContact c where c.storeId = :storeId and c.customerId = :customerId and c.deleted = false and c.preferred = true and c.type = :type")
  Optional<CustomerContact> findPreferred(Long storeId, String customerId, ContactType type);
}
