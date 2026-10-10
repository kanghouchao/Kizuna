package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.api.dto.CastFieldDefinitionCreateRequest;
import com.kizuna.cast.api.dto.CastFieldDefinitionMapper;
import com.kizuna.cast.api.dto.CastFieldDefinitionUpdateRequest;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastEnrollmentSnapshot;
import com.kizuna.cast.domain.CastEnrollmentSnapshotRepository;
import com.kizuna.cast.domain.CastEnrollmentStatusHistoryRepository;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class CastFieldAuditTest {
  private final CastFieldDefinitionRepository definitions =
      mock(CastFieldDefinitionRepository.class);
  private final CastEnrollmentRepository enrollments = mock(CastEnrollmentRepository.class);
  private final CastProfileRepository profiles = mock(CastProfileRepository.class);
  private final CastEnrollmentSnapshotRepository snapshots =
      mock(CastEnrollmentSnapshotRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final List<AuditEvent> recorded = new ArrayList<>();
  private CastFieldDefinitionService service;

  @BeforeEach
  void setup() {
    var store = new StoreContext();
    store.setStoreId(1L);
    var stores = mock(StoreRepository.class);
    var audit = new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC()));
    var lifecycle =
        new CastEnrollmentService(
            enrollments,
            mock(CastEnrollmentStatusHistoryRepository.class),
            snapshots,
            users,
            stores,
            store,
            audit);
    service =
        new CastFieldDefinitionService(
            definitions,
            lifecycle,
            Mappers.getMapper(CastFieldDefinitionMapper.class),
            stores,
            store,
            enrollments,
            profiles,
            audit);
    var actor =
        PlatformUser.builder()
            .email("operator@example.test")
            .password(UUID.randomUUID().toString())
            .displayName("監査担当")
            .userType(UserType.STAFF)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    actor.setId(9L);
    when(users.findByEmail("operator@example.test")).thenReturn(Optional.of(actor));
    when(definitions.findMaxDisplayOrder()).thenReturn(null);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", null, List.of()));
    when(events.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              recorded.add(event);
              return event;
            });
    when(definitions.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              CastFieldDefinition definition = call.getArgument(0);
              definition.setId("definition");
              definition.setStoreId(1L);
              ReflectionTestUtils.setField(definition, "version", 0L);
              return definition;
            });
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void creationRecordsSafeDefinitionWithoutCustomKeyOrLabel() {
    var request = new CastFieldDefinitionCreateRequest();
    request.setKey("private-key");
    request.setLabel("秘密の項目");
    request.setIsPublic(true);
    assertThat(service.create(request).getId()).isEqualTo("definition");
    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("CAST_FIELD_DEFINITION_CREATED");
    assertThat(event.getTargetType()).isEqualTo("CAST_FIELD_DEFINITION");
    assertThat(event.getTargetId()).isEqualTo("definition");
    assertThat(event.getActorId()).isEqualTo(9L);
    assertThat(event.getBeforeValues()).isEmpty();
    assertThat(event.getAfterValues())
        .containsOnlyKeys("exists", "is_public", "display_order", "version")
        .containsEntry("exists", "true")
        .containsEntry("is_public", "true")
        .containsEntry("display_order", "0")
        .containsEntry("version", "0");
  }

  @Test
  void labelOnlyChangesAreRecordedButSameValuesAreSilent() {
    var definition =
        CastFieldDefinition.builder()
            .key("private-key")
            .label("旧い秘密")
            .isPublic(false)
            .displayOrder(3)
            .build();
    definition.setId("definition");
    definition.setStoreId(1L);
    ReflectionTestUtils.setField(definition, "version", 4L);
    when(definitions.findById("definition")).thenReturn(Optional.of(definition));
    when(definitions.save(any())).thenAnswer(call -> call.getArgument(0));
    var same = new CastFieldDefinitionUpdateRequest();
    same.setLabel("旧い秘密");
    same.setDisplayOrder(3);
    same.setIsPublic(false);
    service.update("definition", same);
    assertThat(recorded).isEmpty();
    var changed = new CastFieldDefinitionUpdateRequest();
    changed.setLabel("新しい秘密");
    service.update("definition", changed);
    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("CAST_FIELD_DEFINITION_UPDATED");
    assertThat(event.getBeforeValues())
        .containsEntry("display_order", "3")
        .containsEntry("version", "4");
    assertThat(event.getAfterValues())
        .containsOnlyKeys(
            "exists", "is_public", "display_order", "version", "redacted_fields_changed")
        .containsEntry("redacted_fields_changed", "label");
    assertThat(event.getAfterValues().toString()).doesNotContain("秘密", "private-key");
    service.update("definition", changed);
    assertThat(recorded).hasSize(1);
  }

  @Test
  void deletionTracesOnlyAffectedRowsAndExistingSnapshotWithoutCopyingValues() {
    var definition =
        CastFieldDefinition.builder()
            .key("private-key")
            .label("秘密の項目")
            .isPublic(false)
            .displayOrder(2)
            .build();
    definition.setId("definition");
    definition.setStoreId(1L);
    ReflectionTestUtils.setField(definition, "version", 4L);
    when(definitions.existsById("definition")).thenReturn(true);
    when(definitions.findById("definition")).thenReturn(Optional.of(definition));
    var affected = CastEnrollment.builder().build();
    affected.setId("affected");
    affected.setStoreId(1L);
    ReflectionTestUtils.setField(affected, "version", 5L);
    affected.replaceCustomFields(Map.of("private-key", "秘密の内部値", "remaining", "残す値"));
    var untouched = CastEnrollment.builder().build();
    untouched.setId("untouched");
    var profile = CastProfile.builder().enrollmentId("affected").build();
    profile.setId("profile");
    profile.setStoreId(1L);
    ReflectionTestUtils.setField(profile, "version", 8L);
    profile.replaceCustomFields(Map.of("private-key", "秘密の公開値", "remaining", "残す値"));
    when(enrollments.findAllForUpdate()).thenReturn(List.of(affected, untouched));
    when(profiles.findAll()).thenReturn(List.of(profile, CastProfile.builder().build()));
    var saved = new ArrayList<CastEnrollmentSnapshot>();
    when(snapshots.save(any()))
        .thenAnswer(
            call -> {
              CastEnrollmentSnapshot snapshot = call.getArgument(0);
              snapshot.setId("snapshot");
              saved.add(snapshot);
              return snapshot;
            });
    service.delete("definition", "operator@example.test");
    assertThat(affected.getCustomFields()).containsExactlyEntriesOf(Map.of("remaining", "残す値"));
    assertThat(profile.getCustomFields()).containsExactlyEntriesOf(Map.of("remaining", "残す値"));
    assertThat(saved)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getCustomFields()).containsEntry("private-key", "秘密の内部値");
              assertThat(row.getActorId()).isEqualTo(9L);
            });
    assertThat(recorded)
        .extracting(AuditEvent::getAction)
        .containsExactlyInAnyOrder(
            "CAST_FIELD_DEFINITION_DELETED",
            "CAST_INTERNAL_FIELD_REMOVED",
            "CAST_PROFILE_FIELD_REMOVED");
    var internal =
        recorded.stream().filter(e -> e.getTargetId().equals("affected")).findFirst().orElseThrow();
    assertThat(internal.getSourceType()).isEqualTo("CAST_FIELD_DEFINITION");
    assertThat(internal.getSourceId()).isEqualTo("definition");
    assertThat(internal.getBeforeValues())
        .containsOnlyKeys("exists", "version")
        .containsEntry("version", "5");
    assertThat(internal.getAfterValues())
        .containsOnlyKeys("exists", "version", "snapshot_id", "redacted_fields_changed")
        .containsEntry("snapshot_id", "snapshot")
        .containsEntry("redacted_fields_changed", "internal_custom_fields");
    var publicValue =
        recorded.stream().filter(e -> e.getTargetId().equals("profile")).findFirst().orElseThrow();
    assertThat(publicValue.getSourceId()).isEqualTo("definition");
    assertThat(publicValue.getAfterValues())
        .containsOnlyKeys("exists", "enrollment_id", "version", "redacted_fields_changed")
        .containsEntry("enrollment_id", "affected")
        .containsEntry("redacted_fields_changed", "public_custom_fields");
    var deleted =
        recorded.stream()
            .filter(e -> e.getTargetId().equals("definition"))
            .findFirst()
            .orElseThrow();
    assertThat(deleted.getBeforeValues()).containsEntry("version", "4");
    assertThat(deleted.getAfterValues()).containsExactlyEntriesOf(Map.of("exists", "false"));
    assertThat(recorded)
        .allSatisfy(
            event -> {
              assertThat(event.getActorId()).isEqualTo(9L);
              assertThat(event.getBeforeValues().toString() + event.getAfterValues())
                  .doesNotContain("秘密", "private-key", "remaining", "残す値");
            });
  }
}
