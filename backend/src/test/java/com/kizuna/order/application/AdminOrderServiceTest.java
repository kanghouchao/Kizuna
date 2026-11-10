package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.order.domain.AdminOrderView;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.storescope.StoreScope;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock OrderService orderService;

  @InjectMocks AdminOrderService service;

  @Test
  void storeSetBindsTheGrantedStoreIds() {
    AdminOrderView view = mock(AdminOrderView.class);
    Page<AdminOrderView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 10), 1);
    when(orderRepository.findGroupWideViewsInStores(eq(Set.of(3L, 5L)), any(Pageable.class)))
        .thenReturn(page);

    var result = service.list(new StoreScope(Set.of(3L, 5L)), PageRequest.of(0, 10));

    assertThat(result.getContent()).containsExactly(view);
  }

  @Test
  void pageEndsInOrderIdAfterCallerSort() {
    var captor = ArgumentCaptor.forClass(Pageable.class);
    when(orderRepository.findGroupWideViewsInStores(any(), captor.capture()))
        .thenReturn(Page.empty());

    service.list(new StoreScope(Set.of(3L)), PageRequest.of(1, 10, Sort.by(Order::getCreatedAt)));

    assertThat(captor.getValue().getSort())
        .containsExactly(Sort.Order.asc(Order::getCreatedAt), Sort.Order.asc(Order::getId));
    assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
  }

  @Test
  void emptyStoreSetReturnsAnEmptyPageWithoutQuerying() {
    var result = service.list(new StoreScope(Set.of()), PageRequest.of(0, 10));

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
    verify(orderRepository, never()).findGroupWideViewsInStores(any(), any(Pageable.class));
  }
}
