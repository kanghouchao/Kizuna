package com.kizuna.order.application;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.Capability;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final CustomerRepository customerRepository;
  private final CastRepository castRepository;
  private final PlatformUserRepository platformUserRepository;
  private final CapabilityBundleRepository capabilityBundleRepository;
  private final TenantRepository tenantRepository;
  private final TenantContext tenantContext;
  private final OrderMapper orderMapper;

  @TenantScoped
  @Transactional(readOnly = true)
  public Page<OrderResponse> list(String customerId, Pageable pageable) {
    // 一覧は集約を経由せず JPQL join projection で取得（D3）。customerId は顧客詳細の注文履歴用
    String filter = (customerId == null || customerId.isBlank()) ? null : customerId;
    return orderRepository.findAllViews(filter, pageable).map(orderMapper::toResponse);
  }

  @TenantScoped
  @Transactional(readOnly = true)
  public OrderResponse get(String id) {
    return toResponse(id);
  }

  @TenantScoped
  @Transactional
  public OrderResponse create(OrderCreateRequest request) {
    // MapStructを使用して基本的なフィールドをマッピング
    Order order = orderMapper.toEntity(request);

    // テナントの設定
    order.setStoreId(
        tenantRepository
            .findById(tenantContext.getTenantId())
            .orElseThrow(() -> new ServiceException("テナントが見つかりません"))
            .getId());

    // 複雑な関連ロジックの処理（顧客のスマートリンク）
    handleCustomerLinking(request, order);

    // 関連 ID の割り当て（存在確認のうえ）
    if (!castRepository.existsById(request.getCastId())) {
      throw new ServiceException("キャストが見つかりません: " + request.getCastId());
    }
    order.assignCast(request.getCastId());
    validateReceptionist(request.getReceptionistId());
    order.assignReceptionist(request.getReceptionistId());

    Order saved = orderRepository.save(order);
    return toResponse(saved.getId());
  }

  @TenantScoped
  @Transactional
  public OrderResponse update(String id, OrderUpdateRequest request) {
    Order order =
        orderRepository.findById(id).orElseThrow(() -> new ServiceException("注文が見つかりません: " + id));

    // 非nullフィールドのみをドメインの部分更新コマンドとして適用
    order.apply(orderMapper.toPatch(request));

    // 関連 ID の更新（存在確認のうえ）
    validateReceptionist(request.getReceptionistId());
    order.assignReceptionist(request.getReceptionistId());
    if (!castRepository.existsById(request.getCastId())) {
      throw new ServiceException("キャストが見つかりません: " + request.getCastId());
    }
    order.assignCast(request.getCastId());

    // ステータスはドメインの遷移メソッド経由で変更（不正な遷移はドメイン例外 → 400）
    if (request.getStatus() != null) {
      order.transitionTo(parseStatus(request.getStatus()));
    }

    Order saved = orderRepository.save(order);
    return toResponse(saved.getId());
  }

  private OrderResponse toResponse(String id) {
    return orderRepository
        .findViewById(id)
        .map(orderMapper::toResponse)
        .orElseThrow(() -> new ServiceException("注文が見つかりません: " + id));
  }

  private OrderStatus parseStatus(String raw) {
    try {
      return OrderStatus.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new ServiceException("不正な注文ステータスです: " + raw);
    }
  }

  // 受付担当者は「有効(enabled)かつ受注管理能力（ORDER_MANAGE）を持つ STAFF」かつ「現テナント(店舗)を授権する
  // PlatformUser」でなければならない。platform_users には tenant_id が無いため、単なる存在確認では
  // 他店舗/CAST/MEMBER も通ってしまう。停止済み(enabled=false)の口座は束・授権を保持したままなので明示的に弾く。
  // userType 判定を先行させ、束を持たない CAST/MEMBER で束問い合わせ（空 in 句）へ進まないようにする。
  private void validateReceptionist(Long receptionistId) {
    Long storeId = tenantContext.getTenantId();
    platformUserRepository
        .findById(receptionistId)
        .filter(
            user ->
                user.getUserType() == UserType.STAFF
                    && user.getEnabled()
                    && user.authorizes(storeId)
                    && capabilityBundleRepository.anyBundleHasCapability(
                        user.getBundleIds(), Capability.ORDER_MANAGE))
        .orElseThrow(() -> new ServiceException("受付担当者が見つかりません: " + receptionistId));
  }

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
      if (!customerRepository.existsById(req.getCustomerId())) {
        throw new ServiceException("顧客が見つかりません: " + req.getCustomerId());
      }
      order.linkCustomer(req.getCustomerId());
    } else if (req.getPhoneNumber() != null && !req.getPhoneNumber().isEmpty()) {
      // 顧客の検索または作成
      Customer customer =
          customerRepository
              .findByPhoneNumberAndStoreId(req.getPhoneNumber(), tenantContext.getTenantId())
              .orElseGet(
                  () -> {
                    Customer newCustomer = orderMapper.toCustomer(req);
                    // テナントを明示的に設定
                    newCustomer.setStoreId(
                        tenantRepository
                            .findById(tenantContext.getTenantId())
                            .orElseThrow(() -> new ServiceException("テナントが見つかりません"))
                            .getId());
                    return customerRepository.save(newCustomer);
                  });
      order.linkCustomer(customer.getId());
    }
  }
}
