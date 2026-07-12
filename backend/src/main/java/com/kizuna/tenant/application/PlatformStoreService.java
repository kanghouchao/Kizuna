package com.kizuna.tenant.application;

import com.kizuna.shared.tenancy.StoreScope;
import com.kizuna.tenant.api.dto.PlatformStoreResponse;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台 principal（授権店舗集合）の店舗一覧ユースケース（#324 統一ログイン）。 StoreScope を解決できない場合は fail-closed に {@link
 * AccessDeniedException} を投げる。
 */
@Service
@RequiredArgsConstructor
public class PlatformStoreService {

  private final TenantRepository tenantRepository;

  /** 授権店舗を id 昇順で返す。ALL_STORES は全店、SPECIFIC_STORES は授権集合のみ。 */
  @Transactional(readOnly = true)
  public List<PlatformStoreResponse> listAuthorizedStores() {
    StoreScope scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    if (scope == null) {
      throw new AccessDeniedException("授権店舗集合を解決できません");
    }
    Iterable<Tenant> tenants =
        scope.allStores()
            ? tenantRepository.findAll()
            : tenantRepository.findAllById(scope.storeIds());
    return StreamSupport.stream(tenants.spliterator(), false)
        .sorted(Comparator.comparing(Tenant::getId))
        .map(tenant -> new PlatformStoreResponse(tenant.getId(), tenant.getName()))
        .toList();
  }
}
