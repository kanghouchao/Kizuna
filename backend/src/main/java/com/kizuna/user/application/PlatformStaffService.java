package com.kizuna.user.application;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.CapabilityBundleResponse;
import com.kizuna.user.api.dto.GrantHistoryEntryResponse;
import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.domain.CapabilityBundle;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.GrantAction;
import com.kizuna.user.domain.GrantHistory;
import com.kizuna.user.domain.GrantHistoryRepository;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserResumed;
import com.kizuna.user.domain.PlatformUserStopped;
import com.kizuna.user.domain.SelfStopNotAllowedException;
import com.kizuna.user.domain.StaleStaffUpdateException;
import com.kizuna.user.domain.UserType;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * スタッフ（能力束×店舗集合×精算範囲）管理ユースケース。対象は本人種別 STAFF のみで、CAST/MEMBER は別チケットの専用フローが扱う人員のため 一覧にも作成にも混ぜない（#325
 * / #398）。
 *
 * <p>付与・変更・停止・再開は追記専用の付与履歴（{@link GrantHistory}）へ実行主体つきで記録する（#382 — 停止後の実行主体記録の保持）。
 */
@Service
@RequiredArgsConstructor
public class PlatformStaffService {

  private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_t_users_email";

  private final PlatformUserRepository repository;
  private final CapabilityBundleRepository capabilityBundleRepository;
  private final GrantHistoryRepository grantHistoryRepository;
  private final PasswordEncoder passwordEncoder;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public List<PlatformStaffResponse> list() {
    List<PlatformUser> staff = repository.findByUserTypeOrderByDisplayNameAsc(UserType.STAFF);
    Set<Long> allBundleIds =
        staff.stream().flatMap(user -> user.getBundleIds().stream()).collect(Collectors.toSet());
    Map<Long, String> bundleNames = bundleNamesOf(allBundleIds);
    return staff.stream().map(user -> toResponse(user, bundleNames)).toList();
  }

  /** 能力束の一覧（授与 UI の選択肢）。名称昇順。 */
  @Transactional(readOnly = true)
  public List<CapabilityBundleResponse> listBundles() {
    return capabilityBundleRepository.findAll(Sort.by("name")).stream()
        .map(
            bundle ->
                new CapabilityBundleResponse(
                    bundle.getId(),
                    bundle.getName(),
                    bundle.getCapabilities().stream().map(Enum::name).sorted().toList()))
        .toList();
  }

  /** 付与履歴（新しい順）。対象が不在または STAFF 以外なら空（一覧・編集と同じ不可視扱い）。 */
  @Transactional(readOnly = true)
  public Optional<List<GrantHistoryEntryResponse>> grantHistory(Long id) {
    return repository
        .findById(id)
        .filter(user -> user.getUserType() == UserType.STAFF)
        .map(
            user ->
                grantHistoryRepository
                    .findByPlatformUserIdOrderByCreatedAtDesc(user.getId())
                    .stream()
                    .map(
                        entry ->
                            new GrantHistoryEntryResponse(
                                entry.getId(),
                                entry.getActorEmail(),
                                entry.getAction(),
                                entry.getDetail(),
                                entry.getCreatedAt()))
                    .toList());
  }

  @Transactional
  public PlatformStaffResponse create(PlatformStaffCreateRequest req, String actorEmail) {
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
    PlatformUser saved = save(user);
    recordHistory(saved, GrantAction.GRANT, actorEmail, bundleNames);
    return toResponse(saved, bundleNames);
  }

