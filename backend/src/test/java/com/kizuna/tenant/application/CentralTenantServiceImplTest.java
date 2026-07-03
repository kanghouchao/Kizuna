package com.kizuna.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.tenant.api.dto.TenantCreateDTO;
import com.kizuna.tenant.api.dto.TenantStatusVO;
import com.kizuna.tenant.api.dto.TenantUpdateDTO;
import com.kizuna.tenant.api.dto.TenantVO;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.tenant.domain.event.TenantCreatedEvent;
import java.util.List;
import java.util.Optional;
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
  @Mock private StoreProfileRepository storeProfileRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private AppProperties appProperties;
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
    when(appProperties.getTenantCreatorCachePerfix()).thenReturn("prefix-");

    tenantService.create(req);
    verify(storeProfileRepository).save(any());
    verify(eventPublisher).publishEvent(any(TenantCreatedEvent.class));
  }

  @Test
  void list_handlesNullSearch() {
    Page<Tenant> page = new PageImpl<>(List.of());
    when(tenantRepository.findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(
            anyString(), anyString(), any()))
        .thenReturn(page);

    assertThat(tenantService.list(1, 10, null).data()).isEmpty();
  }

  @Test
  void getById_returnsTenantVO() {
    Tenant t = createTenant(1L, "Store1", "store1.com", "a@b.com");
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(t));

    Optional<TenantVO> result = tenantService.getById("1");

    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("Store1");
    assertThat(result.get().getDomain()).isEqualTo("store1.com");
  }

  @Test
  void getById_returnsEmptyWhenNotFound() {
    when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

    assertThat(tenantService.getById("99")).isEmpty();
  }

  @Test
  void getById_throwsOnInvalidId() {
    assertThatThrownBy(() -> tenantService.getById("abc"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("テナント ID の形式が不正です");
  }

  @Test
  void getByDomain_returnsTenantVO() {
    Tenant t = createTenant(2L, "Store2", "store2.com", "b@c.com");
    when(tenantRepository.findByDomain("store2.com")).thenReturn(Optional.of(t));

    Optional<TenantVO> result = tenantService.getByDomain("store2.com");

    assertThat(result).isPresent();
    assertThat(result.get().getDomain()).isEqualTo("store2.com");
  }

  @Test
  void getByDomain_returnsEmptyWhenNotFound() {
    when(tenantRepository.findByDomain("unknown.com")).thenReturn(Optional.empty());

    assertThat(tenantService.getByDomain("unknown.com")).isEmpty();
  }

  @Test
  void update_modifiesName() {
    Tenant t = createTenant(1L, "Old", "old.com", "o@o.com");
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(t));

    TenantUpdateDTO req = new TenantUpdateDTO();
    req.setName("New");

    tenantService.update("1", req);

    assertThat(t.getName()).isEqualTo("New");
    verify(tenantRepository).save(t);
  }

  @Test
  void update_throwsWhenNotFound() {
    when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

    TenantUpdateDTO req = new TenantUpdateDTO();
    req.setName("New");

    assertThatThrownBy(() -> tenantService.update("99", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("テナントが見つかりません");
  }

  @Test
  void delete_deletesById() {
    tenantService.delete("1");
    verify(tenantRepository).deleteById(1L);
  }

  @Test
  void delete_throwsOnInvalidId() {
    assertThatThrownBy(() -> tenantService.delete("invalid"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("テナント ID の形式が不正です");
  }

  @Test
  void stats_returnsStatusVO() {
    when(tenantRepository.count()).thenReturn(5L);

    TenantStatusVO result = tenantService.stats();

    assertThat(result.total()).isEqualTo(5L);
    assertThat(result.active()).isEqualTo(5L);
  }

  private Tenant createTenant(Long id, String name, String domain, String email) {
    Tenant t = new Tenant();
    t.setId(id);
    t.setName(name);
    t.setDomain(domain);
    t.setEmail(email);
    return t;
  }
}
