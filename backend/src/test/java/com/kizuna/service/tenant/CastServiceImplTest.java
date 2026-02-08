package com.kizuna.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.mapper.tenant.CastMapper;
import com.kizuna.model.dto.tenant.cast.CastCreateRequest;
import com.kizuna.model.dto.tenant.cast.CastResponse;
import com.kizuna.model.dto.tenant.cast.CastUpdateRequest;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.model.entity.tenant.Cast;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.CastRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CastServiceImplTest {

  @Mock private CastRepository castRepository;
  @Mock private CastMapper castMapper;
  @Mock private TenantContext tenantContext;
  @Mock private TenantRepository tenantRepository;

  @InjectMocks private CastServiceImpl castService;

  @Test
  void list_returnsPage() {
    Cast g = new Cast();
    g.setName("Test");
    Page<Cast> page = new PageImpl<>(List.of(g));

    CastResponse resp = new CastResponse();
    resp.setName("Test");

    when(castRepository.findByNameContainingIgnoreCase(eq("test"), any(PageRequest.class)))
        .thenReturn(page);
    when(castMapper.toResponse(g)).thenReturn(resp);

    Page<CastResponse> result = castService.list("test", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test");
  }

  @Test
  void get_returnsResponse() {
    Cast g = new Cast();
    g.setId("g1");

    CastResponse resp = new CastResponse();
    resp.setId("g1");

    when(castRepository.findById("g1")).thenReturn(Optional.of(g));
    when(castMapper.toResponse(g)).thenReturn(resp);

    assertThat(castService.get("g1").getId()).isEqualTo("g1");
  }

  @Test
  void create_savesAndReturns() {
    CastCreateRequest req = new CastCreateRequest();
    req.setName("G1");

    Cast castEntity = new Cast();
    castEntity.setName("G1");

    Tenant tenant = new Tenant();
    tenant.setId(1L);

    when(castMapper.toEntity(req)).thenReturn(castEntity);
    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

    when(castRepository.save(any()))
        .thenAnswer(
            i -> {
              Cast g = i.getArgument(0);
              g.setId("g_new");
              return g;
            });

    CastResponse resp = new CastResponse();
    resp.setId("g_new");
    when(castMapper.toResponse(any())).thenReturn(resp);

    CastResponse res = castService.create(req);
    assertThat(res.getId()).isEqualTo("g_new");
  }

  @Test
  void update_modifiesFields() {
    Cast g = new Cast();
    g.setId("g1");

    when(castRepository.findById("g1")).thenReturn(Optional.of(g));
    when(castRepository.save(any())).thenReturn(g);

    CastUpdateRequest req = new CastUpdateRequest();
    req.setName("G_Updated");

    // Mock mapper doing nothing or simulating update
    // castMapper.updateEntityFromRequest is void, so we don't need to mock return
    // But we should verify it was called or assume it works as it is mocked

    CastResponse resp = new CastResponse();
    resp.setName("G_Updated");
    when(castMapper.toResponse(g)).thenReturn(resp);

    CastResponse result = castService.update("g1", req);
    assertThat(result.getName()).isEqualTo("G_Updated");
    verify(castMapper).updateEntityFromRequest(req, g);
  }

  @Test
  void delete_removes() {
    when(castRepository.existsById("g1")).thenReturn(true);
    castService.delete("g1");
    verify(castRepository).deleteById("g1");
  }

  @Test
  void listActive_ACTIVEステータスのみ返す() {
    Cast active1 = new Cast();
    active1.setId("g1");

    Cast active2 = new Cast();
    active2.setId("g2");

    when(castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE"))
        .thenReturn(List.of(active1, active2));

    CastResponse r1 = new CastResponse();
    r1.setId("g1");
    CastResponse r2 = new CastResponse();
    r2.setId("g2");

    when(castMapper.toResponse(active1)).thenReturn(r1);
    when(castMapper.toResponse(active2)).thenReturn(r2);

    List<CastResponse> result = castService.listActive();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo("g1");
    assertThat(result.get(1).getId()).isEqualTo("g2");
  }
}
