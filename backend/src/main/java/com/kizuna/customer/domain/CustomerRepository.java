package com.kizuna.customer.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository
    extends JpaRepository<Customer, String>, JpaSpecificationExecutor<Customer> {

  Optional<Customer> findByPhoneNumber(String phoneNumber);

  Optional<Customer> findByPhoneNumberAndTenantId(String phoneNumber, Long tenantId);
}
