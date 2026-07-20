package com.kizuna.shared.storescope;

import org.springframework.stereotype.Component;

@Component
public class StoreContext {
  private final ThreadLocal<Long> CURRENT_STORE = new ThreadLocal<>();

  public boolean hasStoreId() {
    Long storeId = CURRENT_STORE.get();
    return storeId != null;
  }

  public void setStoreId(Long storeId) {
    CURRENT_STORE.set(storeId);
  }

  public Long getStoreId() {
    return CURRENT_STORE.get();
  }

  public void clear() {
    CURRENT_STORE.remove();
  }
}
