package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.customer.domain.Customer;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.StoreUserRepository;
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
  private final StoreUserRepository storeUserRepository;
  private final TenantRepository tenantRepository;
  private final TenantContext tenantContext;
  private final OrderMapper orderMapper;

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
    order.setTenantId(
        tenantRepository
            .findById(tenantContext.getTenantId())
            .orElseThrow(() -> new ServiceException("テナントが見つかりません"))
            .getId());

    // 複雑な関連ロジックの処理（顧客のスマートリンク）
    handleCustomerLinking(request, order);

    // その他のID関連の処理
    order.setCast(
        castRepository
            .findById(request.getCastId())
            .orElseThrow(() -> new ServiceException("キャストが見つかりません: " + request.getCastId())));
    order.setReceptionist(
        storeUserRepository
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
        storeUserRepository
            .findById(request.getReceptionistId())
            .orElseThrow(
                () -> new ServiceException("受付担当者が見つかりません: " + request.getReceptionistId())));
    order.setCast(
        castRepository
            .findById(request.getCastId())
            .orElseThrow(() -> new ServiceException("キャストが見つかりません: " + request.getCastId())));

    // ステータスはドメインの遷移メソッド経由で変更（不正な遷移はドメイン例外 → 400）
    if (request.getStatus() != null) {
      order.transitionTo(parseStatus(request.getStatus()));
    }

    return orderMapper.toResponse(orderRepository.save(order));
  }

  private OrderStatus parseStatus(String raw) {
    try {
      return OrderStatus.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new ServiceException("不正な注文ステータスです: " + raw);
    }
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
                    Customer newCustomer = orderMapper.toCustomer(req);
                    // テナントを明示的に設定
                    newCustomer.setTenantId(
                        tenantRepository
                            .findById(tenantContext.getTenantId())
                            .orElseThrow(() -> new ServiceException("テナントが見つかりません"))
                            .getId());
                    return customerRepository.save(newCustomer);
                  });
      order.setCustomer(customer);
    }
  }
}
