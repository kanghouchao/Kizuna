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
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionPatch;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CastFieldDefinitionServiceTest {

  private static final Long STORE_ID = 1L;

  @Mock private CastFieldDefinitionRepository repository;
  @Mock private CastEnrollmentService enrollmentService;
  @Mock private CastFieldDefinitionMapper mapper;
  @Mock private StoreRepository storeRepository;
  @Mock private CastEnrollmentRepository enrollmentRepository;
  @Mock private CastProfileRepository profileRepository;
  @Mock private BusinessAudit audit;

  @InjectMocks private CastFieldDefinitionService service;

  private CastFieldDefinitionCreateRequest createRequest(String key, String label) {
    CastFieldDefinitionCreateRequest req = new CastFieldDefinitionCreateRequest();
    req.setKey(key);
    req.setLabel(label);
    return req;
  }

  @Test
  void create_autoNumbersFromZeroWhenEmpty() {
    when(repository.existsByStoreIdAndKey(STORE_ID, "blood_type")).thenReturn(false);
    when(repository.countByStoreId(STORE_ID)).thenReturn(0L);
    when(repository.findMaxDisplayOrder(STORE_ID)).thenReturn(null);
    when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(new CastFieldDefinitionResponse());

    service.create(STORE_ID, createRequest("blood_type", "血液型"));

    ArgumentCaptor<CastFieldDefinition> captor = ArgumentCaptor.forClass(CastFieldDefinition.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getDisplayOrder()).isEqualTo(0);
    assertThat(captor.getValue().getStoreId()).isEqualTo(STORE_ID);
  }

  @Test
  void create_autoNumbersMaxPlusOne() {
    when(repository.existsByStoreIdAndKey(STORE_ID, "hobby")).thenReturn(false);
    when(repository.countByStoreId(STORE_ID)).thenReturn(3L);
    when(repository.findMaxDisplayOrder(STORE_ID)).thenReturn(5);
    when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(new CastFieldDefinitionResponse());

    service.create(STORE_ID, createRequest("hobby", "趣味"));

    ArgumentCaptor<CastFieldDefinition> captor = ArgumentCaptor.forClass(CastFieldDefinition.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getDisplayOrder()).isEqualTo(6);
  }

  @Test
  void create_defaultsIsPublicToFalseWhenOmitted() {
    when(repository.existsByStoreIdAndKey(STORE_ID, "hobby")).thenReturn(false);
    when(repository.countByStoreId(STORE_ID)).thenReturn(0L);
    when(repository.findMaxDisplayOrder(STORE_ID)).thenReturn(null);
    when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(new CastFieldDefinitionResponse());

    service.create(STORE_ID, createRequest("hobby", "趣味"));

    ArgumentCaptor<CastFieldDefinition> captor = ArgumentCaptor.forClass(CastFieldDefinition.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getIsPublic()).isFalse();
  }

  @Test
  void create_rejectsDuplicateKeyWith400() {
    when(repository.existsByStoreIdAndKey(STORE_ID, "blood_type")).thenReturn(true);

    assertThatThrownBy(() -> service.create(STORE_ID, createRequest("blood_type", "血液型")))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("既に登録されています");

    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void create_rejectsWhenAtCapacityWith400() {
    when(repository.existsByStoreIdAndKey(STORE_ID, "hobby")).thenReturn(false);
    when(repository.countByStoreId(STORE_ID)).thenReturn(20L);

    assertThatThrownBy(() -> service.create(STORE_ID, createRequest("hobby", "趣味")))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("最大20件");

    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void create_propagatesDataIntegrityViolation() {
    // 事前チェックをすり抜けた整合性違反はサービスで握りつぶさず、そのまま伝播すること。
    // 一意違反→409 / それ以外→500 の分類は CommonExceptionHandler が SQLSTATE で行うため、
    // サービス層で 400 に変換すると FK 等の実装欠陥まで「重複」に化けてしまう。
    when(repository.existsByStoreIdAndKey(STORE_ID, "blood_type")).thenReturn(false);
    when(repository.countByStoreId(STORE_ID)).thenReturn(0L);
    when(repository.findMaxDisplayOrder(STORE_ID)).thenReturn(null);
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException("uq_t_cast_field_definitions_store_key");
    when(repository.saveAndFlush(any())).thenThrow(violation);

    assertThatThrownBy(() -> service.create(STORE_ID, createRequest("blood_type", "血液型")))
        .isSameAs(violation);
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
    when(repository.findByIdAndStoreId("d1", STORE_ID)).thenReturn(Optional.of(existing));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(new CastFieldDefinitionResponse());

    CastFieldDefinitionUpdateRequest req = new CastFieldDefinitionUpdateRequest();
    req.setLabel("血液型（更新）");
    when(mapper.toPatch(req)).thenReturn(new CastFieldDefinitionPatch("血液型（更新）", null, null));

    service.update(STORE_ID, "d1", req);

    assertThat(existing.getLabel()).isEqualTo("血液型（更新）");
    // null フィールドは変更なし
    assertThat(existing.getDisplayOrder()).isEqualTo(1);
    assertThat(existing.getIsPublic()).isFalse();
    verify(repository).save(existing);
  }

  @Test
  void update_throwsWhenNotFound() {
    when(repository.findByIdAndStoreId("missing", STORE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.update(STORE_ID, "missing", new CastFieldDefinitionUpdateRequest()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("見つかりません");
  }

  @Test
  void delete_hardDeletesById() {
    CastFieldDefinition definition = CastFieldDefinition.builder().key("memo").build();
    when(repository.findByIdAndStoreId("d1", STORE_ID)).thenReturn(Optional.of(definition));

    service.delete(STORE_ID, "d1", "actor");

    verify(repository).delete(definition);
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(repository.findByIdAndStoreId("missing", STORE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(STORE_ID, "missing", "actor"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("見つかりません");

    verify(repository, never()).delete(any());
  }
}
