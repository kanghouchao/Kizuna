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
  void create_新フィールドが保存される() {
    GirlCreateRequest req = new GirlCreateRequest();
    req.setName("TestGirl");
    req.setPhotoUrl("https://example.com/photo.jpg");
    req.setIntroduction("自己紹介テスト");
    req.setAge(25);
    req.setHeight(165);
    req.setBust(88);
    req.setWaist(58);
    req.setHip(85);
    req.setDisplayOrder(1);

    when(girlRepository.save(any()))
        .thenAnswer(
            i -> {
              Girl g = i.getArgument(0);
              g.setId("g_new2");
              return g;
            });

    GirlResponse res = girlService.create(req);
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
    Girl g = new Girl();
    when(girlRepository.findById("g1")).thenReturn(Optional.of(g));
    when(girlRepository.save(any())).thenReturn(g);

    GirlUpdateRequest req = new GirlUpdateRequest();
    req.setName("G_Updated");
    girlService.update("g1", req);
    assertThat(g.getName()).isEqualTo("G_Updated");
  }

  @Test
  void update_新フィールドが更新される() {
    Girl g = new Girl();
    g.setName("Original");
    g.setStatus("ACTIVE");
    when(girlRepository.findById("g1")).thenReturn(Optional.of(g));
    when(girlRepository.save(any())).thenReturn(g);

    GirlUpdateRequest req = new GirlUpdateRequest();
    req.setPhotoUrl("https://example.com/new-photo.jpg");
    req.setIntroduction("更新された自己紹介");
    req.setAge(26);
    req.setHeight(170);
    req.setBust(90);
    req.setWaist(60);
    req.setHip(88);
    req.setDisplayOrder(2);

    girlService.update("g1", req);

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
    when(girlRepository.existsById("g1")).thenReturn(true);
    girlService.delete("g1");
    verify(girlRepository).deleteById("g1");
  }

  @Test
  void listActive_ACTIVEステータスのみ返す() {
    Girl active1 = new Girl();
    active1.setId("g1");
    active1.setName("Active1");
    active1.setStatus("ACTIVE");
    active1.setDisplayOrder(1);

    Girl active2 = new Girl();
    active2.setId("g2");
    active2.setName("Active2");
    active2.setStatus("ACTIVE");
    active2.setDisplayOrder(2);

    when(girlRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE"))
        .thenReturn(List.of(active1, active2));

    List<GirlResponse> result = girlService.listActive();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo("g1");
    assertThat(result.get(0).getName()).isEqualTo("Active1");
    assertThat(result.get(1).getId()).isEqualTo("g2");
    assertThat(result.get(1).getName()).isEqualTo("Active2");
  }
}
