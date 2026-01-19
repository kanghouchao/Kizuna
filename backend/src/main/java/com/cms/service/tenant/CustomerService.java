package com.cms.service.tenant;

import com.cms.model.dto.tenant.customer.CustomerCreateRequest;
import com.cms.model.dto.tenant.customer.CustomerResponse;
import com.cms.model.dto.tenant.customer.CustomerUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerService {
  Page<CustomerResponse> list(String search, Pageable pageable);

  CustomerResponse get(String id);

  CustomerResponse create(CustomerCreateRequest request);

  CustomerResponse update(String id, CustomerUpdateRequest request);

  void delete(String id);
}
