package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderSpecialServiceEvent;
import com.kizuna.order.domain.OrderSpecialServiceEventRepository;
import com.kizuna.service.application.OrderSpecialServiceCatalog;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.domain.StoreRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderSpecialServicesTest {
  private final OrderSpecialServiceEventRepository events =
      mock(OrderSpecialServiceEventRepository.class);
  private final OrderSpecialServices specialServices =
      new OrderSpecialServices(
          mock(OrderSpecialServiceCatalog.class),
          events,
          mock(OrderRepository.class),
          mock(StoreRepository.class));

  private Order order() {
    Order order = mock(Order.class);
    when(order.getId()).thenReturn("o1");
    return order;
  }

  @Test
  void anUnrepairedRejectionStopsProgressAsARequestError() {
    // 読み直しても再送しても変わらない前提の未達。競合（409）にすると画面が取り直しへ誘導する
    when(events.unresolved(7L, "o1")).thenReturn(List.of(mock(OrderSpecialServiceEvent.class)));

    assertThatThrownBy(() -> specialServices.requireProgress(7L, order()))
        .isInstanceOf(ServiceException.class)
        .hasMessage("拒否された特殊サービスを編集画面で修復してください");
  }

  @Test
  void progressIsAllowedOnceEveryRejectionIsRepaired() {
    when(events.unresolved(7L, "o1")).thenReturn(List.of());

    assertThatCode(() -> specialServices.requireProgress(7L, order())).doesNotThrowAnyException();
  }
}
