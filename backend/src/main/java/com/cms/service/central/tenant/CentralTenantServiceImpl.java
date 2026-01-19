package com.cms.service.central.tenant;

import com.cms.config.listener.event.TenantCreatedEvent;
import com.cms.exception.ServiceException;
import com.cms.model.dto.central.tenant.PaginatedTenantVO;
import com.cms.model.dto.central.tenant.TenantCreateDTO;
import com.cms.model.dto.central.tenant.TenantStatusVO;
import com.cms.model.dto.central.tenant.TenantUpdateDTO;
import com.cms.model.dto.central.tenant.TenantVO;
import com.cms.model.entity.central.tenant.Tenant;
import com.cms.repository.central.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CentralTenantServiceImpl implements CentralTenantService {

  private final TenantRepository tenantRepository;
  private final ApplicationEventPublisher eventPublisher;

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
    return tenantRepository.findByDomain(domain).map(this::toDto);
  }

  @Override
  @Transactional
  public void create(TenantCreateDTO req) {
    Tenant t = new Tenant();
    t.setName(req.getName());
    t.setDomain(req.getDomain());
    t.setEmail(req.getEmail());
    Tenant saved = tenantRepository.save(t);
    eventPublisher.publishEvent(new TenantCreatedEvent(saved));
  }

  @Override
  @Transactional
  public void update(String id, TenantUpdateDTO req) {
    var tenant =
        tenantRepository
            .findById(parseId(id))
            .orElseThrow(() -> new ServiceException("tenant not found"));
    tenant.setName(req.getName());
    tenantRepository.save(tenant);
  }

  @Override
  @Transactional
  public void delete(String id) {
    tenantRepository.deleteById(parseId(id));
  }

  @Override
  @Transactional(readOnly = true)
  public TenantStatusVO stats() {
    long total = tenantRepository.count();
    // placeholder: active/inactive/pending not modelled yet
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
