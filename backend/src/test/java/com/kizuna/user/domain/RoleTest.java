package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoleTest {

  @Test
  @DisplayName("名称と権限集合を持つロールを構築できる")
  void buildsRoleWithNameAndPermissions() {
    Role role = Role.builder().name("店長").permissionIds(Set.of(1L, 2L)).build();

    assertThat(role.getName()).isEqualTo("店長");
    assertThat(role.getPermissionIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(role.getSystemRole()).isFalse();
  }

  @Test
  @DisplayName("systemRole 未指定は自作ロール（false）として扱う")
  void systemRoleDefaultsToFalse() {
    Role role = Role.builder().name("受付").permissionIds(Set.of(1L)).build();

    assertThat(role.getSystemRole()).isFalse();
  }

  @Test
  @DisplayName("名称が空白だと不変条件違反で例外")
  void blankNameThrows() {
    assertThatThrownBy(() -> Role.builder().name("  ").permissionIds(Set.of(1L)).build())
        .isInstanceOf(InvalidRoleException.class);
  }

  @Test
  @DisplayName("権限集合が空だと不変条件違反で例外")
  void emptyPermissionsThrows() {
    assertThatThrownBy(() -> Role.builder().name("空のロール").permissionIds(Set.of()).build())
        .isInstanceOf(InvalidRoleException.class);
  }

  @Test
  @DisplayName("権限集合が null だと不変条件違反で例外")
  void nullPermissionsThrows() {
    assertThatThrownBy(() -> Role.builder().name("null のロール").build())
        .isInstanceOf(InvalidRoleException.class);
  }

  @Test
  @DisplayName("自作ロールは名称と権限集合を変更できる")
  void customRoleCanBeRenamedAndRepermissioned() {
    Role role = Role.builder().name("受付").permissionIds(Set.of(1L)).build();

    role.rename("受付リーダー");
    role.replacePermissions(Set.of(2L, 3L));

    assertThat(role.getName()).isEqualTo("受付リーダー");
    assertThat(role.getPermissionIds()).containsExactlyInAnyOrder(2L, 3L);
  }

  @Test
  @DisplayName("自作ロールの改名でも名称の不変条件は効く")
  void renameValidatesName() {
    Role role = Role.builder().name("受付").permissionIds(Set.of(1L)).build();

    assertThatThrownBy(() -> role.rename(" ")).isInstanceOf(InvalidRoleException.class);
  }

  @Test
  @DisplayName("自作ロールの権限置換でも空集合は拒否する")
  void replacePermissionsValidatesNonEmpty() {
    Role role = Role.builder().name("受付").permissionIds(Set.of(1L)).build();

    assertThatThrownBy(() -> role.replacePermissions(Set.of()))
        .isInstanceOf(InvalidRoleException.class);
  }

  @Test
  @DisplayName("平台既定ロールは改名できない")
  void systemRoleCannotBeRenamed() {
    Role role = Role.builder().name("HQ管理者").systemRole(true).permissionIds(Set.of(1L)).build();

    assertThatThrownBy(() -> role.rename("別名")).isInstanceOf(SystemRoleImmutableException.class);
    assertThat(role.getName()).isEqualTo("HQ管理者");
  }

  @Test
  @DisplayName("平台既定ロールは権限集合を置き換えられない")
  void systemRoleCannotReplacePermissions() {
    Role role = Role.builder().name("HQ管理者").systemRole(true).permissionIds(Set.of(1L)).build();

    assertThatThrownBy(() -> role.replacePermissions(Set.of(2L)))
        .isInstanceOf(SystemRoleImmutableException.class);
    assertThat(role.getPermissionIds()).containsExactly(1L);
  }
}
