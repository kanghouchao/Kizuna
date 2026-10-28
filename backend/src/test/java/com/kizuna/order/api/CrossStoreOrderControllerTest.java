package com.kizuna.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderMapperImpl;
import com.kizuna.order.application.AdminOrderService;
import com.kizuna.order.domain.AdminOrderView;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 横断一覧が要求ごとに解決した授権店舗集合を service へ渡すことと、offset ページングの並びの安定性の単体テスト。 */
@WebMvcTest(CrossStoreOrderController.class)
@Import({
  CrossStoreOrderControllerTest.MethodSecurityConfig.class,
  OrderMapperImpl.class,
  StoreScopeHolder.class
})
class CrossStoreOrderControllerTest {

  /** メソッドセキュリティ（@PreAuthorize）を有効化し、jwt() の認証主体をそのまま通す設定 */
  @TestConfiguration
  @EnableMethodSecurity
  @EnableWebSecurity
  static class MethodSecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AdminOrderService adminOrderService;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  private static MockHttpServletRequestBuilder list(String query) {
    return get("/orders/cross-store" + query)
        .with(
            jwt()
                .jwt(j -> j.subject("viewer"))
                .authorities(new SimpleGrantedAuthority("PERM_ORDER_SET_VIEW")));
  }

  @Test
  @DisplayName("GET /orders/cross-store?sort=status でも id が副キーとして補われること")
  void listAppendsIdTiebreakerWhenCallerOverridesSort() throws Exception {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(true, Set.of()));
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Page<AdminOrderView> empty = new PageImpl<>(List.of());
    when(adminOrderService.list(any(), pageableCaptor.capture())).thenReturn(empty);

    mockMvc.perform(list("?sort=status")).andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  @Test
  @DisplayName("SPECIFIC_STORES の授権集合がそのまま service へ渡ること")
  void listPassesTheResolvedStoreSet() throws Exception {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(false, Set.of(3L, 5L)));
    ArgumentCaptor<StoreScope> scopeCaptor = ArgumentCaptor.forClass(StoreScope.class);
    when(adminOrderService.list(scopeCaptor.capture(), any()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc.perform(list("")).andExpect(status().isOk());

    assertThat(scopeCaptor.getValue()).isEqualTo(new StoreScope(false, Set.of(3L, 5L)));
  }

  @Test
  @DisplayName("授権集合を解決できない要求は 403 で、service へ届かないこと")
  void unresolvedStoreSetIsForbidden() throws Exception {
    when(storeScopeResolver.resolve(any())).thenReturn(null);

    mockMvc.perform(list("")).andExpect(status().isForbidden());

    verifyNoInteractions(adminOrderService);
  }
}
