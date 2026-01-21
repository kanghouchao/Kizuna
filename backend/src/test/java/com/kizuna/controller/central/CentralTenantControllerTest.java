package com.kizuna.controller.central;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.central.tenant.PaginatedTenantVO;
import com.kizuna.model.dto.central.tenant.TenantCreateDTO;
import com.kizuna.model.dto.central.tenant.TenantStatusVO;
import com.kizuna.model.dto.central.tenant.TenantUpdateDTO;
import com.kizuna.model.dto.central.tenant.TenantVO;
import com.kizuna.service.central.tenant.CentralTenantService;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class CentralTenantControllerTest {

  @Mock CentralTenantService tenantService;

  @InjectMocks CentralTenantController controller;

  @Test
  void list_returnsOk() {
    PaginatedTenantVO<TenantVO> page =
        new PaginatedTenantVO<>(Collections.emptyList(), 1, 0, 1, 10, 0, 0, "", "", null, null);
    when(tenantService.list(1, 10, null)).thenReturn(page);

    ResponseEntity<PaginatedTenantVO<TenantVO>> response = controller.list(1, 10, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(page);
  }

  @Test
  void getById_returnsOk_whenFound() {
    TenantVO vo = new TenantVO("1", "Name", "domain", "email");
    when(tenantService.getById("1")).thenReturn(Optional.of(vo));

    ResponseEntity<TenantVO> response = controller.getById("1");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(vo);
  }

  @Test
  void getById_returnsNotFound_whenMissing() {
    when(tenantService.getById("1")).thenReturn(Optional.empty());

    ResponseEntity<TenantVO> response = controller.getById("1");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void getByDomain_returnsOk_whenFound() {
    TenantVO vo = new TenantVO("1", "Name", "domain", "email");
    when(tenantService.getByDomain("domain")).thenReturn(Optional.of(vo));

    ResponseEntity<TenantVO> response = controller.getByDomain("domain");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(vo);
  }

  @Test
  void create_callsService() {
    TenantCreateDTO dto = new TenantCreateDTO();
    ResponseEntity<Void> response = controller.create(dto);

    verify(tenantService).create(dto);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void update_callsService() {
    TenantUpdateDTO dto = new TenantUpdateDTO();
    ResponseEntity<Void> response = controller.update("1", dto);

    verify(tenantService).update("1", dto);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void delete_callsService() {
    ResponseEntity<Void> response = controller.delete("1");

    verify(tenantService).delete("1");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void stats_returnsOk() {
    TenantStatusVO stats = new TenantStatusVO(10, 10, 0, 0);
    when(tenantService.stats()).thenReturn(stats);

    ResponseEntity<TenantStatusVO> response = controller.stats();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(stats);
  }
}
