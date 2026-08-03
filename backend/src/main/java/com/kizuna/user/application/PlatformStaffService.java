package com.kizuna.user.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserResumed;
import com.kizuna.user.domain.PlatformUserStopped;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.SelfStopNotAllowedException;
import com.kizuna.user.domain.StaleStaffUpdateException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** スタッフ（ロール × 担当店舗集合）管理ユースケース。対象は本人種別 STAFF のみで、CAST/MEMBER は専用フローが扱う人員のため一覧にも作成にも混ぜない。 */
@Service
@RequiredArgsConstructor
public class PlatformStaffService {

  private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_t_users_email";

  /** 担当店舗集合が実在店舗を指すことを保証する t_user_stores の FK。FK 違反は全域ハンドラで 4xx にならないため、ここで照合して変換する。 */
  private static final String STORE_FK_CONSTRAINT = "fk_t_user_stores_store";

  /** LIKE パターンのエスケープ規則。派生クエリが内部で使うものと同一で、手書きの cb.like にも同じ規則を適用する。 */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  private final PlatformUserRepository repository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public Page<PlatformStaffResponse> list(String search, Long storeId, Pageable pageable) {
    Page<PlatformUser> staff = repository.findAll(staffSpec(search, storeId), pageable);
    Set<Long> allRoleIds =
        staff.getContent().stream()
            .flatMap(user -> user.getRoleIds().stream())
            .collect(Collectors.toSet());
    Map<Long, String> roleNames = roleNamesOf(allRoleIds);
    return staff.map(user -> toResponse(user, roleNames));
  }

