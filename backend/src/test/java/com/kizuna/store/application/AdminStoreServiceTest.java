package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.store.domain.StoreStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AdminStoreServiceTest {

  @Mock StoreRepository storeRepository;

  private final StoreScopeHolder holder = new StoreScopeHolder();
  private AdminStoreService service;

  @BeforeEach
  void setUp() {
    service = new AdminStoreService(storeRepository, holder);
  }

  @AfterEach
  void clearContext() {
    holder.clear();
  }

  private void authenticate(Set<Long> storeIds) {
    StoreScope scope = new StoreScope(storeIds);
    holder.install(() -> scope);
  }

  private Store store(long id, String name) {
    Store t = new Store(name, name.substring(0, 1), name + ".test");
    t.setId(id);
    return t;
  }

  @Test
  void storeSet_delegatesToFindAllById_sortedByIdAscending() {
    authenticate(Set.of(1L, 2L));
    // 意図的に降順で返し、サービスが id 昇順へ整列することを確認する。
    when(storeRepository.findAllById(Set.of(1L, 2L)))
        .thenReturn(List.of(store(2L, "B店"), store(1L, "A店")));

    List<StoreView> result = service.listAuthorizedStores();

    assertThat(result)
        .extracting(StoreView::id, StoreView::name, StoreView::shortName, StoreView::status)
        .containsExactly(
            tuple(1L, "A店", "A", StoreStatus.PREPARING),
            tuple(2L, "B店", "B", StoreStatus.PREPARING));
    verify(storeRepository, never()).findAll();
  }

  @Test
  void scopeUnresolved_throwsAccessDenied() {
    // 保持器未設定 → StoreScope 解決不能（fail-closed）。
    assertThatThrownBy(() -> service.listAuthorizedStores())
        .isInstanceOf(AccessDeniedException.class);
  }
}
