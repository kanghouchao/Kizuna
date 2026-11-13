package com.kizuna.store.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kizuna.shared.storescope.StoreScope;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreDashboardReaderTest {

  @Test
  @DisplayName("空の店舗集合は問い合わせを組まずに 0 を返す（空の IN の描き方に頼らない）")
  void emptyScopeIsZeroWithoutQuerying() {
    EntityManager em = mock(EntityManager.class);

    StoreStatusCounts counts = new StoreDashboardReader(em).statusCounts(new StoreScope(Set.of()));

    assertThat(counts).isEqualTo(new StoreStatusCounts(0, 0, 0));
    verifyNoInteractions(em);
  }
}
