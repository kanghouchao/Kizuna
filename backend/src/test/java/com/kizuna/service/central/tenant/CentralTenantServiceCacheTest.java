package com.kizuna.service.central.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.central.tenant.TenantUpdateDTO;
import com.kizuna.model.dto.central.tenant.TenantVO;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.TenantConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** CentralTenantServiceImpl のキャッシュ動作を検証する統合テスト。 */
@SpringBootTest(classes = CentralTenantServiceCacheTest.TestConfig.class)
class CentralTenantServiceCacheTest {

  @Configuration
  @EnableCaching
  @ImportAutoConfiguration(CacheAutoConfiguration.class)
  @Import(CentralTenantServiceImpl.class)
  static class TestConfig {

    @Bean
    ApplicationEventPublisher eventPublisher() {
      return event -> {};
    }
  }

  @MockitoBean private TenantRepository tenantRepository;
  @MockitoBean private TenantConfigRepository tenantConfigRepository;

  @Autowired private CentralTenantService tenantService;
  @Autowired private CacheManager cacheManager;

  private Tenant testTenant;

  @BeforeEach
  void setUp() {
    cacheManager.getCache("tenantByDomain").clear();

    testTenant = new Tenant();
    testTenant.setId(1L);
    testTenant.setName("Test Tenant");
    testTenant.setDomain("test.example.com");
  }

  @Test
  void getByDomain_cacheHit_doesNotCallRepository() {
    when(tenantRepository.findByDomain("test.example.com")).thenReturn(Optional.of(testTenant));

    // 1回目: キャッシュミス → リポジトリ呼び出し
    Optional<TenantVO> first = tenantService.getByDomain("test.example.com");
    assertThat(first).isPresent();

    // 2回目: キャッシュヒット → リポジトリ呼び出しなし
    Optional<TenantVO> second = tenantService.getByDomain("test.example.com");
    assertThat(second).isPresent();

    verify(tenantRepository, times(1)).findByDomain("test.example.com");
  }

  @Test
  void update_doesNotEvictCache() {
    when(tenantRepository.findByDomain("test.example.com")).thenReturn(Optional.of(testTenant));
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(testTenant));
    when(tenantRepository.save(testTenant)).thenReturn(testTenant);

    // キャッシュにロード
    tenantService.getByDomain("test.example.com");
    verify(tenantRepository, times(1)).findByDomain("test.example.com");

    // update は domain を変更しないためキャッシュに影響しない
    TenantUpdateDTO updateReq = new TenantUpdateDTO();
    updateReq.setName("Updated");
    tenantService.update("1", updateReq);

    // キャッシュが残っているためリポジトリは再呼び出しされない
    tenantService.getByDomain("test.example.com");
    verify(tenantRepository, times(1)).findByDomain("test.example.com");
  }

  @Test
  void delete_evictsCache() {
    when(tenantRepository.findByDomain("test.example.com")).thenReturn(Optional.of(testTenant));

    // キャッシュにロード
    tenantService.getByDomain("test.example.com");
    verify(tenantRepository, times(1)).findByDomain("test.example.com");

    // delete によりキャッシュがクリアされる
    tenantService.delete("1");

    // 再度呼び出すとキャッシュミス → リポジトリ再呼び出し
    tenantService.getByDomain("test.example.com");
    verify(tenantRepository, times(2)).findByDomain("test.example.com");
  }
}
