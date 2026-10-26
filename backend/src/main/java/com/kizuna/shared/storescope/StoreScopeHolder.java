package com.kizuna.shared.storescope;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 要求単位の集合作用域（{@link StoreScope}）の保持器。ThreadLocal で、解決器（user モジュール側のインターセプタが差し込む {@link
 * Supplier}）を遅延評価し、最初の {@link #current()} で 1 回だけ解決して記憶する。
 *
 * <p>shared は user モジュールへ依存できないため、ここは「誰が・どう解決するか」を知らない。未設定・解決不能は null を返し、呼び出し側が fail-closed に拒否する。
 */
@Component
public class StoreScopeHolder {

  private final ThreadLocal<Resolution> current = new ThreadLocal<>();

  private static final class Resolution {
    private final Supplier<StoreScope> resolver;
    private boolean resolved;
    private StoreScope scope;

    private Resolution(Supplier<StoreScope> resolver) {
      this.resolver = resolver;
    }

    private StoreScope get() {
      if (!resolved) {
        scope = resolver.get();
        resolved = true;
      }
      return scope;
    }
  }

  /** 解決器を差し込む。解決は最初の {@link #current()} まで遅延し、差し替えると記憶も捨てる。 */
  public void install(Supplier<StoreScope> resolver) {
    current.set(new Resolution(resolver));
  }

  /** 現在の要求の集合作用域。未設定・解決不能なら null。 */
  public StoreScope current() {
    Resolution resolution = current.get();
    return resolution == null ? null : resolution.get();
  }

  public void clear() {
    current.remove();
  }
}
