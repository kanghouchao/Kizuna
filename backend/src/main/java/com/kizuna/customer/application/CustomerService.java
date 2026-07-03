package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerService {
  Page<CustomerResponse> list(String search, String rank, String classification, Pageable pageable);

  CustomerResponse get(String id);

  CustomerResponse create(CustomerCreateRequest request);

  CustomerResponse update(String id, CustomerUpdateRequest request);

  void delete(String id);
}
