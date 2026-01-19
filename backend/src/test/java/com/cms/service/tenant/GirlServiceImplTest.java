package com.cms.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.exception.ServiceException;
import com.cms.model.dto.tenant.girl.GirlCreateRequest;
import com.cms.model.dto.tenant.girl.GirlResponse;
import com.cms.model.dto.tenant.girl.GirlUpdateRequest;
import com.cms.model.entity.tenant.Girl;
import com.cms.repository.tenant.GirlRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class GirlServiceImplTest {

  @Mock GirlRepository girlRepository;
  @InjectMocks GirlServiceImpl service;
  @Captor ArgumentCaptor<Girl> girlCaptor;

  @Test
  void listReturnsFilteredPage() {
    Girl g = new Girl();
    g.setId("g1");
    g.setName("Anna");
    Page<Girl> page = new PageImpl<>(List.of(g));
    when(girlRepository.findByNameContainingIgnoreCase(any(), any())).thenReturn(page);

    Page<GirlResponse> res = service.list("Ann", PageRequest.of(0, 10));
    assertThat(res.getContent()).hasSize(1);
    assertThat(res.getContent().get(0).getName()).isEqualTo("Anna");
  }

  @Test
  void getThrowsIfNotFound() {
    when(girlRepository.findById("g1")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get("g1")).isInstanceOf(ServiceException.class);
  }

  @Test
  void createSavesGirl() {
    GirlCreateRequest req = new GirlCreateRequest();
    req.setName("Bella");
    when(girlRepository.save(any(Girl.class))).thenAnswer(i -> i.getArgument(0));

    service.create(req);
    verify(girlRepository).save(girlCaptor.capture());
    assertThat(girlCaptor.getValue().getName()).isEqualTo("Bella");
    assertThat(girlCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
  }

  @Test
  void updateModifiesGirl() {
    Girl existing = new Girl();
    existing.setId("g1");
    existing.setName("Cathy");
    when(girlRepository.findById("g1")).thenReturn(Optional.of(existing));
    when(girlRepository.save(any(Girl.class))).thenAnswer(i -> i.getArgument(0));

    GirlUpdateRequest req = new GirlUpdateRequest();
    req.setName("Catherine");

    GirlResponse res = service.update("g1", req);
    assertThat(res.getName()).isEqualTo("Catherine");
  }

  @Test
  void deleteThrowsIfNotFound() {
    when(girlRepository.existsById("g1")).thenReturn(false);
    assertThatThrownBy(() -> service.delete("g1")).isInstanceOf(ServiceException.class);
  }
}
