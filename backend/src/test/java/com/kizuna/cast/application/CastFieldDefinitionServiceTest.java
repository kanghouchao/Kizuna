package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.api.dto.CastFieldDefinitionCreateRequest;
import com.kizuna.cast.api.dto.CastFieldDefinitionMapper;
import com.kizuna.cast.api.dto.CastFieldDefinitionResponse;
import com.kizuna.cast.api.dto.CastFieldDefinitionUpdateRequest;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionPatch;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CastFieldDefinitionServiceTest {

  @Mock private CastFieldDefinitionRepository repository;
  @Mock private CastFieldDefinitionMapper mapper;
  @Mock private TenantContext tenantContext;

  @InjectMocks private CastFieldDefinitionService service;

  private CastFieldDefinitionCreateRequest createRequest(String key, String label) {
    CastFieldDefinitionCreateRequest req = new CastFieldDefinitionCreateRequest();
    req.setKey(key);
    req.setLabel(label);
    return req;
  }

  @Test
  void create_autoNumbersFromZeroWhenEmptyAndSetsTenantId() {
    when(repository.existsByKey("blood_type")).thenReturn(false);
    when(repository.count()).thenReturn(0L);
    when(repository.findMaxDisplayOrder()).thenReturn(null);
    when(tenantContext.getTenantId()).thenReturn(7L);
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(new CastFieldDefinitionResponse());

    service.create(createRequest("blood_type", "血液型"));

    ArgumentCaptor<CastFieldDefinition> captor = ArgumentCaptor.forClass(CastFieldDefinition.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getDisplayOrder()).isEqualTo(0);
    assertThat(captor.getValue().getTenantId()).isEqualTo(7L);
  }

  @Test
  void create_autoNumbersMaxPlusOne() {
    when(repository.existsByKey("hobby")).thenReturn(false);
    when(repository.count()).thenReturn(3L);
    when(repository.findMaxDisplayOrder()).thenReturn(5);
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(new CastFieldDefinitionResponse());

    service.create(createRequest("hobby", "趣味"));

    ArgumentCaptor<CastFieldDefinition> captor = ArgumentCaptor.forClass(CastFieldDefinition.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getDisplayOrder()).isEqualTo(6);
  }

  @Test
  void create_defaultsIsPublicToFalseWhenOmitted() {
    when(repository.existsByKey("hobby")).thenReturn(false);
    when(repository.count()).thenReturn(0L);
    when(repository.findMaxDisplayOrder()).thenReturn(null);
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(new CastFieldDefinitionResponse());

    service.create(createRequest("hobby", "趣味"));

    ArgumentCaptor<CastFieldDefinition> captor = ArgumentCaptor.forClass(CastFieldDefinition.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getIsPublic()).isFalse();
  }

  @Test
  void create_rejectsDuplicateKeyWith400() {
    when(repository.existsByKey("blood_type")).thenReturn(true);

    assertThatThrownBy(() -> service.create(createRequest("blood_type", "血液型")))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("既に登録されています");

    verify(repository, never()).save(any());
  }

  @Test
  void create_rejectsWhenAtCapacityWith400() {
    when(repository.existsByKey("hobby")).thenReturn(false);
    when(repository.count()).thenReturn(20L);

    assertThatThrownBy(() -> service.create(createRequest("hobby", "趣味")))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("最大20件");

    verify(repository, never()).save(any());
  }

  @Test
  void update_appliesPatchAndSaves() {
    CastFieldDefinition existing =
        CastFieldDefinition.builder()
            .key("blood_type")
            .label("血液型")
            .displayOrder(1)
            .isPublic(false)
            .build();
    when(repository.findById("d1")).thenReturn(Optional.of(existing));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(new CastFieldDefinitionResponse());

    CastFieldDefinitionUpdateRequest req = new CastFieldDefinitionUpdateRequest();
    req.setLabel("血液型（更新）");
    when(mapper.toPatch(req)).thenReturn(new CastFieldDefinitionPatch("血液型（更新）", null, null));

    service.update("d1", req);

    assertThat(existing.getLabel()).isEqualTo("血液型（更新）");
    // null フィールドは変更なし
    assertThat(existing.getDisplayOrder()).isEqualTo(1);
    assertThat(existing.getIsPublic()).isFalse();
    verify(repository).save(existing);
  }

  @Test
  void update_throwsWhenNotFound() {
    when(repository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("missing", new CastFieldDefinitionUpdateRequest()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("見つかりません");
  }

  @Test
  void delete_hardDeletesById() {
    when(repository.existsById("d1")).thenReturn(true);

    service.delete("d1");

    verify(repository).deleteById("d1");
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(repository.existsById("missing")).thenReturn(false);

    assertThatThrownBy(() -> service.delete("missing"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("見つかりません");

    verify(repository, never()).deleteById(any());
  }
}
