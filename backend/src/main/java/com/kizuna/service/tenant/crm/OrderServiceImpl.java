package com.kizuna.service.tenant.crm;

import com.kizuna.mapper.tenant.CustomerMapper;
import com.kizuna.mapper.tenant.OrderMapper;
import com.kizuna.model.dto.tenant.order.OrderCreateRequest;
import com.kizuna.model.dto.tenant.order.OrderResponse;
import com.kizuna.model.dto.tenant.order.OrderUpdateRequest;
import com.kizuna.model.entity.tenant.Customer;
import com.kizuna.model.entity.tenant.Order;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.CastRepository;
import com.kizuna.repository.tenant.CustomerRepository;
import com.kizuna.repository.tenant.OrderRepository;
import com.kizuna.repository.tenant.TenantUserRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
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
  private final CastRepository castRepository;
  private final TenantUserRepository tenantUserRepository;
  private final TenantRepository tenantRepository;
  private final TenantContext tenantContext;
  private final OrderMapper orderMapper;
  private final CustomerMapper customerMapper;

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public Page<OrderResponse> list(Pageable pageable) {
    return orderRepository.findAll(pageable).map(orderMapper::toResponse);
  }

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public OrderResponse get(String id) {
    return orderRepository
        .findById(id)
        .map(orderMapper::toResponse)
        .orElseThrow(() -> new ServiceException("注文が見つかりません: " + id));
  }

  @Override
  @TenantScoped
  @Transactional
  public OrderResponse create(OrderCreateRequest request) {
    // MapStructを使用して基本的なフィールドをマッピング
    Order order = orderMapper.toEntity(request);

    // テナントの設定
    order.setTenant(
        tenantRepository
            .findById(tenantContext.getTenantId())
            .orElseThrow(() -> new ServiceException("テナントが見つかりません")));

    // 複雑な関連ロジックの処理（顧客のスマートリンク）
    handleCustomerLinking(request, order);

    // その他のID関連の処理
    order.setCast(
        castRepository
            .findById(request.getCastId())
            .orElseThrow(() -> new ServiceException("キャストが見つかりません: " + request.getCastId())));
    order.setReceptionist(
        tenantUserRepository
            .findById(request.getReceptionistId())
            .orElseThrow(
                () -> new ServiceException("受付担当者が見つかりません: " + request.getReceptionistId())));

    return orderMapper.toResponse(orderRepository.save(order));
  }

  @Override
  @TenantScoped
  @Transactional
  public OrderResponse update(String id, OrderUpdateRequest request) {
    Order order =
        orderRepository.findById(id).orElseThrow(() -> new ServiceException("注文が見つかりません: " + id));

    // MapStructを使用して非nullフィールドを自動更新
    orderMapper.updateEntityFromRequest(request, order);

    // 関連IDの更新処理（MapStructではDB検索ができないため手動で実施）
    order.setReceptionist(
        tenantUserRepository
            .findById(request.getReceptionistId())
            .orElseThrow(
                () -> new ServiceException("受付担当者が見つかりません: " + request.getReceptionistId())));
    order.setCast(
        castRepository
            .findById(request.getCastId())
            .orElseThrow(() -> new ServiceException("キャストが見つかりません: " + request.getCastId())));

    return orderMapper.toResponse(orderRepository.save(order));
  }

  @Override
  @TenantScoped
  @Transactional
  public void delete(String id) {
    if (!orderRepository.existsById(id)) {
      throw new ServiceException("注文が見つかりません: " + id);
    }
    orderRepository.deleteById(id);
  }

  private void handleCustomerLinking(OrderCreateRequest req, Order order) {
    if (req.getCustomerId() != null && !req.getCustomerId().isEmpty()) {
      order.setCustomer(
          customerRepository
              .findById(req.getCustomerId())
              .orElseThrow(() -> new ServiceException("顧客が見つかりません: " + req.getCustomerId())));
    } else if (req.getPhoneNumber() != null && !req.getPhoneNumber().isEmpty()) {
      // 顧客の検索または作成
      Customer customer =
          customerRepository
              .findByPhoneNumberAndTenantId(req.getPhoneNumber(), tenantContext.getTenantId())
              .orElseGet(
                  () -> {
                    Customer newCustomer = customerMapper.toCustomer(req);
                    // テナントを明示的に設定
                    newCustomer.setTenant(
                        tenantRepository
                            .findById(tenantContext.getTenantId())
                            .orElseThrow(() -> new ServiceException("テナントが見つかりません")));
                    return customerRepository.save(newCustomer);
                  });
      order.setCustomer(customer);
    }
  }
}
