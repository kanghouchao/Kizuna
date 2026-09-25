package com.kizuna.order.domain;

import com.kizuna.customer.domain.ContactType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessContactHistoryRepository
    extends JpaRepository<BusinessContactHistory, String> {
  Optional<BusinessContactHistory> findFirstByOrderIdAndTypeOrderByIdDesc(
      String orderId, ContactType type);

  List<BusinessContactHistory> findByOrderIdAndIdLessThanOrderByIdDesc(
      String orderId, String id, Limit limit);
}