  @Transactional
  public Optional<PlatformStaffResponse> update(
      Long id, PlatformStaffUpdateRequest req, String actorEmail) {
    Map<Long, String> bundleNames = requireBundles(req.getBundleIds());
    return repository
        .findById(id)
        // 対象の本人種別がスタッフ以外（CAST/MEMBER）なら不可視として空を返す（list/create と同じ扱い）。
        .filter(user -> user.getUserType() == UserType.STAFF)
        .map(
            user -> {
              // 陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）
              // ため、応答で往復させた version を明示比対して 409 で拒否する（#400）。
              if (!user.getVersion().equals(req.getVersion())) {
                throw new StaleStaffUpdateException("他の管理者が更新しました。最新の内容を確認してください");
              }
              // 自分自身を停止すると自らのセッションも即時失効し、以後の操作ができなくなる（サポート経路がない自己ロックアウト）ため拒否する。
              if (Boolean.FALSE.equals(req.getEnabled()) && user.getEmail().equals(actorEmail)) {
                throw new SelfStopNotAllowedException("自分自身を停止することはできません");
              }
              user.reassignGrants(
                  req.getBundleIds(),
                  req.getStoreScopeType(),
                  req.getStoreIds(),
                  req.getSettlementScopeType(),
                  req.getSettlementStoreIds());
              // enabled の遷移（null=現状維持）。停止は行を残し、履歴へ実行主体つきで記録する。
              boolean stopped = Boolean.FALSE.equals(req.getEnabled()) && user.getEnabled();
              boolean resumed = Boolean.TRUE.equals(req.getEnabled()) && !user.getEnabled();
              if (stopped) {
                user.stop();
              }
              if (resumed) {
                user.resume();
              }
              // 失効の即時反映は「本リクエストが停止/再開を明示的に要求したか」で判定する（現在状態との差分ではない）。
              // AFTER_COMMIT の Redis 書き込みが失敗して 500 になっても、最新 version を取り直して同じ停止要求を
              // 再送すれば失効が書き直されるようにするための冪等化（差分語義だと再送時には既に enabled=false の
              // ためイベントが発行されず、resume→stop 以外に復旧手段が無くなる）。version は #400 の楽観ロックで
              // commit 済みの更新ぶん進んでいるため、再送には GET の取り直しが要る点に注意。
              if (Boolean.FALSE.equals(req.getEnabled())) {
                eventPublisher.publishEvent(new PlatformUserStopped(user.getEmail()));
              }
              if (Boolean.TRUE.equals(req.getEnabled())) {
                eventPublisher.publishEvent(new PlatformUserResumed(user.getEmail()));
              }
              PlatformUser saved = save(user);
              recordHistory(saved, GrantAction.CHANGE, actorEmail, bundleNames);
              if (stopped) {
                recordHistory(saved, GrantAction.STOP, actorEmail, bundleNames);
              }
              if (resumed) {
                recordHistory(saved, GrantAction.RESUME, actorEmail, bundleNames);
              }
              return toResponse(saved, bundleNames);
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

  /** 授権内容の快照を JSON で残す（束名・店舗集合・精算範囲・enabled — 検索目的ではなく監査目的の追記専用）。 */
  private void recordHistory(
      PlatformUser user, GrantAction action, String actorEmail, Map<Long, String> bundleNames) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("bundles", user.getBundleIds().stream().map(bundleNames::get).sorted().toList());
    snapshot.put("store_scope_type", user.getStoreScopeType().name());
    snapshot.put("store_ids", user.getStoreIds().stream().sorted().toList());
    snapshot.put(
        "settlement_scope_type",
        user.getSettlementScopeType() == null ? null : user.getSettlementScopeType().name());
    snapshot.put("settlement_store_ids", user.getSettlementStoreIds().stream().sorted().toList());
    snapshot.put("enabled", user.getEnabled());
    grantHistoryRepository.save(
        GrantHistory.builder()
            .platformUserId(user.getId())
            .actorEmail(actorEmail)
            .action(action)
            .detail(toJson(snapshot))
            .build());
  }

  private String toJson(Map<String, Object> snapshot) {
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JacksonException e) {
      // 文字列・数値・真偽のみの Map で直列化が失敗することは無い（失敗はプログラミングエラー）。
      throw new IllegalStateException("付与履歴快照の直列化に失敗しました", e);
    }
  }

  /**
   * 保存時の整合性違反を原因別に分類する。email 一意制約違反（同一メール二重送信レース）は重複エラー、それ以外（存在しない店舗 id の FK 違反）は店舗エラーへ変換する（いずれも
   * 400。束は事前検証済みのため、残る FK 違反経路は店舗系のみ）。
   *
   * <p>店舗集合等の @ElementCollection 行はトランザクション commit 時に flush されるため、{@code save} だけでは FK 違反が この try
   * を突き抜けて 500 になる。{@code saveAndFlush} で違反をここで顕在化させ 400 へ変換する。
   */
  private PlatformUser save(PlatformUser user) {
    try {
      return repository.saveAndFlush(user);
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
        new HashSet<>(user.getSettlementStoreIds()),
        user.getVersion());
  }
}
