package com.kizuna.store.application;

import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.store.domain.AttendanceRecordCheck;
import com.kizuna.store.domain.CompletedOrderCheck;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StorePatch;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.store.domain.StoreStatus;
import com.kizuna.storeprofile.domain.StoreProfile;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.application.CreatedStoreGrant;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class StoreRegistryService {

  /**
   * ドメイン照会のキャッシュ名。値は {@link StoreView} の JDK 直列化で、永続化された Redis に残る旧型の写しは復元できず照会が 500
   * になる。値の型を変えるときは名前も変え、旧い写しは TTL で消えるに任せる。
   */
  static final String DOMAIN_CACHE = "storeViewByDomain";

  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  private final StoreRepository storeRepository;
  private final StoreProfileRepository storeProfileRepository;
  private final CompletedOrderCheck completedOrderCheck;
  private final PointLedgerService pointLedgerService;
  private final AttendanceRecordCheck attendanceRecordCheck;
  private final BusinessAudit businessAudit;
  private final CreatedStoreGrant createdStoreGrant;

  /** 削除を止める側の外部キーの写像。履歴と業務記録は誤登録の撤回にも従わせない。 */
  private static final Map<DbConstraint, Supplier<RuntimeException>> DELETION_VIOLATIONS =
      Map.of(
          DbConstraint.FK_T_APPLICANT_UPLOADS_STORE,
          () -> new ConflictException("非公開添付の記録が存在する店舗は削除できません"),
          DbConstraint.FK_T_APPLICANT_UPLOADS_APPLICANT_STORE,
          () -> new ConflictException("非公開添付の記録が存在する店舗は削除できません"),
          DbConstraint.FK_T_SERVICES_STORE,
          () -> new ServiceException("サービス設定が存在する店舗は削除できません"),
          DbConstraint.FK_T_SERVICE_REVISIONS_STORE,
          () -> new ServiceException("サービス変更履歴が存在する店舗は削除できません"),
          DbConstraint.FK_T_GUARANTEE_TERMS_STORE,
          () -> new ServiceException("保証条件の記録が存在する店舗は削除できません"),
          DbConstraint.FK_T_BONUS_AWARDS_STORE,
          () -> new ServiceException("ボーナスの記録が存在する店舗は削除できません"));

  /** 呼び出し者の店舗集合内の店舗だけを返す。検索語は名称とドメインを横断する部分一致。 */
  @Transactional(readOnly = true)
  public Page<StoreView> list(StoreScope scope, String search, Pageable pageable) {
    if (scope.storeIds().isEmpty()) {
      return Page.empty(pageable);
    }
    return storeRepository.findAll(withinScope(scope, search), pageable).map(StoreView::of);
  }

  @Transactional(readOnly = true)
  public StoreView getById(StoreScope scope, String id) {
    return storeRepository
        .findById(requireInScope(scope, id))
        .map(StoreView::of)
        .orElseThrow(() -> notFound(id));
  }

  @Transactional(readOnly = true)
  // Optional は unwrap されるため未登録ドメインは null。cache-null-values=false の Redis に
  // null を書くと IllegalArgumentException → 500 になるのでキャッシュ対象外にする
  @Cacheable(value = DOMAIN_CACHE, key = "#domain", unless = "#result == null")
  public Optional<StoreView> getByDomain(String domain) {
    log.debug("店舗をデータベースから検索 domain: {}", domain);
    return storeRepository.findByDomain(domain).map(StoreView::of);
  }

  /**
   * 新規登録する。
   *
   * <p>ドメインの重複は、制約違反を捕まえるのではなく事前に照会して判定する — 一意制約はここを擦り抜けた競合を受け止める 最後の一枚（409）であり、業務上の重複判定を委ねる先ではない。
   */
  @Transactional
  public Long create(StoreCreateCommand command) {
    if (storeRepository.findByDomain(command.domain()).isPresent()) {
      throw new ServiceException("このドメインは既に登録されています");
    }
    Store saved =
        saveShortNameUnique(new Store(command.name(), command.shortName(), command.domain()));
    storeProfileRepository.save(StoreProfile.createDefault(saved.getId()));
    // 監査が作成者を読むより先に呼ぶ。先に読まれていると、作成者の行ロックが版照合付きの昇格になる。
    createdStoreGrant.grantToCurrent(saved.getId());
    record(
        saved.getId(),
        "STORE_CREATED",
        Map.of(),
        Map.of(
            "exists", "true",
            "status", saved.getStatus().name(),
            "version", String.valueOf(saved.getVersion()),
            "domain", saved.getDomain(),
            "changed_fields", "name,short_name,domain"));
    return saved.getId();
  }

  // ドメイン照会のキャッシュのキーは domain。だが注釈の式から見えるのは引数の id と戻り値（void）だけで、
  // domain はメソッド本体のローカルにしか現れずキーとして書けない。delete と同じ全件失効に揃える。
  @Transactional
  @CacheEvict(value = DOMAIN_CACHE, allEntries = true)
  public void update(StoreScope scope, String id, StorePatch patch) {
    var store = storeRepository.findById(requireInScope(scope, id)).orElseThrow(() -> notFound(id));
    String versionBefore = String.valueOf(store.getVersion());
    boolean renamed = !Objects.equals(store.getName(), patch.name());
    boolean shortNameChanged = !Objects.equals(store.getShortName(), patch.shortName());
    store.apply(patch);
    saveShortNameUnique(store);
    List<String> changed = new ArrayList<>();
    if (renamed) {
      changed.add("name");
    }
    if (shortNameChanged) {
      changed.add("short_name");
    }
    if (!changed.isEmpty()) {
      record(
          store.getId(),
          "STORE_UPDATED",
          Map.of("version", versionBefore),
          Map.of(
              "version", String.valueOf(store.getVersion()),
              "changed_fields", String.join(",", changed)));
    }
  }

  /** 略称の重複は並行する登録でも起こりうるため、flush で制約違反をこの場に顕在化させて 409 へ写す。 */
  private Store saveShortNameUnique(Store store) {
    return IntegrityViolations.translateOnFailure(
        () -> storeRepository.saveAndFlush(store),
        Map.of(DbConstraint.UQ_T_STORES_SHORT_NAME, () -> new ConflictException("この略称は既に使われています")));
  }

  /**
   * 削除する。削除できるのは、まだ開店しておらず確定した記録も持たない店舗だけ。
   *
   * <p>関門は 3 つで順序に意味がある。稼働中はそれ自体が拒否の理由なので、記録を数える前に落とす。 記録の照会は跨モジュールの問い合わせであり、結論が変わらない場合に払う必要はない。
   *
   * <p>記録の側は、完了済みの受注とポイント台帳の帰属の両方を見る。台帳の仕訳は会員が持ち店舗が消えても行は残る（発生店舗が 外れるだけ）ため、DB の外部キーは削除を止めない —
   * 「その店舗で起きた記録が読めなくなる」ことを止めるのはここだけである。
   *
   * <p>当日実績は別の理由で止める。実績と訂正履歴は法定保存（労基法 109 条）の対象で、店舗削除の連鎖に巻き込んで 消してよい行ではない（ADR
   * 0014）。断りの文言を分けるのは、次の一手が「記録が読めなくなる」側と違うためである。
   */
  @Transactional
  @CacheEvict(value = DOMAIN_CACHE, allEntries = true)
  public void delete(StoreScope scope, String id) {
    Long storeId = requireInScope(scope, id);
    Store store = storeRepository.findByIdForUpdate(storeId).orElseThrow(() -> notFound(id));
    if (store.getStatus() == StoreStatus.ACTIVE) {
      throw new ServiceException("稼働中の店舗は削除できません");
    }
    if (completedOrderCheck.existsForStore(storeId)
        || pointLedgerService.hasEntriesForStore(storeId)) {
      throw new ServiceException("完了済みの受注またはポイント仕訳が存在する店舗は削除できません");
    }
    if (attendanceRecordCheck.existsForStore(storeId)) {
      throw new ServiceException("当日実績が記録されている店舗は削除できません");
    }
    // 外部キーによる削除拒否は flush で
    // この場に顕在化させて業務例外へ写す（commit まで遅らせると catch を擦り抜けて 500 になる）。
    var before =
        Map.of(
            "exists", "true",
            "status", store.getStatus().name(),
            "version", String.valueOf(store.getVersion()),
            "domain", store.getDomain());
    String cascade =
        storeRepository.countCascade(storeId).stream()
            .filter(c -> c.getRows() > 0)
            .map(c -> c.getKind() + "=" + c.getRows())
            .collect(Collectors.joining(","));
    try {
      storeRepository.deleteById(storeId);
      storeRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      throw IntegrityViolations.translate(ex, DELETION_VIOLATIONS);
    }
    record(storeId, "STORE_DELETED", before, Map.of("exists", "false", "cascade_deleted", cascade));
  }

  private void record(
      Long storeId, String action, Map<String, String> before, Map<String, String> after) {
    businessAudit.recordCurrent(storeId, action, "STORE", String.valueOf(storeId), before, after);
  }

  @Transactional(readOnly = true)
  public long count(StoreScope scope) {
    return scope.storeIds().isEmpty() ? 0 : storeRepository.count(withinScope(scope, null));
  }

  /** 集合外の店舗は存在しない扱い（404）にする。業務上の検証より先に判定し、集合外の店舗の状態（稼働中・略称の重複）を応答で漏らさない。 */
  private Long requireInScope(StoreScope scope, String id) {
    Long storeId = parseId(id);
    if (!scope.authorizes(storeId)) {
      throw notFound(id);
    }
    return storeId;
  }

  /** 空集合は呼び手が先に短絡するので、空の IN は組み立てない。 */
  private static Specification<Store> withinScope(StoreScope scope, String search) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(root.get("id").in(scope.storeIds()));
      if (search != null) {
        char escape = LIKE_ESCAPE.getEscapeCharacter();
        String pattern = "%" + LIKE_ESCAPE.escape(search.toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, escape),
                cb.like(cb.lower(root.get("domain")), pattern, escape)));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static NotFoundException notFound(String id) {
    return new NotFoundException("店舗が見つかりません: " + id);
  }

  private Long parseId(String id) {
    try {
      return Long.parseLong(id);
    } catch (NumberFormatException e) {
      throw new ServiceException("店舗 ID の形式が不正です: " + id);
    }
  }
}
