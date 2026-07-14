package com.kizuna.user.application;

import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * スタッフ（ロール×店舗集合）管理ユースケース。作成対象は HQ_ADMIN/STORE_MANAGER/STORE_STAFF のみで、CAST/MEMBER
 * は別チケットの専用フローが扱う人員のため 一覧にも作成にも混ぜない（#325）。
 */
@Service
@RequiredArgsConstructor
public class PlatformStaffService {

  private static final Set<PlatformRole> STAFF_ROLES =
      EnumSet.of(PlatformRole.HQ_ADMIN, PlatformRole.STORE_MANAGER, PlatformRole.STORE_STAFF);

  private final PlatformUserRepository repository;

  @Transactional(readOnly = true)
  public List<PlatformStaffResponse> list() {
    return repository.findByRoleInOrderByDisplayNameAsc(STAFF_ROLES).stream()
        .map(PlatformStaffService::toResponse)
        .toList();
  }

  private static PlatformStaffResponse toResponse(PlatformUser user) {
    return new PlatformStaffResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getRole(),
        user.getStoreScopeType(),
        new HashSet<>(user.getStoreIds()));
  }
}
