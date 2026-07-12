package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.PlatformOrderResponse;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.tenancy.StoreSetScoped;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台 principal（授権店舗集合）での受注横断ユースケース（#323 集合作用域）。 */
@Service
@RequiredArgsConstructor
public class PlatformOrderService {

  private final OrderRepository orderRepository;
  private final OrderMapper orderMapper;

  /** 授権店舗集合での受注横断一覧。濾過は storeSetFilter（@StoreSetScoped）が機構的に行う。 */
  @StoreSetScoped
  @Transactional(readOnly = true)
  public Page<PlatformOrderResponse> list(Pageable pageable) {
    return orderRepository.findPlatformViews(pageable).map(orderMapper::toPlatformResponse);
  }
}
