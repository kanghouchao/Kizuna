package com.kizuna.service.tenant;

import com.kizuna.config.TenantScoped;
import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.exception.ServiceException;
import com.kizuna.mapper.tenant.CustomerMapper;
import com.kizuna.model.dto.tenant.customer.CustomerCreateRequest;
import com.kizuna.model.dto.tenant.customer.CustomerResponse;
import com.kizuna.model.dto.tenant.customer.CustomerUpdateRequest;
import com.kizuna.model.entity.tenant.Customer;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

  private final CustomerRepository customerRepository;
  private final CustomerMapper customerMapper;
  private final TenantContext tenantContext;
  private final TenantRepository tenantRepository;

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public Page<CustomerResponse> list(String search, Pageable pageable) {
    if (search != null && !search.isEmpty()) {
      return customerRepository
          .findByNameContainingIgnoreCase(search, pageable)
          .map(customerMapper::toResponse);
    }
    return customerRepository.findAll(pageable).map(customerMapper::toResponse);
  }

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public CustomerResponse get(String id) {
    return customerRepository
        .findById(id)
        .map(customerMapper::toResponse)
        .orElseThrow(() -> new ServiceException("顧客が見つかりません: " + id));
  }

  @Override
  @TenantScoped
  @Transactional
  public CustomerResponse create(CustomerCreateRequest request) {
    Customer customer = customerMapper.toEntity(request);

    customer.setTenant(
        tenantRepository
            .findById(tenantContext.getTenantId())
            .orElseThrow(() -> new ServiceException("テナントが見つかりません")));

    return customerMapper.toResponse(customerRepository.save(customer));
  }

  @Override
  @TenantScoped
  @Transactional
  public CustomerResponse update(String id, CustomerUpdateRequest request) {
    Customer customer =
        customerRepository
            .findById(id)
            .orElseThrow(() -> new ServiceException("顧客が見つかりません: " + id));

    customerMapper.updateEntityFromRequest(request, customer);

    return customerMapper.toResponse(customerRepository.save(customer));
  }

  @Override
  @TenantScoped
  @Transactional
  public void delete(String id) {
    if (!customerRepository.existsById(id)) {
      throw new ServiceException("顧客が見つかりません: " + id);
    }
    customerRepository.deleteById(id);
  }
}
