package com.cms.service.tenant;

import com.cms.model.dto.tenant.order.OrderCreateRequest;
import com.cms.model.dto.tenant.order.OrderResponse;
import com.cms.model.dto.tenant.order.OrderUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
  Page<OrderResponse> list(Pageable pageable);

  OrderResponse get(String id);

  OrderResponse create(OrderCreateRequest request);

  OrderResponse update(String id, OrderUpdateRequest request);

  void delete(String id);
}
