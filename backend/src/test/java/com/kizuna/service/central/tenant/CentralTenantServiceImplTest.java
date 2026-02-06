package com.kizuna.service.central.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.central.tenant.TenantCreateDTO;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.TenantConfigRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class CentralTenantServiceImplTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private TenantConfigRepository tenantConfigRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @InjectMocks private CentralTenantServiceImpl tenantService;

  @Test
  void list_returnsPage() {
    Page<Tenant> page = new PageImpl<>(List.of(new Tenant()));
    when(tenantRepository.findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(
            anyString(), anyString(), any()))
        .thenReturn(page);
    assertThat(tenantService.list(1, 10, "test").data()).hasSize(1);
  }

  @Test
  void create_savesAndPublishes() {
    TenantCreateDTO req = new TenantCreateDTO();
    req.setName("T1");
    req.setDomain("d1.com");

    Tenant t = new Tenant();
    t.setId(1L);
    when(tenantRepository.save(any())).thenReturn(t);

    tenantService.create(req);
    verify(tenantConfigRepository).save(any());
    verify(eventPublisher).publishEvent(any());
  }
}
