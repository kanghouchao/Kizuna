package com.kizuna.store.application;

import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.store.api.dto.StoreCreateDTO;
import com.kizuna.store.api.dto.StoreStatusVO;
import com.kizuna.store.api.dto.StoreUpdateDTO;
import com.kizuna.store.api.dto.StoreVO;
import com.kizuna.store.domain.CompletedOrderCheck;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.store.domain.StoreStatus;
import com.kizuna.storeprofile.domain.StoreProfile;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class StoreRegistryService {

  /** 店舗台帳（Store）は店舗スコープ表ではない。StoreProfile を触るのは {@code create} だけで、他の方法は素通しにならない。 */
  private static final String REGISTRY_ONLY = "店舗スコープ表（StoreProfile）を読まず、店舗台帳（Store）だけを扱う HQ 管理面";

  private final StoreRepository storeRepository;
  private final StoreProfileRepository storeProfileRepository;
  private final CompletedOrderCheck completedOrderCheck;
  private final PointLedgerService pointLedgerService;

  @StoreScopeExempt(reason = REGISTRY_ONLY)
  @Transactional(readOnly = true)
  public Page<StoreVO> list(String search, Pageable pageable) {
    // 派生クエリの Containing は null を渡せないため、絞り込み無し（null）は空パターンで表す。
    String term = search == null ? "" : search;
    return storeRepository
        .findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(term, term, pageable)
        .map(this::toDto);
  }

  @StoreScopeExempt(reason = REGISTRY_ONLY)
  @Transactional(readOnly = true)
  public StoreVO getById(String id) {
    return storeRepository.findById(parseId(id)).map(this::toDto).orElseThrow(() -> notFound(id));
  }

  @StoreScopeExempt(reason = REGISTRY_ONLY)
  @Transactional(readOnly = true)
  // Optional は unwrap されるため未登録ドメインは null。cache-null-values=false の Redis に
  // null を書くと IllegalArgumentException → 500 になるのでキャッシュ対象外にする
  @Cacheable(value = "storeByDomain", key = "#domain", unless = "#result == null")
  public Optional<StoreVO> getByDomain(String domain) {
    log.debug("店舗をデータベースから検索 domain: {}", domain);
    return storeRepository.findByDomain(domain).map(this::toDto);
  }

  /**
   * 新規登録する。
   *
   * <p>ドメインの重複は、制約違反を捕まえるのではなく事前に照会して判定する — 一意制約はここを擦り抜けた競合を受け止める 最後の一枚（409）であり、業務上の重複判定を委ねる先ではない。
   */
  @StoreScopeExempt(reason = "HQ の店舗登録で既定 StoreProfile を起こす書き込みで、store_id は今作った店舗の id を明示設定する")
  @Transactional
  public Long create(StoreCreateDTO req) {
    if (storeRepository.findByDomain(req.getDomain()).isPresent()) {
      throw new ServiceException("このドメインは既に登録されています");
    }
    Store t = new Store();
    t.setName(req.getName());
    t.setDomain(req.getDomain());
    t.setEmail(req.getEmail());
    Store saved = storeRepository.save(t);
    storeProfileRepository.save(StoreProfile.createDefault(saved.getId()));
    return saved.getId();
  }

  // storeByDomain のキーは domain。だが注釈の式から見えるのは引数の id と戻り値（void）だけで、
  // domain はメソッド本体のローカルにしか現れずキーとして書けない。delete と同じ全件失効に揃える。
  @StoreScopeExempt(reason = REGISTRY_ONLY)
  @Transactional
  @CacheEvict(value = "storeByDomain", allEntries = true)
  public void update(String id, StoreUpdateDTO req) {
    var store = storeRepository.findById(parseId(id)).orElseThrow(() -> notFound(id));
    store.setName(req.getName());
    store.setEmail(req.getEmail());
    storeRepository.save(store);
  }

  /**
   * 削除する。削除できるのは、まだ開店しておらず確定した記録も持たない店舗だけ。
   *
   * <p>関門は 2 つで順序に意味がある。稼働中はそれ自体が拒否の理由なので、記録を数える前に落とす。 記録の照会は跨モジュールの問い合わせであり、結論が変わらない場合に払う必要はない。
   *
   * <p>記録の側は、完了済みの受注とポイント台帳の帰属の両方を見る。台帳の仕訳は会員が持ち店舗が消えても行は残る（発生店舗が 外れるだけ）ため、DB の外部キーは削除を止めない —
   * 「その店舗で起きた記録が読めなくなる」ことを止めるのはここだけである。
   */
  @StoreScopeExempt(reason = REGISTRY_ONLY)
  @Transactional
  @CacheEvict(value = "storeByDomain", allEntries = true)
  public void delete(String id) {
    Long storeId = parseId(id);
    Store store = storeRepository.findById(storeId).orElseThrow(() -> notFound(id));
    if (store.getStatus() == StoreStatus.ACTIVE) {
      throw new ServiceException("稼働中の店舗は削除できません");
    }
    if (completedOrderCheck.existsForStore(storeId)
        || pointLedgerService.hasEntriesForStore(storeId)) {
      throw new ServiceException("完了済みの受注またはポイント仕訳が存在する店舗は削除できません");
    }
    storeRepository.deleteById(storeId);
  }

  @StoreScopeExempt(reason = REGISTRY_ONLY)
  @Transactional(readOnly = true)
  public StoreStatusVO stats() {
    return new StoreStatusVO(storeRepository.count());
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

  private StoreVO toDto(Store t) {
    return new StoreVO(
        String.valueOf(t.getId()),
        t.getName(),
        t.getDomain(),
        t.getEmail(),
        t.getStatus(),
        t.getCreatedAt());
  }
}
