package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastResponse;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastPatch;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import java.util.List;
import java.util.Map;
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
class CastServiceTest {

  @Mock private CastRepository castRepository;
  @Mock private CastMapper castMapper;
  @Mock private TenantContext tenantContext;
  @Mock private TenantRepository tenantRepository;
  @Mock private CastInvitationService castInvitationService;

  @InjectMocks private CastService castService;

  @Test
  void list_returnsPage() {
    Cast g = Cast.builder().name("Test").build();
    g.setId("g1");
    Page<Cast> page = new PageImpl<>(List.of(g));

    CastResponse resp = new CastResponse();
    resp.setName("Test");

    when(castRepository.findByNameContainingIgnoreCase(eq("test"), any(PageRequest.class)))
        .thenReturn(page);
    when(castInvitationService.deriveStatuses(anyList()))
        .thenReturn(Map.of("g1", CastInvitationStatus.NOT_INVITED));
    when(castMapper.toResponse(g, CastInvitationStatus.NOT_INVITED)).thenReturn(resp);

    Page<CastResponse> result = castService.list("test", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test");
  }

  @Test
  void list_withoutSearch_returnsAll() {
    Cast g = Cast.builder().name("All").build();
    g.setId("g1");
    Page<Cast> page = new PageImpl<>(List.of(g));

    CastResponse resp = new CastResponse();
    resp.setName("All");

    when(castRepository.findAll(any(PageRequest.class))).thenReturn(page);
    when(castInvitationService.deriveStatuses(anyList()))
        .thenReturn(Map.of("g1", CastInvitationStatus.INVITED));
    when(castMapper.toResponse(g, CastInvitationStatus.INVITED)).thenReturn(resp);

    Page<CastResponse> result = castService.list(null, PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("All");
  }

  @Test
  void get_returnsResponse() {
    Cast g = new Cast();
    g.setId("g1");

    CastResponse resp = new CastResponse();
    resp.setId("g1");

    when(castRepository.findById("g1")).thenReturn(Optional.of(g));
    when(castInvitationService.deriveStatuses(anyList()))
        .thenReturn(Map.of("g1", CastInvitationStatus.LINKED));
    when(castMapper.toResponse(g, CastInvitationStatus.LINKED)).thenReturn(resp);

    assertThat(castService.get("g1").getId()).isEqualTo("g1");
  }

  @Test
  void get_throwsWhenNotFound() {
    when(castRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> castService.get("missing"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("キャストが見つかりません");
  }

  @Test
  void create_savesAndReturns() {
    CastCreateRequest req = new CastCreateRequest();
    req.setName("G1");

    Cast castEntity = Cast.builder().name("G1").build();

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
  void create_throwsWhenTenantNotFound() {
    CastCreateRequest req = new CastCreateRequest();
    req.setName("G1");

    when(castMapper.toEntity(req)).thenReturn(new Cast());
    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> castService.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("テナントが見つかりません");
  }

  @Test
  void update_modifiesFields() {
    Cast g = new Cast();
    g.setId("g1");

    when(castRepository.findById("g1")).thenReturn(Optional.of(g));
    when(castRepository.save(any())).thenReturn(g);

    CastUpdateRequest req = new CastUpdateRequest();
    req.setName("G_Updated");

    when(castMapper.toPatch(req))
        .thenReturn(
            new CastPatch("G_Updated", null, null, null, null, null, null, null, null, null));

    CastResponse resp = new CastResponse();
    resp.setName("G_Updated");
    when(castMapper.toResponse(g)).thenReturn(resp);

    CastResponse result = castService.update("g1", req);
    assertThat(result.getName()).isEqualTo("G_Updated");
    assertThat(g.getName()).isEqualTo("G_Updated");
  }

  @Test
  void update_throwsWhenNotFound() {
    when(castRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> castService.update("missing", new CastUpdateRequest()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("キャストが見つかりません");
  }

  @Test
  void delete_removes() {
    when(castRepository.existsById("g1")).thenReturn(true);
    castService.delete("g1");
    verify(castRepository).deleteById("g1");
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(castRepository.existsById("missing")).thenReturn(false);

    assertThatThrownBy(() -> castService.delete("missing"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("キャストが見つかりません");
  }

  @Test
  void listActive_returnsOnlyActiveCasts() {
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
