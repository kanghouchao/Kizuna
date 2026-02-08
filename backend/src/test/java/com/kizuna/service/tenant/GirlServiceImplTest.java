package com.kizuna.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.tenant.cast.CastCreateRequest;
import com.kizuna.model.dto.tenant.cast.CastResponse;
import com.kizuna.model.dto.tenant.cast.CastUpdateRequest;
import com.kizuna.model.entity.tenant.Cast;
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
  @InjectMocks private CastServiceImpl castService;

  @Test
  void list_returnsPage() {
    Cast g = new Cast();
    g.setName("Test");
    Page<Cast> page = new PageImpl<>(List.of(g));
    when(castRepository.findByNameContainingIgnoreCase(eq("test"), any(PageRequest.class)))
        .thenReturn(page);

    Page<CastResponse> result = castService.list("test", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void get_returnsResponse() {
    Cast g = new Cast();
    g.setId("g1");
    when(castRepository.findById("g1")).thenReturn(Optional.of(g));
    assertThat(castService.get("g1").getId()).isEqualTo("g1");
  }

  @Test
  void create_savesAndReturns() {
    CastCreateRequest req = new CastCreateRequest();
    req.setName("G1");
    when(castRepository.save(any()))
        .thenAnswer(
            i -> {
              Cast g = i.getArgument(0);
              g.setId("g_new");
              return g;
            });
    CastResponse res = castService.create(req);
    assertThat(res.getId()).isEqualTo("g_new");
  }

  @Test
  void create_新フィールドが保存される() {
    CastCreateRequest req = new CastCreateRequest();
    req.setName("TestCast");
    req.setPhotoUrl("https://example.com/photo.jpg");
    req.setIntroduction("自己紹介テスト");
    req.setAge(25);
    req.setHeight(165);
    req.setBust(88);
    req.setWaist(58);
    req.setHip(85);
    req.setDisplayOrder(1);

    when(castRepository.save(any()))
        .thenAnswer(
            i -> {
              Cast g = i.getArgument(0);
              g.setId("g_new2");
              return g;
            });

    CastResponse res = castService.create(req);
    assertThat(res.getId()).isEqualTo("g_new2");
    assertThat(res.getPhotoUrl()).isEqualTo("https://example.com/photo.jpg");
    assertThat(res.getIntroduction()).isEqualTo("自己紹介テスト");
    assertThat(res.getAge()).isEqualTo(25);
    assertThat(res.getHeight()).isEqualTo(165);
    assertThat(res.getBust()).isEqualTo(88);
    assertThat(res.getWaist()).isEqualTo(58);
    assertThat(res.getHip()).isEqualTo(85);
    assertThat(res.getDisplayOrder()).isEqualTo(1);
  }

  @Test
  void update_modifiesFields() {
    Cast g = new Cast();
    when(castRepository.findById("g1")).thenReturn(Optional.of(g));
    when(castRepository.save(any())).thenReturn(g);

    CastUpdateRequest req = new CastUpdateRequest();
    req.setName("G_Updated");
    castService.update("g1", req);
    assertThat(g.getName()).isEqualTo("G_Updated");
  }

  @Test
  void update_新フィールドが更新される() {
    Cast g = new Cast();
    g.setName("Original");
    g.setStatus("ACTIVE");
    when(castRepository.findById("g1")).thenReturn(Optional.of(g));
    when(castRepository.save(any())).thenReturn(g);

    CastUpdateRequest req = new CastUpdateRequest();
    req.setPhotoUrl("https://example.com/new-photo.jpg");
    req.setIntroduction("更新された自己紹介");
    req.setAge(26);
    req.setHeight(170);
    req.setBust(90);
    req.setWaist(60);
    req.setHip(88);
    req.setDisplayOrder(2);

    castService.update("g1", req);

    assertThat(g.getPhotoUrl()).isEqualTo("https://example.com/new-photo.jpg");
    assertThat(g.getIntroduction()).isEqualTo("更新された自己紹介");
    assertThat(g.getAge()).isEqualTo(26);
    assertThat(g.getHeight()).isEqualTo(170);
    assertThat(g.getBust()).isEqualTo(90);
    assertThat(g.getWaist()).isEqualTo(60);
    assertThat(g.getHip()).isEqualTo(88);
    assertThat(g.getDisplayOrder()).isEqualTo(2);
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
    active1.setName("Active1");
    active1.setStatus("ACTIVE");
    active1.setDisplayOrder(1);

    Cast active2 = new Cast();
    active2.setId("g2");
    active2.setName("Active2");
    active2.setStatus("ACTIVE");
    active2.setDisplayOrder(2);

    when(castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE"))
        .thenReturn(List.of(active1, active2));

    List<CastResponse> result = castService.listActive();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo("g1");
    assertThat(result.get(0).getName()).isEqualTo("Active1");
    assertThat(result.get(1).getId()).isEqualTo("g2");
    assertThat(result.get(1).getName()).isEqualTo("Active2");
  }
}
