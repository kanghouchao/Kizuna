package com.kizuna.service.tenant;

import com.kizuna.model.dto.tenant.customer.CustomerCreateRequest;
import com.kizuna.model.dto.tenant.customer.CustomerResponse;
import com.kizuna.model.dto.tenant.customer.CustomerUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerService {
  Page<CustomerResponse> list(String search, Pageable pageable);

  CustomerResponse get(String id);

  CustomerResponse create(CustomerCreateRequest request);

  CustomerResponse update(String id, CustomerUpdateRequest request);

  void delete(String id);
}
