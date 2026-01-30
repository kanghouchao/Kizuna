package com.kizuna.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.tenant.girl.GirlCreateRequest;
import com.kizuna.model.dto.tenant.girl.GirlResponse;
import com.kizuna.model.dto.tenant.girl.GirlUpdateRequest;
import com.kizuna.model.entity.tenant.Girl;
import com.kizuna.repository.tenant.GirlRepository;
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
class GirlServiceImplTest {

  @Mock private GirlRepository girlRepository;
  @InjectMocks private GirlServiceImpl girlService;

  @Test
  void list_returnsPage() {
    Girl g = new Girl();
    g.setName("Test");
    Page<Girl> page = new PageImpl<>(List.of(g));
    when(girlRepository.findByNameContainingIgnoreCase(eq("test"), any(PageRequest.class)))
        .thenReturn(page);

    Page<GirlResponse> result = girlService.list("test", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void get_returnsResponse() {
    Girl g = new Girl();
    g.setId("g1");
    when(girlRepository.findById("g1")).thenReturn(Optional.of(g));
    assertThat(girlService.get("g1").getId()).isEqualTo("g1");
  }

  @Test
  void create_savesAndReturns() {
    GirlCreateRequest req = new GirlCreateRequest();
    req.setName("G1");
    when(girlRepository.save(any()))
        .thenAnswer(
            i -> {
              Girl g = i.getArgument(0);
              g.setId("g_new");
              return g;
            });
    GirlResponse res = girlService.create(req);
    assertThat(res.getId()).isEqualTo("g_new");
  }

  @Test
  void update_modifiesFields() {
    Girl g = new Girl();
    when(girlRepository.findById("g1")).thenReturn(Optional.of(g));
    when(girlRepository.save(any())).thenReturn(g);

    GirlUpdateRequest req = new GirlUpdateRequest();
    req.setName("G_Updated");
    girlService.update("g1", req);
    assertThat(g.getName()).isEqualTo("G_Updated");
  }

  @Test
  void delete_removes() {
    when(girlRepository.existsById("g1")).thenReturn(true);
    girlService.delete("g1");
    verify(girlRepository).deleteById("g1");
  }
}
