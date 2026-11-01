package com.kizuna.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PageSizeLimitParityTest {

  @Test
  @DisplayName("Pageable の取得件数の上限とカーソル一覧の上限が同じ値であること")
  void pageableAndCursorListsShareOneLimit() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .run(
            context ->
                assertThat(
                        context
                            .getEnvironment()
                            .getProperty("spring.data.web.pageable.max-page-size", Integer.class))
                    .isEqualTo(CursorPage.MAX_SIZE));
  }
}
