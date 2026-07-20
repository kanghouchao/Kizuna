package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.PlatformOrderCreateRequest;
import com.kizuna.order.api.dto.PlatformOrderResponse;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.PlatformOrderView;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PlatformOrderServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock OrderService orderService;
  @Mock OrderMapper orderMapper;

  // create は実物の StoreContext を注入し、OrderService.create 呼び出し時点の storeId を捕捉して検証する。
  private final StoreContext storeContext = new StoreContext();
  private PlatformOrderService service;

  @BeforeEach
  void setUp() {
    service = new PlatformOrderService(orderRepository, orderService, storeContext, orderMapper);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
    storeContext.clear();
  }

  private void authenticate(String scopeType, Object storeIds) {
    Claims claims = mock(Claims.class);
    lenient().when(claims.get("storeScopeType", String.class)).thenReturn(scopeType);
    lenient().when(claims.get("storeIds")).thenReturn(storeIds);
    Authentication auth = mock(Authentication.class);
    lenient().when(auth.getDetails()).thenReturn(claims);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private PlatformOrderCreateRequest requestForStore(long storeId) {
    PlatformOrderCreateRequest req = new PlatformOrderCreateRequest();
    req.setStoreId(storeId);
    return req;
  }

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

  @Test
  void createFailsClosedWhenScopeUnresolved() {
    // 認証コンテキスト未設定 → StoreScope 解決不能
    assertThatThrownBy(() -> service.create(requestForStore(1L)))
        .isInstanceOf(AccessDeniedException.class);

    verify(orderService, never()).create(any());
    assertThat(storeContext.getStoreId()).isNull();
  }

  @Test
  void createRejectsOutOfSetStore() {
    authenticate("SPECIFIC_STORES", List.of(1));

    assertThatThrownBy(() -> service.create(requestForStore(2L)))
        .isInstanceOf(AccessDeniedException.class);

    verify(orderService, never()).create(any());
    assertThat(storeContext.getStoreId()).isNull();
  }

  @Test
  void createSetsStoreContextForAuthorizedStoreAndClearsAfter() {
    authenticate("SPECIFIC_STORES", List.of(1));
    PlatformOrderCreateRequest req = requestForStore(1L);
    OrderResponse res = OrderResponse.builder().id("o1").build();

    AtomicReference<Long> storeAtCall = new AtomicReference<>();
    when(orderService.create(req))
        .thenAnswer(
            inv -> {
              storeAtCall.set(storeContext.getStoreId());
              return res;
            });

    OrderResponse result = service.create(req);

    assertThat(result).isSameAs(res);
    assertThat(storeAtCall.get())
        .as("OrderService.create 呼び出し時点で StoreContext が storeId")
        .isEqualTo(1L);
    assertThat(storeContext.getStoreId()).as("復帰後は finally で clear 済み").isNull();
  }

  @Test
  void createAllowsAnyStoreForAllStoresScope() {
    authenticate("ALL_STORES", null);
    PlatformOrderCreateRequest req = requestForStore(999L);
    OrderResponse res = OrderResponse.builder().id("o1").build();

    AtomicReference<Long> storeAtCall = new AtomicReference<>();
    when(orderService.create(req))
        .thenAnswer(
            inv -> {
              storeAtCall.set(storeContext.getStoreId());
              return res;
            });

    service.create(req);

    assertThat(storeAtCall.get()).isEqualTo(999L);
    assertThat(storeContext.getStoreId()).isNull();
  }

  @Test
  void createClearsStoreContextEvenWhenOrderServiceThrows() {
    authenticate("SPECIFIC_STORES", List.of(1));
    PlatformOrderCreateRequest req = requestForStore(1L);

    when(orderService.create(req)).thenThrow(new ServiceException("boom"));

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(ServiceException.class);
    assertThat(storeContext.getStoreId()).as("例外時も finally で clear される").isNull();
  }
}
