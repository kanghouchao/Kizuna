package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.security.Authorities;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionCodeTest {

  @Test
  @DisplayName("authority は Authorities.permission と同一の PERM_ 接頭辞表現を返す")
  void authorityUsesPermissionEncoding() {
    assertThat(PermissionCode.ORDER_MANAGE.authority()).isEqualTo("PERM_ORDER_MANAGE");
    assertThat(PermissionCode.STORE_MANAGE.authority())
        .isEqualTo(Authorities.permission(PermissionCode.STORE_MANAGE.name()));
    assertThat(Authorities.isPermission(PermissionCode.STORE_MANAGE.authority())).isTrue();
    assertThat(Authorities.isPermission("ROLE_CAST")).isFalse();
  }

  // 目録の増減は wire 契約（frontend の union・基線 seed）に波及する製品判断なので、字面の個数で止める。
  @Test
  @DisplayName("権限目録は 55 個")
  void catalogIsComplete() {
    assertThat(PermissionCode.values()).hasSize(55);
  }

  @Test
  @DisplayName("サービス ID 専用の TASK_EXECUTE は既定ロールへ授与しない")
  void serviceOnlyPermissionHasNoDefaultRole() {
    assertThat(PermissionCode.TASK_EXECUTE.getDefaultRoles()).isEmpty();
    assertThat(Arrays.stream(PermissionCode.values()).map(PermissionCode::getDefaultRoles))
        .doesNotContainNull();
  }
}
