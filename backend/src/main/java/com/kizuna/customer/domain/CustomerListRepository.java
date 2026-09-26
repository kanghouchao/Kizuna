package com.kizuna.customer.domain;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface CustomerListRepository {
  record Row(Customer customer, LocalDate lastVisitDate, Long pointBalance, boolean memberLinked) {}

  Page<Row> findAll(Specification<Customer> specification, Pageable pageable);
}
