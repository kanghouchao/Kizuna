package com.kizuna.dashboard.api.dto;

import com.kizuna.store.dashboard.StoreStatusCounts;

public record StoresResponse(long total, long preparing, long active) {
  public static StoresResponse from(StoreStatusCounts counts) {
    return new StoresResponse(counts.total(), counts.preparing(), counts.active());
  }
}
