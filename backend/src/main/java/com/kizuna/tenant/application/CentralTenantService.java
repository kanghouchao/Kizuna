package com.kizuna.tenant.application;

import com.kizuna.tenant.api.dto.PaginatedTenantVO;
import com.kizuna.tenant.api.dto.TenantCreateDTO;
import com.kizuna.tenant.api.dto.TenantStatusVO;
import com.kizuna.tenant.api.dto.TenantUpdateDTO;
import com.kizuna.tenant.api.dto.TenantVO;
import java.util.Optional;

public interface CentralTenantService {
  PaginatedTenantVO<TenantVO> list(int page, int perPage, String search);

  Optional<TenantVO> getById(String id);

  Optional<TenantVO> getByDomain(String domain);

  void create(TenantCreateDTO req);

  void update(String id, TenantUpdateDTO req);

  void delete(String id);

  TenantStatusVO stats();
}
