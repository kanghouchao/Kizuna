package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastStoreView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CastSelfServiceTest {

  @Mock private CastEnrollmentRepository castRepository;

  @InjectMocks private CastSelfService service;

  private CastStoreView storeView(long storeId, String storeName) {
    CastStoreView view = mock(CastStoreView.class);
    when(view.getStoreId()).thenReturn(storeId);
    when(view.getStoreName()).thenReturn(storeName);
    return view;
  }

  @Test
  void myStores_returnsRepositoryViewsInOrder() {
    CastStoreView storeA = storeView(1L, "店舗A");
    CastStoreView storeB = storeView(2L, "店舗B");
    when(castRepository.findStoresByUserId(42L)).thenReturn(List.of(storeA, storeB));

    var result = service.myStores(42L);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getStoreId()).isEqualTo(1L);
    assertThat(result.get(0).getStoreName()).isEqualTo("店舗A");
    assertThat(result.get(1).getStoreId()).isEqualTo(2L);
    assertThat(result.get(1).getStoreName()).isEqualTo("店舗B");
  }
}
