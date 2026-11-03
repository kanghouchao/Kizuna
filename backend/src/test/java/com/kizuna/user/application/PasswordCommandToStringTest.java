package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.domain.StoreScopeType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordCommandToStringTest {

  @Test
  @DisplayName("従業員作成の命令は文字列化しても生のパスワードを含まない")
  void staffCreateHidesPassword() {
    var command =
        new StaffCommand.Create(
            "new@kizuna.test",
            "s3cret-raw",
            "表示名",
            Set.of(1L),
            StoreScopeType.SPECIFIC_STORES,
            Set.of(2L));

    assertThat(command.toString()).doesNotContain("s3cret-raw").contains("new@kizuna.test");
  }

  @Test
  @DisplayName("初期設定の命令は文字列化しても生のパスワードを含まない")
  void initialSetupHidesPassword() {
    var command = new InitialSetupCommand("owner@kizuna.test", "s3cret-raw", "管理者");

    assertThat(command.toString()).doesNotContain("s3cret-raw").contains("owner@kizuna.test");
  }
}
