package com.kizuna.task.api.dto;

import com.kizuna.task.application.TaskOptions;

public record UncoveredStoreResponse(Long id, String name) {
  public static UncoveredStoreResponse of(TaskOptions.UncoveredStore store) {
    return new UncoveredStoreResponse(store.id(), store.name());
  }
}
