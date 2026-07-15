package com.kizuna.user.application;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
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

  private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_platform_users_email";

  private final PlatformUserRepository repository;
  private final PasswordEncoder passwordEncoder;

  @Transactional(readOnly = true)
  public List<PlatformStaffResponse> list() {
    return repository.findByRoleInOrderByDisplayNameAsc(STAFF_ROLES).stream()
        .map(PlatformStaffService::toResponse)
        .toList();
  }

  public PlatformStaffResponse create(PlatformStaffCreateRequest req) {
    requireStaffRole(req.getRole());
    if (repository.findByEmail(req.getEmail().toLowerCase(Locale.ROOT)).isPresent()) {
      throw new DuplicateStaffEmailException("このメールアドレスは既に登録されています");
    }
    PlatformUser user =
        PlatformUser.builder()
            .email(req.getEmail())
            .password(passwordEncoder.encode(req.getPassword()))
            .displayName(req.getDisplayName())
            .enabled(true)
            .role(req.getRole())
            .storeScopeType(req.getStoreScopeType())
            .storeIds(req.getStoreIds())
            .build();
    return toResponse(save(user));
  }

  public Optional<PlatformStaffResponse> update(Long id, PlatformStaffUpdateRequest req) {
    requireStaffRole(req.getRole());
    return repository
        .findById(id)
        // 対象の現在ロールがスタッフ集合外（CAST/MEMBER）なら不可視として空を返す（list/create と同じ扱い）。
        .filter(user -> STAFF_ROLES.contains(user.getRole()))
        .map(
            user -> {
              user.reassign(req.getRole(), req.getStoreScopeType(), req.getStoreIds());
              return toResponse(save(user));
            });
  }

  private static void requireStaffRole(PlatformRole role) {
    if (!STAFF_ROLES.contains(role)) {
      throw new ServiceException("スタッフ管理では HQ/店長/スタッフのロールのみ作成できます");
    }
  }

  /**
   * 保存時の整合性違反を原因別に分類する。email 一意制約違反（同一メール二重送信レース）は重複エラー、それ以外（存在しない店舗 id の FK 違反）は店舗エラーへ変換する（いずれも
   * 400）。
   */
  private PlatformUser save(PlatformUser user) {
    try {
      return repository.save(user);
    } catch (DataIntegrityViolationException ex) {
      String cause = ex.getMostSpecificCause().getMessage();
      if (cause != null && cause.contains(EMAIL_UNIQUE_CONSTRAINT)) {
        throw new DuplicateStaffEmailException("このメールアドレスは既に登録されています");
      }
      throw new InvalidStoreScopeException("指定された店舗が存在しません");
    }
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
