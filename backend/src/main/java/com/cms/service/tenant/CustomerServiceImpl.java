package com.cms.service.tenant;

import com.cms.config.TenantScoped;
import com.cms.exception.ServiceException;
import com.cms.model.dto.tenant.customer.CustomerCreateRequest;
import com.cms.model.dto.tenant.customer.CustomerResponse;
import com.cms.model.dto.tenant.customer.CustomerUpdateRequest;
import com.cms.model.entity.tenant.Customer;
import com.cms.repository.tenant.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

  private final CustomerRepository customerRepository;

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public Page<CustomerResponse> list(String search, Pageable pageable) {
    if (search != null && !search.isEmpty()) {
      return customerRepository
          .findByNameContainingIgnoreCase(search, pageable)
          .map(this::toResponse);
    }
    return customerRepository.findAll(pageable).map(this::toResponse);
  }

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public CustomerResponse get(String id) {
    return customerRepository
        .findById(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ServiceException("Customer not found: " + id));
  }

  @Override
  @TenantScoped
  @Transactional
  public CustomerResponse create(CustomerCreateRequest request) {
    Customer customer = new Customer();
    customer.setName(request.getName());
    customer.setPhoneNumber(request.getPhoneNumber());
    customer.setPhoneNumber2(request.getPhoneNumber2());
    customer.setAddress(request.getAddress());
    customer.setBuildingName(request.getBuildingName());
    customer.setClassification(request.getClassification());
    customer.setHasPet(request.getHasPet());
    customer.setNgType(request.getNgType());
    customer.setNgContent(request.getNgContent());
    // Defaults
    customer.setPoints(0);

    return toResponse(customerRepository.save(customer));
  }

  @Override
  @TenantScoped
  @Transactional
  public CustomerResponse update(String id, CustomerUpdateRequest request) {
    Customer customer =
        customerRepository
            .findById(id)
            .orElseThrow(() -> new ServiceException("Customer not found: " + id));

    if (request.getName() != null) customer.setName(request.getName());
    if (request.getPhoneNumber() != null) customer.setPhoneNumber(request.getPhoneNumber());
    if (request.getPhoneNumber2() != null) customer.setPhoneNumber2(request.getPhoneNumber2());
    if (request.getAddress() != null) customer.setAddress(request.getAddress());
    if (request.getBuildingName() != null) customer.setBuildingName(request.getBuildingName());
    if (request.getClassification() != null)
      customer.setClassification(request.getClassification());
    if (request.getHasPet() != null) customer.setHasPet(request.getHasPet());
    if (request.getNgType() != null) customer.setNgType(request.getNgType());
    if (request.getNgContent() != null) customer.setNgContent(request.getNgContent());

    return toResponse(customerRepository.save(customer));
  }

  @Override
  @TenantScoped
  @Transactional
  public void delete(String id) {
    if (!customerRepository.existsById(id)) {
      throw new ServiceException("Customer not found: " + id);
    }
    customerRepository.deleteById(id);
  }

  private CustomerResponse toResponse(Customer customer) {
    return CustomerResponse.builder()
        .id(customer.getId())
        .name(customer.getName())
        .phoneNumber(customer.getPhoneNumber())
        .phoneNumber2(customer.getPhoneNumber2())
        .address(customer.getAddress())
        .buildingName(customer.getBuildingName())
        .classification(customer.getClassification())
        .hasPet(customer.getHasPet())
        .points(customer.getPoints())
        .ngType(customer.getNgType())
        .ngContent(customer.getNgContent())
        .build();
  }
}
