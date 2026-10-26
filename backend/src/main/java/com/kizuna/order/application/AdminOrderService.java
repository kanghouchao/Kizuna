package com.kizuna.order.application;

import com.kizuna.order.api.dto.AdminOrderResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.storescope.StoreScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** グループ級 principal（授権店舗集合）での受注横断ユースケース（集合作用域）。 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

  private final OrderRepository orderRepository;
  private final OrderMapper orderMapper;

  /** 授権店舗集合での受注横断一覧。SPECIFIC_STORES は集合内の店舗だけ、ALL_STORES は全店舗を返す。 */
  @Transactional(readOnly = true)
  public Page<AdminOrderResponse> list(StoreScope scope, Pageable pageable) {
    var sort = pageable.getSort().and(Sort.by(Order::getId).descending());
    var page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    var views =
        scope.allStores()
            ? orderRepository.findGroupWideViews(page)
            : orderRepository.findGroupWideViewsInStores(scope.storeIds(), page);
    return views.map(orderMapper::toAdminResponse);
  }
}
