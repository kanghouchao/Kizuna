package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
  Page<OrderResponse> list(Pageable pageable);

  OrderResponse get(String id);

  OrderResponse create(OrderCreateRequest request);

  OrderResponse update(String id, OrderUpdateRequest request);

  void delete(String id);
}
