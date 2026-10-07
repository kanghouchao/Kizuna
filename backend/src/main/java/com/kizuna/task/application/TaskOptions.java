package com.kizuna.task.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.task.execution.TaskHandler;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskOptions {
  private final TaskRegistry registry;
  private final StoreRepository stores;
  private final ServiceExecutionIdentityService identities;

  public record TaskType(String name, String scope, boolean manualAllowed) {}

  public record StoreOption(String id, String name) {}

  public List<TaskType> taskTypes(String email) {
    return registry.registered().stream()
        .map(
            handler ->
                new TaskType(
                    handler.name(),
                    handler.platformWide() ? "PLATFORM" : "STORE",
                    allowed(email, handler)))
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

  @Transactional(readOnly = true)
  public Page<StoreOption> stores(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new ServiceException("ページ指定が不正です");
    return stores
        .findAll(PageRequest.of(page, size, Sort.by("name", "id")))
        .map(store -> new StoreOption(store.getId().toString(), store.getName()));
  }

  public Page<ServiceExecutionIdentityService.Candidate> candidates(
      String taskName, Long storeId, int page, int size) {
    var handler = registry.requireName(taskName);
    if (handler.platformWide() != (storeId == null) || storeId != null && storeId <= 0)
      throw new ServiceException("処理の対象範囲と店舗を確認してください");
    if (storeId != null && !stores.existsById(storeId)) throw new NotFoundException("対象店舗が見つかりません");
    return identities.candidates(page, size, handler.permission(), storeId);
  }
}
