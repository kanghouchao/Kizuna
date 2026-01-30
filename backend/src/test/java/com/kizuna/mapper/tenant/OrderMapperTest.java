package com.kizuna.mapper.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.model.dto.tenant.order.OrderCreateRequest;
import com.kizuna.model.dto.tenant.order.OrderResponse;
import com.kizuna.model.dto.tenant.order.OrderUpdateRequest;
import com.kizuna.model.entity.tenant.Order;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class OrderMapperTest {

  private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);

  @Test
  void testToEntity() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setStoreName("S1");
    Order order = mapper.toEntity(req);
    assertThat(order.getStoreName()).isEqualTo("S1");
  }

  @Test
  void testToResponse() {
    Order order = new Order();
    order.setId("o1");
    OrderResponse res = mapper.toResponse(order);
    assertThat(res.getId()).isEqualTo("o1");
  }

  @Test
  void testUpdate() {
    Order order = new Order();
    order.setStoreName("Old");
    OrderUpdateRequest req = new OrderUpdateRequest();
    req.setStoreName("New");
    mapper.updateEntityFromRequest(req, order);
    assertThat(order.getStoreName()).isEqualTo("New");
  }
}
