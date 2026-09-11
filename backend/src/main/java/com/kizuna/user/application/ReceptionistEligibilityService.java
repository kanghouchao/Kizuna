package com.kizuna.user.application;

import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.UserType;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NamedInterface("receptionist-eligibility")
@Transactional(readOnly = true)
public class ReceptionistEligibilityService {
  private final PlatformUserRepository platformUserRepository;
  private final RoleRepository roleRepository;

  public List<ReceptionistCandidate> listCandidates(Long storeId) {
    Set<Long> roleIds = roleRepository.findIdsByPermissionCode(PermissionCode.ORDER_MANAGE.name());
    // 他店舗・停止中・非 STAFF を DB で除外し、無関係な利用者のロール集合を読み込まない。
    return platformUserRepository
        .findAuthorizedByUserTypeOrderByDisplayNameAsc(UserType.STAFF, storeId)
        .stream()
        .filter(user -> isEligible(user, storeId, roleIds))
        .map(user -> new ReceptionistCandidate(user.getId(), user.getDisplayName()))
        .toList();
  }

  public Optional<Long> findEligibleIdByEmail(String email, Long storeId) {
    Set<Long> roleIds = roleRepository.findIdsByPermissionCode(PermissionCode.ORDER_MANAGE.name());
    return platformUserRepository
        .findByEmail(email)
        .filter(user -> isEligible(user, storeId, roleIds))
        .map(PlatformUser::getId);
  }

  public boolean isEligible(Long userId, Long storeId) {
    Set<Long> roleIds = roleRepository.findIdsByPermissionCode(PermissionCode.ORDER_MANAGE.name());
    return platformUserRepository
        .findById(userId)
        .filter(user -> isEligible(user, storeId, roleIds))
        .isPresent();
  }

  // 受付資格は現在のアカウントと保持ロールで判断する。緊急昇格や JWT の権限は資格を与えない。
  private boolean isEligible(PlatformUser user, Long storeId, Set<Long> roleIds) {
    return user.getUserType() == UserType.STAFF
        && user.getEnabled()
        && user.authorizes(storeId)
        && !Collections.disjoint(user.getRoleIds(), roleIds);
  }
}
