package com.kizuna.task.application;

import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.task.execution.TaskHandler;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskOptions {
  private final TaskRegistry registry;
  private final ServiceExecutionIdentityService identities;
  private final StoreRepository stores;

  public record TaskType(String name, boolean manualAllowed) {}

  public record UncoveredStore(Long id, String name) {}

  public List<TaskType> taskTypes(String email) {
    return registry.registered().stream()
        .map(handler -> new TaskType(handler.name(), allowed(email, handler)))
        .toList();
  }

  private boolean allowed(String email, TaskHandler handler) {
    try {
      handler
          .manualPermission()
          .ifPresent(permission -> identities.requireOperator(email, permission));
      return true;
    } catch (AccessDeniedException denied) {
      return false;
    }
  }

  /**
   * 有効なサービス ID のどの店舗集合にも入っていない店舗のうち、呼び出し側の店舗集合内のもの。定期処理は店舗級のデータを集合で絞るため、
   * ここに出た店舗は処理されない。件数はグループの店舗数で上限が決まる。
   */
  public List<UncoveredStore> uncoveredStores(StoreScope caller) {
    var covered = identities.coveredStores();
    if (covered.allStores()) return List.of();
    return stores.findAll(Sort.by("id")).stream()
        .filter(store -> caller.authorizes(store.getId()))
        .filter(store -> !covered.storeIds().contains(store.getId()))
        .map(store -> new UncoveredStore(store.getId(), store.getName()))
        .toList();
  }

  public Page<ServiceExecutionIdentityService.Candidate> candidates(
      String taskName, Pageable pageable) {
    return identities.candidates(pageable, registry.requireName(taskName).permission());
  }
}
