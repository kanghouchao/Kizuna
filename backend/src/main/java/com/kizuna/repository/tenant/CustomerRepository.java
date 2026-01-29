package com.kizuna.repository.tenant;

import com.kizuna.model.entity.tenant.Customer;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository
    extends JpaRepository<Customer, String>, JpaSpecificationExecutor<Customer> {
  Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);

  Optional<Customer> findByPhoneNumber(String phoneNumber);
}
