package com.kizuna.user.application;

import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NamedInterface("receptionist-eligibility")
@Transactional(readOnly = true)
public class ReceptionistEligibilityService {
  private final UserRepository userRepository;
  private final PlatformUserRepository platformUserRepository;
  private final RoleRepository roleRepository;

  public List<ReceptionistCandidate> listCandidates(Long storeId) {
    Set<Long> roleIds = roleRepository.findIdsByPermissionCode(PermissionCode.ORDER_MANAGE.name());
    // 他店舗・停止中・非 PLATFORM_USER を DB で除外し、無関係な利用者のロール集合を読み込まない。
    // 店舗の授権は HQL 側で判定済みだが、真の条件は呼び出し側の述語が持つ（UserRepository の契約）。
    List<User> users =
        userRepository.findAuthorizedByUserTypeOrderByDisplayNameAsc(
            UserType.PLATFORM_USER, storeId);
    Map<Long, PlatformUser> scopes =
        platformUserRepository.findByUserIdIn(users.stream().map(User::getId).toList()).stream()
            .collect(Collectors.toMap(PlatformUser::getUserId, scope -> scope));
    return users.stream()
        .filter(user -> isEligible(user, scopes.get(user.getId()), storeId, roleIds))
        .map(user -> new ReceptionistCandidate(user.getId(), user.getDisplayName()))
        .toList();
  }

  public Optional<Long> findEligibleIdByEmail(String email, Long storeId) {
    Set<Long> roleIds = roleRepository.findIdsByPermissionCode(PermissionCode.ORDER_MANAGE.name());
    return userRepository
        .findByEmail(email)
        .filter(user -> isEligible(user, scopeOf(user), storeId, roleIds))
        .map(User::getId);
  }

  public boolean isEligible(Long userId, Long storeId) {
    Set<Long> roleIds = roleRepository.findIdsByPermissionCode(PermissionCode.ORDER_MANAGE.name());
    return userRepository
        .findById(userId)
        .filter(user -> isEligible(user, scopeOf(user), storeId, roleIds))
        .isPresent();
  }

  private PlatformUser scopeOf(User user) {
    return platformUserRepository.findByUserId(user.getId()).orElse(null);
  }

  // 受付資格は現在のアカウント・保持ロール・店舗作用域で判断する。緊急昇格や JWT の権限は資格を与えない。
  private static boolean isEligible(
      User user, PlatformUser scope, Long storeId, Set<Long> roleIds) {
    return user.getUserType() == UserType.PLATFORM_USER
        && user.getEnabled()
        && !Collections.disjoint(user.getRoleIds(), roleIds)
        && scope != null
        && scope.authorizes(storeId);
  }
}
