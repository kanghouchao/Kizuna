package com.kizuna.dashboard.application;

import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.store.dashboard.StoreDashboardReader;
import com.kizuna.store.dashboard.StoreStatusCounts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoresService {
  private final StoreDashboardReader stores;

  public StoreStatusCounts read(StoreScope scope) {
    return stores.statusCounts(scope);
  }
}
