package com.kizuna.store.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.api.dto.StoreCreateDTO;
import com.kizuna.store.api.dto.StoreStatusVO;
import com.kizuna.store.api.dto.StoreUpdateDTO;
import com.kizuna.store.api.dto.StoreVO;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
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

  private final StoreRepository storeRepository;
  private final StoreProfileRepository storeProfileRepository;

  @Transactional(readOnly = true)
  public Page<StoreVO> list(String search, Pageable pageable) {
    // 派生クエリの Containing は null を渡せないため、絞り込み無し（null）は空パターンで表す。
    String term = search == null ? "" : search;
    return storeRepository
        .findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(term, term, pageable)
        .map(this::toDto);
  }

  @Transactional(readOnly = true)
  public StoreVO getById(String id) {
    return storeRepository.findById(parseId(id)).map(this::toDto).orElseThrow(() -> notFound(id));
  }

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
  @Transactional
  public void create(StoreCreateDTO req) {
    if (storeRepository.findByDomain(req.getDomain()).isPresent()) {
      throw new ServiceException("このドメインは既に登録されています");
    }
    Store t = new Store();
    t.setName(req.getName());
    t.setDomain(req.getDomain());
    t.setEmail(req.getEmail());
    Store saved = storeRepository.save(t);
    storeProfileRepository.save(StoreProfile.createDefault(saved.getId()));
  }

  // storeByDomain のキーは domain。だが注釈の式から見えるのは引数の id と戻り値（void）だけで、
  // domain はメソッド本体のローカルにしか現れずキーとして書けない。delete と同じ全件失効に揃える。
  @Transactional
  @CacheEvict(value = "storeByDomain", allEntries = true)
  public void update(String id, StoreUpdateDTO req) {
    var store = storeRepository.findById(parseId(id)).orElseThrow(() -> notFound(id));
    store.setName(req.getName());
    store.setEmail(req.getEmail());
    storeRepository.save(store);
  }

  @Transactional
  @CacheEvict(value = "storeByDomain", allEntries = true)
  public void delete(String id) {
    storeRepository.deleteById(parseId(id));
  }

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
        String.valueOf(t.getId()), t.getName(), t.getDomain(), t.getEmail(), t.getCreatedAt());
  }
}
