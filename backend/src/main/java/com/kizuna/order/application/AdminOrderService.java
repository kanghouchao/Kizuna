package com.kizuna.order.application;

import com.kizuna.order.domain.AdminOrderView;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.persistence.Paging;
import com.kizuna.shared.storescope.StoreScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** グループ級 principal（授権店舗集合）での受注横断ユースケース（集合作用域）。 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

  private final OrderRepository orderRepository;

  /** 授権店舗集合での受注横断一覧。集合内の店舗の受注だけを返し、集合が空なら空の頁を返す（空の IN は組み立てない）。 */
  @Transactional(readOnly = true)
  public Page<AdminOrderView> list(StoreScope scope, Pageable pageable) {
    var page = Paging.totalOrder(pageable, Sort.Order.asc(Order::getId));
    if (scope.storeIds().isEmpty()) return Page.empty(page);
    return orderRepository.findGroupWideViewsInStores(scope.storeIds(), page);
  }
}
