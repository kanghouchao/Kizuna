package com.kizuna.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.tenant.api.dto.PlatformStoreResponse;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PlatformStoreServiceTest {

  @Mock TenantRepository tenantRepository;

  private PlatformStoreService service;

  @BeforeEach
  void setUp() {
    service = new PlatformStoreService(tenantRepository);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(String scopeType, Object storeIds) {
    Claims claims = mock(Claims.class);
    lenient().when(claims.get("storeScopeType", String.class)).thenReturn(scopeType);
    lenient().when(claims.get("storeIds")).thenReturn(storeIds);
    Authentication auth = mock(Authentication.class);
    lenient().when(auth.getDetails()).thenReturn(claims);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private Tenant tenant(long id, String name) {
    Tenant t = new Tenant(name, name + ".test", null);
    t.setId(id);
    return t;
  }

  @Test
  void allStoresScope_delegatesToFindAll_sortedByIdAscending() {
    authenticate("ALL_STORES", null);
    // 意図的に降順で返し、サービスが id 昇順へ整列することを確認する。
    when(tenantRepository.findAll()).thenReturn(List.of(tenant(2L, "B店"), tenant(1L, "A店")));

    List<PlatformStoreResponse> result = service.listAuthorizedStores();

    assertThat(result)
        .containsExactly(new PlatformStoreResponse(1L, "A店"), new PlatformStoreResponse(2L, "B店"));
    verify(tenantRepository).findAll();
    verify(tenantRepository, never()).findAllById(any());
  }

  @Test
  void specificStoresScope_delegatesToFindAllById() {
    authenticate("SPECIFIC_STORES", List.of(1));
    when(tenantRepository.findAllById(Set.of(1L))).thenReturn(List.of(tenant(1L, "A店")));

    List<PlatformStoreResponse> result = service.listAuthorizedStores();

    assertThat(result).containsExactly(new PlatformStoreResponse(1L, "A店"));
    verify(tenantRepository).findAllById(Set.of(1L));
    verify(tenantRepository, never()).findAll();
  }

  @Test
  void scopeUnresolved_throwsAccessDenied() {
    // 認証コンテキスト未設定 → StoreScope 解決不能（fail-closed）。
    assertThatThrownBy(() -> service.listAuthorizedStores())
        .isInstanceOf(AccessDeniedException.class);
  }
}
