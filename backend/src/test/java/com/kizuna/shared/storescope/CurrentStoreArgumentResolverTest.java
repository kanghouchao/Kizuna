package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;

class CurrentStoreArgumentResolverTest {

  private final StoreContext storeContext = new StoreContext();
  private final CurrentStoreArgumentResolver resolver =
      new CurrentStoreArgumentResolver(storeContext);

  /** 引数の形を切り替えて MethodParameter を組み立てるための土台。 */
  static class Handlers {
    void handle(
        @CurrentStore Long required,
        @CurrentStore(required = false) Long optional,
        Long unannotated,
        @CurrentStore String wrongType) {}
  }

  private static MethodParameter parameter(int index) throws NoSuchMethodException {
    Method method =
        Handlers.class.getDeclaredMethod(
            "handle", Long.class, Long.class, Long.class, String.class);
    return new MethodParameter(method, index);
  }

  @Test
  @DisplayName("店舗文脈があれば必須の引数にその店舗 id を渡すこと")
  void requiredResolvesEstablishedStore() throws Exception {
    storeContext.setStoreId(7L);
    try {
      assertThat(resolver.resolveArgument(parameter(0), null, null, null)).isEqualTo(7L);
    } finally {
      storeContext.clear();
    }
  }

  @Test
  @DisplayName("店舗文脈が無いと必須の引数は 403 で拒否すること")
  void requiredWithoutStoreIsDenied() {
    assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, null, null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("店舗文脈が解決できません");
  }

  @Test
  @DisplayName("店舗文脈が無くても任意の引数には null を渡すこと")
  void optionalWithoutStoreResolvesNull() throws Exception {
    assertThat(resolver.resolveArgument(parameter(1), null, null, null)).isNull();
  }

  @Test
  @DisplayName("@CurrentStore の付いた Long 引数だけを扱うこと")
  void supportsOnlyAnnotatedLongParameters() throws Exception {
    assertThat(resolver.supportsParameter(parameter(0))).isTrue();
    assertThat(resolver.supportsParameter(parameter(1))).isTrue();
    assertThat(resolver.supportsParameter(parameter(2))).isFalse();
    assertThat(resolver.supportsParameter(parameter(3))).isFalse();
  }
}
