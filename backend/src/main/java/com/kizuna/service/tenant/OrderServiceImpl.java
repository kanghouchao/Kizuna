package com.kizuna.service.tenant;

import com.kizuna.config.TenantScoped;
import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.exception.ServiceException;
import com.kizuna.model.dto.tenant.order.OrderCreateRequest;
import com.kizuna.model.dto.tenant.order.OrderResponse;
import com.kizuna.model.dto.tenant.order.OrderUpdateRequest;
import com.kizuna.model.entity.tenant.Order;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.CustomerRepository;
import com.kizuna.repository.tenant.GirlRepository;
import com.kizuna.repository.tenant.OrderRepository;
import com.kizuna.repository.tenant.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

  private final OrderRepository orderRepository;
  private final CustomerRepository customerRepository;
  private final GirlRepository girlRepository;
  private final TenantUserRepository tenantUserRepository;
  private final TenantRepository tenantRepository;
  private final TenantContext tenantContext;

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public Page<OrderResponse> list(Pageable pageable) {
    return orderRepository.findAll(pageable).map(this::toResponse);
  }

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public OrderResponse get(String id) {
    return orderRepository
        .findById(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ServiceException("Order not found: " + id));
  }

  @Override
  @TenantScoped
  @Transactional
  public OrderResponse create(OrderCreateRequest request) {
    Order order = new Order();
    mapRequestToEntity(request, order);
    return toResponse(orderRepository.save(order));
  }

  @Override
  @TenantScoped
  @Transactional
  public OrderResponse update(String id, OrderUpdateRequest request) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new ServiceException("Order not found: " + id));

    // Update fields
    if (request.getStoreName() != null) order.setStoreName(request.getStoreName());
    if (request.getReceptionistId() != null) {
      order.setReceptionist(
          tenantUserRepository
              .findById(request.getReceptionistId())
              .orElseThrow(
                  () ->
                      new ServiceException(
                          "Receptionist not found: " + request.getReceptionistId())));
    }
    if (request.getArrivalScheduledStartTime() != null)
      order.setArrivalScheduledStartTime(request.getArrivalScheduledStartTime());
    if (request.getArrivalScheduledEndTime() != null)
      order.setArrivalScheduledEndTime(request.getArrivalScheduledEndTime());
    if (request.getGirlId() != null) {
      order.setGirl(
          girlRepository
              .findById(request.getGirlId())
              .orElseThrow(() -> new ServiceException("Girl not found: " + request.getGirlId())));
    }
    if (request.getCourseMinutes() != null) order.setCourseMinutes(request.getCourseMinutes());
    if (request.getExtensionMinutes() != null)
      order.setExtensionMinutes(request.getExtensionMinutes());
    if (request.getOptionCodes() != null) order.setOptionCodes(request.getOptionCodes());
    if (request.getDiscountName() != null) order.setDiscountName(request.getDiscountName());
    if (request.getManualDiscount() != null) order.setManualDiscount(request.getManualDiscount());
    if (request.getUsedPoints() != null) order.setUsedPoints(request.getUsedPoints());
    if (request.getManualGrantPoints() != null)
      order.setManualGrantPoints(request.getManualGrantPoints());
    if (request.getRemarks() != null) order.setRemarks(request.getRemarks());
    if (request.getGirlDriverMessage() != null)
      order.setGirlDriverMessage(request.getGirlDriverMessage());
    if (request.getStatus() != null) order.setStatus(request.getStatus());

    return toResponse(orderRepository.save(order));
  }

  @Override
  @TenantScoped
  @Transactional
  public void delete(String id) {
    if (!orderRepository.existsById(id)) {
      throw new ServiceException("Order not found: " + id);
    }
    orderRepository.deleteById(id);
  }

  private void mapRequestToEntity(OrderCreateRequest req, Order order) {
    order.setStoreName(req.getStoreName());
    order.setBusinessDate(req.getBusinessDate());
    order.setArrivalScheduledStartTime(req.getArrivalScheduledStartTime());
    order.setArrivalScheduledEndTime(req.getArrivalScheduledEndTime());
    order.setCourseMinutes(req.getCourseMinutes());
    order.setExtensionMinutes(req.getExtensionMinutes());
    order.setOptionCodes(req.getOptionCodes());
    order.setDiscountName(req.getDiscountName());
    order.setManualDiscount(req.getManualDiscount());
    order.setCarrier(req.getCarrier());
    order.setMediaName(req.getMediaName());
    order.setUsedPoints(req.getUsedPoints());
    order.setManualGrantPoints(req.getManualGrantPoints());
    order.setRemarks(req.getRemarks());
    order.setGirlDriverMessage(req.getGirlDriverMessage());
    order.setStatus("CREATED");

    // Set location info from request
    order.setLocationAddress(req.getAddress());
    order.setLocationBuilding(req.getBuildingName());

    // Smart Customer Linking
    if (req.getCustomerId() != null && !req.getCustomerId().isEmpty()) {
      order.setCustomer(
          customerRepository
              .findById(req.getCustomerId())
              .orElseThrow(
                  () -> new ServiceException("Customer not found: " + req.getCustomerId())));
    } else if (req.getPhoneNumber() != null && !req.getPhoneNumber().isEmpty()) {
      // Find or Create Customer
      com.kizuna.model.entity.tenant.Customer customer =
          customerRepository
              .findByPhoneNumber(req.getPhoneNumber())
              .orElseGet(
                  () -> {
                    com.kizuna.model.entity.tenant.Customer newCustomer =
                        new com.kizuna.model.entity.tenant.Customer();
                    newCustomer.setName(req.getCustomerName());
                    newCustomer.setPhoneNumber(req.getPhoneNumber());
                    newCustomer.setPhoneNumber2(req.getPhoneNumber2());
                    newCustomer.setAddress(req.getAddress());
                    newCustomer.setBuildingName(req.getBuildingName());
                    newCustomer.setClassification(req.getClassification());
                    newCustomer.setLandmark(req.getLandmark());
                    newCustomer.setHasPet(req.getHasPet() != null ? req.getHasPet() : false);
                    newCustomer.setNgType(req.getNgType());
                    newCustomer.setNgContent(req.getNgContent());
                    // Set Tenant Explicitly
                    newCustomer.setTenant(
                        tenantRepository
                            .findById(tenantContext.getTenantId())
                            .orElseThrow(() -> new ServiceException("Tenant context not found")));
                    return customerRepository.save(newCustomer);
                  });
      order.setCustomer(customer);
    }

    if (req.getGirlId() != null && !req.getGirlId().isEmpty()) {
      order.setGirl(
          girlRepository
              .findById(req.getGirlId())
              .orElseThrow(() -> new ServiceException("Girl not found: " + req.getGirlId())));
    }
    if (req.getReceptionistId() != null && !req.getReceptionistId().isEmpty()) {
      order.setReceptionist(
          tenantUserRepository
              .findById(req.getReceptionistId())
              .orElseThrow(
                  () ->
                      new ServiceException("Receptionist not found: " + req.getReceptionistId())));
    }
  }

  private OrderResponse toResponse(Order order) {
    return OrderResponse.builder()
        .id(order.getId())
        .storeName(order.getStoreName())
        .receptionistId(order.getReceptionist() != null ? order.getReceptionist().getId() : null)
        .receptionistName(
            order.getReceptionist() != null ? order.getReceptionist().getNickname() : null)
        .businessDate(order.getBusinessDate())
        .arrivalScheduledStartTime(order.getArrivalScheduledStartTime())
        .arrivalScheduledEndTime(order.getArrivalScheduledEndTime())
        .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
        .customerName(order.getCustomer() != null ? order.getCustomer().getName() : null)
        .girlId(order.getGirl() != null ? order.getGirl().getId() : null)
        .girlName(order.getGirl() != null ? order.getGirl().getName() : null)
        .courseMinutes(order.getCourseMinutes())
        .extensionMinutes(order.getExtensionMinutes())
        .optionCodes(order.getOptionCodes())
        .discountName(order.getDiscountName())
        .manualDiscount(order.getManualDiscount())
        .carrier(order.getCarrier())
        .mediaName(order.getMediaName())
        .usedPoints(order.getUsedPoints())
        .manualGrantPoints(order.getManualGrantPoints())
        .remarks(order.getRemarks())
        .girlDriverMessage(order.getGirlDriverMessage())
        .status(order.getStatus())
        // Added location fields
        .locationAddress(order.getLocationAddress())
        .locationBuilding(order.getLocationBuilding())
        .build();
  }
}
