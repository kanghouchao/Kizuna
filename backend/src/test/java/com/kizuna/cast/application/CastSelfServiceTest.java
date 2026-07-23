package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.cast.domain.CastStoreView;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CastSelfServiceTest {

  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private CastRepository castRepository;

  @InjectMocks private CastSelfService service;

  private static final String EMAIL = "cast@kizuna.test";

  private PlatformUser userWithId(long id) {
    PlatformUser user = mock(PlatformUser.class);
    when(user.getId()).thenReturn(id);
    return user;
  }

  private CastStoreView storeView(long storeId, String storeName) {
    CastStoreView view = mock(CastStoreView.class);
    when(view.getStoreId()).thenReturn(storeId);
    when(view.getStoreName()).thenReturn(storeName);
    return view;
  }

  @Test
  void myStores_throwsWhenEmailUnknown() {
    when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.myStores(EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("ユーザーが見つかりません");

    verifyNoInteractions(castRepository);
  }

  @Test
  void myStores_mapsRepositoryViewsToResponses() {
    PlatformUser user = userWithId(42L);
    CastStoreView storeA = storeView(1L, "店舗A");
    CastStoreView storeB = storeView(2L, "店舗B");
    when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(castRepository.findStoresByPlatformUserId(42L)).thenReturn(List.of(storeA, storeB));

    var result = service.myStores(EMAIL);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).storeId()).isEqualTo(1L);
    assertThat(result.get(0).storeName()).isEqualTo("店舗A");
    assertThat(result.get(1).storeId()).isEqualTo(2L);
    assertThat(result.get(1).storeName()).isEqualTo("店舗B");
  }
}
