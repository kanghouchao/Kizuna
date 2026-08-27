package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleRepositoryTest {

  @Test
  @DisplayName("HQ 側ロールは Console.PLATFORM の権限だけから解決される")
  void hqSideRoleIsResolvedFromPlatformConsolePermissionsOnly() {
    // 「HQ 側ロール」の定義そのものを固定する。STORE_STAFF_MANAGE は店舗側へ移った権限（ADR 0020）、STORE_VIEW は SHARED で、
    // どちらもロールを HQ 側にはしない。目録が緩むと、店長のロールが管理者管理へ現れる。
    RoleRepository repository = mock(RoleRepository.class);
    when(repository.findHqRoleIds()).thenCallRealMethod();
    when(repository.findIdsByPermissionCodeIn(anyCollection())).thenReturn(Set.of(7L));

    assertThat(repository.findHqRoleIds()).containsExactly(7L);

    ArgumentCaptor<Collection<String>> codes = ArgumentCaptor.captor();
    verify(repository).findIdsByPermissionCodeIn(codes.capture());
    assertThat(codes.getValue())
        .contains(PermissionCode.ROLE_MANAGE.name(), PermissionCode.STORE_MANAGE.name())
        .doesNotContain(
            PermissionCode.STORE_STAFF_MANAGE.name(),
            PermissionCode.STORE_VIEW.name(),
            PermissionCode.ORDER_MANAGE.name());
  }

  @Test
  @DisplayName("店舗コンソールへ入れるロールは標識権限だけ・SHARED だけの権限からは解決されない")
  void storeConsoleRoleIsResolvedFromOperationalStorePermissionsOnly() {
    // 授与の検証はこの目録で行い、着地の判定（PlatformAuthService）と述語を共有する。
    // 目録が緩むと、付与はできるのにログイン後どこへも着地できないアカウントが作れてしまう。
    RoleRepository repository = mock(RoleRepository.class);
    when(repository.findStoreConsoleRoleIds()).thenCallRealMethod();
    when(repository.findIdsByPermissionCodeIn(anyCollection())).thenReturn(Set.of(8L));

    assertThat(repository.findStoreConsoleRoleIds()).containsExactly(8L);

    ArgumentCaptor<Collection<String>> codes = ArgumentCaptor.captor();
    verify(repository).findIdsByPermissionCodeIn(codes.capture());
    assertThat(codes.getValue())
        .contains(PermissionCode.ORDER_MANAGE.name(), PermissionCode.STORE_STAFF_MANAGE.name())
        .doesNotContain(
            PermissionCode.STORE_MENU_VIEW.name(),
            PermissionCode.STORE_VIEW.name(),
            PermissionCode.ROLE_MANAGE.name());
  }
}
