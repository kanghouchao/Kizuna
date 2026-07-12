package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.PlatformOrderResponse;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.PlatformOrderView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PlatformOrderServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock OrderMapper orderMapper;

  @InjectMocks PlatformOrderService service;

  @Test
  void listMapsPlatformViewsToResponses() {
    PlatformOrderView view = mock(PlatformOrderView.class);
    PlatformOrderResponse res = PlatformOrderResponse.builder().id("o1").storeId(1L).build();
    Page<PlatformOrderView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 10), 1);

    when(orderRepository.findPlatformViews(any(Pageable.class))).thenReturn(page);
    when(orderMapper.toPlatformResponse(view)).thenReturn(res);

    Page<PlatformOrderResponse> result = service.list(PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getId()).isEqualTo("o1");
    assertThat(result.getContent().get(0).getStoreId()).isEqualTo(1L);
    verify(orderRepository).findPlatformViews(any(Pageable.class));
  }
}
