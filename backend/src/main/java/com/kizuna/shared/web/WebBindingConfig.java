package com.kizuna.shared.web;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 要求引数の束縛に横断的な既定を敷く設定。店舗文脈の interceptor 登録とは関心が別なので設定も分ける。 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class WebBindingConfig implements WebMvcConfigurer {

  @NonNull private final PageableHandlerMethodArgumentResolver pageableResolver;

  /**
   * Spring Data 自身の解決器より前に登録する。引数解決器は最初に支持した実装が値を作るため、先頭に置くことでしか 副キー補完を必ず通す保証は得られない（この順序は各一覧の
   * controller テストが守る）。
   */
  @Override
  public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new TiebreakerPageableArgumentResolver(pageableResolver));
  }
}
