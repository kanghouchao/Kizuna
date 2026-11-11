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
import java.time.OffsetDateTime;
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

  private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+09:00");

  private Store store(long id, String name, OffsetDateTime createdAt) {
    Store t = new Store(name, name.substring(0, 1), name + ".test");
    t.setId(id);
    t.setCreatedAt(createdAt);
    return t;
  }

  @Test
  void storeSet_delegatesToFindAllById_sortedByCreatedAtThenIdDescending() {
    authenticate(Set.of(1L, 2L, 3L));
    // id の順と作成日の順を食い違わせ、作成日の新しい順（同時刻は id の降順）に並ぶことを確かめる。
    when(storeRepository.findAllById(Set.of(1L, 2L, 3L)))
        .thenReturn(
            List.of(
                store(1L, "A店", T0.plusDays(1)),
                store(2L, "B店", T0),
                store(3L, "C店", T0.plusDays(1))));

    List<StoreView> result = service.listAuthorizedStores();

    assertThat(result)
        .extracting(StoreView::id, StoreView::name, StoreView::shortName, StoreView::status)
        .containsExactly(
            tuple(3L, "C店", "C", StoreStatus.PREPARING),
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
