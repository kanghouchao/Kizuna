package com.kizuna.user.application;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.domain.CapabilityBundle;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * スタッフ（能力束×店舗集合×精算範囲）管理ユースケース。対象は本人種別 STAFF のみで、CAST/MEMBER は別チケットの専用フローが扱う人員のため 一覧にも作成にも混ぜない（#325
 * / #398）。
 */
@Service
@RequiredArgsConstructor
public class PlatformStaffService {

  private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_platform_users_email";

  private final PlatformUserRepository repository;
  private final CapabilityBundleRepository capabilityBundleRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional(readOnly = true)
  public List<PlatformStaffResponse> list() {
    List<PlatformUser> staff = repository.findByUserTypeOrderByDisplayNameAsc(UserType.STAFF);
    Set<Long> allBundleIds =
        staff.stream().flatMap(user -> user.getBundleIds().stream()).collect(Collectors.toSet());
    Map<Long, String> bundleNames = bundleNamesOf(allBundleIds);
    return staff.stream().map(user -> toResponse(user, bundleNames)).toList();
  }

  public PlatformStaffResponse create(PlatformStaffCreateRequest req) {
    Map<Long, String> bundleNames = requireBundles(req.getBundleIds());
    if (repository.findByEmail(req.getEmail().toLowerCase(Locale.ROOT)).isPresent()) {
      throw new DuplicateStaffEmailException("このメールアドレスは既に登録されています");
    }
    PlatformUser user =
        PlatformUser.builder()
            .email(req.getEmail())
            .password(passwordEncoder.encode(req.getPassword()))
            .displayName(req.getDisplayName())
            .enabled(true)
            .userType(UserType.STAFF)
            .bundleIds(req.getBundleIds())
            .storeScopeType(req.getStoreScopeType())
            .storeIds(req.getStoreIds())
            .settlementScopeType(req.getSettlementScopeType())
            .settlementStoreIds(req.getSettlementStoreIds())
            .build();
    return toResponse(save(user), bundleNames);
  }

  public Optional<PlatformStaffResponse> update(Long id, PlatformStaffUpdateRequest req) {
    Map<Long, String> bundleNames = requireBundles(req.getBundleIds());
    return repository
        .findById(id)
        // 対象の本人種別がスタッフ以外（CAST/MEMBER）なら不可視として空を返す（list/create と同じ扱い）。
        .filter(user -> user.getUserType() == UserType.STAFF)
        .map(
            user -> {
              user.reassignGrants(
                  req.getBundleIds(),
                  req.getStoreScopeType(),
                  req.getStoreIds(),
                  req.getSettlementScopeType(),
                  req.getSettlementStoreIds());
              return toResponse(save(user), bundleNames);
            });
  }

  /** 指定 id の束が全て実在することを検証し、id→名称の対応を返す（応答組立にも使う）。 */
  private Map<Long, String> requireBundles(Set<Long> bundleIds) {
    Map<Long, String> names = bundleNamesOf(bundleIds);
    if (names.size() != bundleIds.size()) {
      throw new ServiceException("指定された能力束が存在しません");
    }
    return names;
  }

  private Map<Long, String> bundleNamesOf(Set<Long> bundleIds) {
    return capabilityBundleRepository.findAllById(bundleIds).stream()
        .collect(Collectors.toMap(CapabilityBundle::getId, CapabilityBundle::getName));
  }

  /**
   * 保存時の整合性違反を原因別に分類する。email 一意制約違反（同一メール二重送信レース）は重複エラー、それ以外（存在しない店舗 id の FK 違反）は店舗エラーへ変換する（いずれも
   * 400。束は事前検証済みのため、残る FK 違反経路は店舗系のみ）。
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

  private static PlatformStaffResponse toResponse(
      PlatformUser user, Map<Long, String> bundleNames) {
    List<PlatformStaffResponse.BundleRef> bundles =
        user.getBundleIds().stream()
            .map(id -> new PlatformStaffResponse.BundleRef(id, bundleNames.get(id)))
            .sorted(
                Comparator.comparing(
                    PlatformStaffResponse.BundleRef::name,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    return new PlatformStaffResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getEnabled(),
        bundles,
        user.getStoreScopeType(),
        new HashSet<>(user.getStoreIds()),
        user.getSettlementScopeType(),
        new HashSet<>(user.getSettlementStoreIds()));
  }
}
