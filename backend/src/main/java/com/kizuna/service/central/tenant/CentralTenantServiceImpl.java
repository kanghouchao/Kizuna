package com.kizuna.service.central.tenant;

import com.kizuna.config.listener.event.TenantCreatedEvent;
import com.kizuna.exception.ServiceException;
import com.kizuna.model.dto.central.tenant.PaginatedTenantVO;
import com.kizuna.model.dto.central.tenant.TenantCreateDTO;
import com.kizuna.model.dto.central.tenant.TenantStatusVO;
import com.kizuna.model.dto.central.tenant.TenantUpdateDTO;
import com.kizuna.model.dto.central.tenant.TenantVO;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.model.entity.tenant.TenantSiteConfig;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.TenantSiteConfigRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class CentralTenantServiceImpl implements CentralTenantService {

  private final TenantRepository tenantRepository;
  private final TenantSiteConfigRepository siteConfigRepository;
  private final ApplicationEventPublisher eventPublisher;

  // ドメイン → テナント情報のキャッシュ
  private final ConcurrentHashMap<String, TenantVO> domainCache = new ConcurrentHashMap<>();

  @Override
  @Transactional(readOnly = true)
  public PaginatedTenantVO<TenantVO> list(int page, int perPage, String search) {
    int p = Math.max(1, page);
    Pageable pageable = PageRequest.of(p - 1, perPage);
    var pageRes =
        tenantRepository.findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(
            search == null ? "" : search, search == null ? "" : search, pageable);

    List<TenantVO> dtos = pageRes.stream().map(this::toDto).collect(Collectors.toList());

    return new PaginatedTenantVO<>(
        dtos,
        p,
        (dtos.isEmpty() ? 0 : (p - 1) * perPage + 1),
        pageRes.getTotalPages(),
        perPage,
        (dtos.isEmpty() ? 0 : (p - 1) * perPage + dtos.size()),
        pageRes.getTotalElements(),
        "",
        "",
        pageRes.hasNext() ? String.valueOf(p + 1) : null,
        pageRes.hasPrevious() ? String.valueOf(p - 1) : null);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TenantVO> getById(String id) {
    return tenantRepository.findById(parseId(id)).map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TenantVO> getByDomain(String domain) {
    // キャッシュを先にチェック
    TenantVO cached = domainCache.get(domain);
    if (cached != null) {
      log.debug("テナントキャッシュヒット domain: {}", domain);
      return Optional.of(cached);
    }

    // キャッシュミス、データベースを検索
    log.debug("テナントキャッシュミス domain: {}, データベースを検索", domain);
    return tenantRepository
        .findByDomain(domain)
        .map(
            t -> {
              TenantVO dto = toDto(t);
              domainCache.put(domain, dto);
              return dto;
            });
  }

  @Override
  @Transactional
  public void create(TenantCreateDTO req) {
    Tenant t = new Tenant();
    t.setName(req.getName());
    t.setDomain(req.getDomain());
    t.setEmail(req.getEmail());
    Tenant saved = tenantRepository.save(t);

    // テナント作成時にデフォルトのサイト設定を保存
    TenantSiteConfig config =
        TenantSiteConfig.builder()
            .tenant(saved)
            .templateKey("default")
            .mvType("image")
            .snsLinks(new ArrayList<>())
            .partnerLinks(new ArrayList<>())
            .build();
    siteConfigRepository.save(config);

    eventPublisher.publishEvent(new TenantCreatedEvent(saved));
  }

  @Override
  @Transactional
  public void update(String id, TenantUpdateDTO req) {
    var tenant =
        tenantRepository
            .findById(parseId(id))
            .orElseThrow(() -> new ServiceException("tenant not found"));
    // キャッシュをクリア
    domainCache.remove(tenant.getDomain());
    tenant.setName(req.getName());
    tenantRepository.save(tenant);
  }

  @Override
  @Transactional
  public void delete(String id) {
    // 削除前にドメインを取得してキャッシュをクリア
    tenantRepository
        .findById(parseId(id))
        .ifPresent(tenant -> domainCache.remove(tenant.getDomain()));
    tenantRepository.deleteById(parseId(id));
  }

  @Override
  @Transactional(readOnly = true)
  public TenantStatusVO stats() {
    long total = tenantRepository.count();
    // TODO: active/inactive/pending はまだモデル化されていない
    return new TenantStatusVO(total, total, 0, 0);
  }

  private Long parseId(String id) {
    try {
      return Long.parseLong(id);
    } catch (NumberFormatException e) {
      throw new ServiceException("Invalid tenant ID format: " + id);
    }
  }

  private TenantVO toDto(Tenant t) {
    return new TenantVO(String.valueOf(t.getId()), t.getName(), t.getDomain(), t.getEmail());
  }
}
