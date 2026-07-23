package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.store.api.dto.PlatformStoreResponse;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class PlatformStoreServiceTest {

  @Mock StoreRepository storeRepository;

  private PlatformStoreService service;

  @BeforeEach
  void setUp() {
    service = new PlatformStoreService(storeRepository);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(String scopeType, Object storeIds) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("user@example.com")
            .claim("storeScopeType", scopeType);
    if (storeIds != null) {
      builder.claim("storeIds", storeIds);
    }
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(builder.build()));
  }

  private Store store(long id, String name) {
    Store t = new Store(name, name + ".test", null);
    t.setId(id);
    return t;
  }

  @Test
  void allStoresScope_delegatesToFindAll_sortedByIdAscending() {
    authenticate("ALL_STORES", null);
    // 意図的に降順で返し、サービスが id 昇順へ整列することを確認する。
    when(storeRepository.findAll()).thenReturn(List.of(store(2L, "B店"), store(1L, "A店")));

    List<PlatformStoreResponse> result = service.listAuthorizedStores();

    assertThat(result)
        .containsExactly(new PlatformStoreResponse(1L, "A店"), new PlatformStoreResponse(2L, "B店"));
    verify(storeRepository).findAll();
    verify(storeRepository, never()).findAllById(any());
  }

  @Test
  void specificStoresScope_delegatesToFindAllById() {
    authenticate("SPECIFIC_STORES", List.of(1L));
    when(storeRepository.findAllById(Set.of(1L))).thenReturn(List.of(store(1L, "A店")));

    List<PlatformStoreResponse> result = service.listAuthorizedStores();

    assertThat(result).containsExactly(new PlatformStoreResponse(1L, "A店"));
    verify(storeRepository).findAllById(Set.of(1L));
    verify(storeRepository, never()).findAll();
  }

  @Test
  void scopeUnresolved_throwsAccessDenied() {
    // 認証コンテキスト未設定 → StoreScope 解決不能（fail-closed）。
    assertThatThrownBy(() -> service.listAuthorizedStores())
        .isInstanceOf(AccessDeniedException.class);
  }
}
