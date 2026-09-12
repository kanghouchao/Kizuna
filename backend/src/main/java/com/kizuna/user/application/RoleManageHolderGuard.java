package com.kizuna.user.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.domain.LastRoleManageHolderException;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** G5 の検査と業務変更が同じトランザクションで確定するよう、既存のトランザクションを必須とする。 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RoleManageHolderGuard {
  private static final String ROLE_MANAGE = PermissionCode.ROLE_MANAGE.name();

  private final PlatformUserRepository repository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;

  public void requireAfterGrantChange(PlatformUser target, Set<Long> nextRoleIds) {
    permissionRepository.lockIdByCode(ROLE_MANAGE);
    Set<Long> supplierIds = roleRepository.findIdsByPermissionCode(ROLE_MANAGE);
    if (target.getEnabled()
        && !Collections.disjoint(target.getRoleIds(), supplierIds)
        && Collections.disjoint(nextRoleIds, supplierIds)) {
      requireAnotherHolder(target, supplierIds);
    }
  }

  public PlatformUser loadForSuspension(Long targetId) {
    permissionRepository.lockIdByCode(ROLE_MANAGE);
    // 目録行を待つ前に実体を読み込まず、待機中に確定した対象の状態をここで取得する。
    PlatformUser target =
        repository
            .findByIdForUpdate(targetId)
            .orElseThrow(() -> new NotFoundException("アカウントが見つかりません: " + targetId));
    if (target.getEnabled()) {
      Set<Long> supplierIds = roleRepository.findIdsByPermissionCode(ROLE_MANAGE);
      if (!Collections.disjoint(target.getRoleIds(), supplierIds)) {
        requireAnotherHolder(target, supplierIds);
      }
    }
    return target;
  }

  public void requireAfterRolePermissionRemoval(Long roleId) {
    permissionRepository.lockIdByCode(ROLE_MANAGE);
    Set<Long> supplierIds = roleRepository.findIdsByPermissionCode(ROLE_MANAGE);
    if (!supplierIds.contains(roleId)) {
      return;
    }
    repository.lockEnabledRoleHolderIds(supplierIds);
    Set<Long> remaining = new HashSet<>(supplierIds);
    remaining.remove(roleId);
    if (!remaining.isEmpty() && !repository.findEnabledRoleHolderIds(remaining).isEmpty()) {
      return;
    }
    if (!repository.findEnabledRoleHolderIds(Set.of(roleId)).isEmpty()) {
      throw new LastRoleManageHolderException("最後の管理権限保持者が居なくなるため、このロールから管理権限を外すことはできません");
    }
  }

  private void requireAnotherHolder(PlatformUser target, Set<Long> supplierIds) {
    // ロック照会は待機中に変わった関連ロールを見ないため、別クエリの新しい顔ぶれで数える。
    repository.lockEnabledRoleHolderIds(supplierIds);
    List<Long> holders = repository.findEnabledRoleHolderIds(supplierIds);
    if (holders.size() == 1 && holders.contains(target.getId())) {
      throw new LastRoleManageHolderException("最後の管理権限保持者を停止・降格することはできません");
    }
  }
}
