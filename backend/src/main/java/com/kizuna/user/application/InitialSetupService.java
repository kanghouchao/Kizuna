package com.kizuna.user.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.api.dto.InitialSetupRequest;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.SystemRole;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 最初の管理者（グループ管理者 + ALL_STORES）を匿名で登録する初期設定。
 *
 * <p>要否は「本人種別 STAFF の行が 1 件も無い」で判定する。有効な ROLE_MANAGE 保持者の有無で判定すると、保持者が DB
 * の直接編集などでゼロになったときに匿名の登録口が再び開いてしまう。ユーザー行を削除する経路は無いため、一度完了すると未完了へは戻らない。
 */
@Service
@RequiredArgsConstructor
public class InitialSetupService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final BusinessAudit businessAudit;
  private final AppProperties appProperties;

  @Transactional(readOnly = true)
  public boolean required() {
    return !staffExists();
  }

  /** 行為者は登録されたアカウント自身として監査する（匿名の要求には認証主体が無い）。 */
  @Transactional
  public Long register(InitialSetupRequest request) {
    // 並行登録の排他は持たない（異なるメールアドレスの同時送信で根管理者が 2 人できうるのは既知の制約）。
    if (staffExists()) {
      throw new NotFoundException("初期設定は完了しています");
    }
    Role admin = roleRepository.findByName(SystemRole.HQ_ADMIN.getRoleName()).orElseThrow();
    User user =
        save(
            User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .enabled(true)
                .userType(UserType.STAFF)
                .roleIds(Set.of(admin.getId()))
                .storeScopeType(StoreScopeType.ALL_STORES)
                .storeIds(Set.of())
                .build());
    businessAudit.recordById(
        user.getId(),
        null,
        "STAFF_CREATED",
        "USER_GRANTS",
        user.getId().toString(),
        null,
        null,
        Map.of(),
        BusinessAudit.grants(user));
    return user.getId();
  }

  /** 起動時の案内文。入口は管理画面のホスト（app.domain）にあり、スキームは app.scheme に従う。 */
  @Transactional(readOnly = true)
  public Optional<String> startupNotice() {
    if (staffExists()) {
      return Optional.empty();
    }
    return Optional.of(
        "初期設定が未完了です。"
            + appProperties.getScheme()
            + "://"
            + appProperties.getDomain()
            + "/auth/initial-setup を開いて最初の管理者を登録してください");
  }

  private boolean staffExists() {
    return userRepository.existsByUserType(UserType.STAFF);
  }

  /** STAFF が居なくても CAST / MEMBER の行や並行登録とメールアドレスが重複しうる。従業員作成と同じ 400 に写像する。 */
  private User save(User user) {
    return IntegrityViolations.translateOnFailure(
        () -> userRepository.saveAndFlush(user),
        Map.of(
            DbConstraint.UQ_T_USERS_EMAIL,
            () -> new DuplicateStaffEmailException("このメールアドレスは既に登録されています")));
  }
}
