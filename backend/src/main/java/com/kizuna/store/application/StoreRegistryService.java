package com.kizuna.store.application;

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
    String term = search == null ? "" : search;
    return storeRepository
        .findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(term, term, pageable)
        .map(this::toDto);
  }

  @Transactional(readOnly = true)
  public Optional<StoreVO> getById(String id) {
    return storeRepository.findById(parseId(id)).map(this::toDto);
  }

  @Transactional(readOnly = true)
  // Optional は unwrap されるため未登録ドメインは null。cache-null-values=false の Redis に
  // null を書くと IllegalArgumentException → 500 になるのでキャッシュ対象外にする
  @Cacheable(value = "storeByDomain", key = "#domain", unless = "#result == null")
  public Optional<StoreVO> getByDomain(String domain) {
    log.debug("店舗をデータベースから検索 domain: {}", domain);
    return storeRepository.findByDomain(domain).map(this::toDto);
  }

  @Transactional
  public void create(StoreCreateDTO req) {
    Store t = new Store();
    t.setName(req.getName());
    t.setDomain(req.getDomain());
    t.setEmail(req.getEmail());
    Store saved = storeRepository.save(t);
    storeProfileRepository.save(StoreProfile.createDefault(saved.getId()));
  }

  @Transactional
  public void update(String id, StoreUpdateDTO req) {
    var store =
        storeRepository.findById(parseId(id)).orElseThrow(() -> new ServiceException("店舗が見つかりません"));
    store.setName(req.getName());
    storeRepository.save(store);
  }

  @Transactional
  @CacheEvict(value = "storeByDomain", allEntries = true)
  public void delete(String id) {
    storeRepository.deleteById(parseId(id));
  }

  @Transactional(readOnly = true)
  public StoreStatusVO stats() {
    long total = storeRepository.count();
    // TODO: active/inactive/pending はまだモデル化されていない
    return new StoreStatusVO(total, total, 0, 0);
  }

  private Long parseId(String id) {
    try {
      return Long.parseLong(id);
    } catch (NumberFormatException e) {
      throw new ServiceException("店舗 ID の形式が不正です: " + id);
    }
  }

  private StoreVO toDto(Store t) {
    return new StoreVO(String.valueOf(t.getId()), t.getName(), t.getDomain(), t.getEmail());
  }
}