  /**
   * 一覧の対象は本人種別 STAFF に限る（CAST/MEMBER は専用フローが扱う）。検索語は表示名とメールアドレスを横断する部分一致。店舗 id
   * は「その店舗を担当範囲に含む」行への絞り込みで、ALL_STORES は個別 id を持たないまま全店舗を覆うため常に該当させる。
   *
   * <p>null の条件は述語を生成しない（JPQL の ":param is null or ..." パターンは PostgreSQL の null パラメータ型推論で 500 になるため
   * Specification で組み立てる）。
   */
  private static Specification<PlatformUser> staffSpec(String search, Long storeId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userType"), UserType.STAFF));
      if (search != null) {
        char escape = LIKE_ESCAPE.getEscapeCharacter();
        String pattern = "%" + LIKE_ESCAPE.escape(search.toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("displayName")), pattern, escape),
                cb.like(cb.lower(root.get("email")), pattern, escape)));
      }
      if (storeId != null) {
        // 担当店舗集合は @ElementCollection のため、member of は Hibernate が相関副問い合わせ（exists）へ展開する。
        // 親行を結合で増やさないので、ページングの件数・境界に影響しない。
        predicates.add(
            cb.or(
                cb.equal(root.get("storeScopeType"), StoreScopeType.ALL_STORES),
                cb.isMember(storeId, root.get("storeIds"))));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @Transactional
  public PlatformStaffResponse create(PlatformStaffCreateRequest req) {
    Map<Long, String> roleNames = requireRoles(req.getRoleIds());
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
            .roleIds(req.getRoleIds())
            .storeScopeType(req.getStoreScopeType())
            .storeIds(req.getStoreIds())
            .build();
    return toResponse(save(user), roleNames);
  }

  /** 1 件取得。編集中に競合（409）が起きたとき、一覧の現在ページに対象が居なくても最新の版を取り直せるようにするための経路。 */
  @Transactional(readOnly = true)
  public PlatformStaffResponse get(Long id) {
    PlatformUser user = requireStaff(id);
    return toResponse(user, roleNamesOf(user.getRoleIds()));
  }

  @Transactional
  public PlatformStaffResponse update(Long id, PlatformStaffUpdateRequest req, String actorEmail) {
    Map<Long, String> roleNames = requireRoles(req.getRoleIds());
    PlatformUser user = requireStaff(id);
    // 陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）
    // ため、応答で往復させた version を明示比対して 409 で拒否する。
    if (!user.getVersion().equals(req.getVersion())) {
      throw new StaleStaffUpdateException("他の管理者が更新しました。最新の内容を確認してください");
    }
    // 自分自身を停止すると自らのセッションも即時失効し、以後の操作ができなくなる（サポート経路がない自己ロックアウト）ため拒否する。
    if (Boolean.FALSE.equals(req.getEnabled()) && user.getEmail().equals(actorEmail)) {
      throw new SelfStopNotAllowedException("自分自身を停止することはできません");
    }
    user.reassignGrants(req.getRoleIds(), req.getStoreScopeType(), req.getStoreIds());
    // enabled の遷移（null=現状維持）。停止は行を残し、過去の実行主体の記録を保持する。
    if (Boolean.FALSE.equals(req.getEnabled()) && user.getEnabled()) {
      user.stop();
    }
    if (Boolean.TRUE.equals(req.getEnabled()) && !user.getEnabled()) {
      user.resume();
    }
    // 失効の即時反映は「本リクエストが停止/再開を明示的に要求したか」で判定する（現在状態との差分ではない）。
    // AFTER_COMMIT の Redis 書き込みが失敗して 500 になっても、最新 version を取り直して同じ停止要求を
    // 再送すれば失効が書き直されるようにするための冪等化（差分語義だと再送時には既に enabled=false の
    // ためイベントが発行されず、resume→stop 以外に復旧手段が無くなる）。version は楽観ロックで
    // commit 済みの更新ぶん進んでいるため、再送には GET の取り直しが要る点に注意。
    if (Boolean.FALSE.equals(req.getEnabled())) {
      eventPublisher.publishEvent(new PlatformUserStopped(user.getEmail()));
    }
    if (Boolean.TRUE.equals(req.getEnabled())) {
      eventPublisher.publishEvent(new PlatformUserResumed(user.getEmail()));
    }
    return toResponse(save(user), roleNames);
  }

  /**
   * スタッフ管理の対象行を取り出す。
   *
   * <p>本人種別がスタッフ以外（CAST/MEMBER）の行は、存在しても本 API の対象外として「見つからない」に倒す（list/create と同じ扱い）。 種別違いと不在を呼出側から
   * 区別できないようにするため、両者は同一の応答になる。
   */
  private PlatformUser requireStaff(Long id) {
    return repository
        .findById(id)
        .filter(user -> user.getUserType() == UserType.STAFF)
        .orElseThrow(() -> new NotFoundException("スタッフが見つかりません: " + id));
  }

  /** 指定 id のロールが全て実在することを検証し、id→名称の対応を返す（応答組立にも使う）。 */
  private Map<Long, String> requireRoles(Set<Long> roleIds) {
    Map<Long, String> names = roleNamesOf(roleIds);
    if (names.size() != roleIds.size()) {
      throw new ServiceException("指定されたロールが存在しません");
    }
    return names;
  }

  private Map<Long, String> roleNamesOf(Set<Long> roleIds) {
    return roleRepository.findAllById(roleIds).stream()
        .collect(Collectors.toMap(Role::getId, Role::getName));
  }

  /**
   * 保存時の整合性違反を制約名で分類する。email 一意制約違反（同一メール二重送信レース）は重複エラー、店舗 FK 違反（存在しない店舗 id）は店舗エラーへ変換する（いずれも
   * 400）。それ以外の整合性違反は実装欠陥であり、握りつぶさず全域ハンドラの分類に委ねる。
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
      if (cause != null && cause.contains(STORE_FK_CONSTRAINT)) {
        throw new InvalidStoreScopeException("指定された店舗が存在しません");
      }
      throw ex;
    }
  }

  private static PlatformStaffResponse toResponse(PlatformUser user, Map<Long, String> roleNames) {
    List<PlatformStaffResponse.RoleRef> roles =
        user.getRoleIds().stream()
            .map(id -> new PlatformStaffResponse.RoleRef(id, roleNames.get(id)))
            .sorted(
                Comparator.comparing(
                    PlatformStaffResponse.RoleRef::name,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    return new PlatformStaffResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getEnabled(),
        roles,
        user.getStoreScopeType(),
        new HashSet<>(user.getStoreIds()),
        user.getVersion());
  }
}
