package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastEnrollmentSnapshot;
import com.kizuna.cast.domain.CastEnrollmentSnapshotRepository;
import com.kizuna.cast.domain.CastEnrollmentStatusHistoryRepository;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.cast.domain.CastPublicationStatus;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class CastAuditTest {
  private final CastEnrollmentRepository enrollments = mock(CastEnrollmentRepository.class);
  private final CastProfileRepository profiles = mock(CastProfileRepository.class);
  private final CastEnrollmentStatusHistoryRepository histories =
      mock(CastEnrollmentStatusHistoryRepository.class);
  private final CastEnrollmentSnapshotRepository snapshots =
      mock(CastEnrollmentSnapshotRepository.class);
  private final CastFieldDefinitionRepository definitions =
      mock(CastFieldDefinitionRepository.class);
  private final StoreRepository stores = mock(StoreRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final BusinessAudit audit =
      new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC()));
  private final List<AuditEvent> recorded = new ArrayList<>();
  private final List<CastEnrollmentSnapshot> recordedSnapshots = new ArrayList<>();
  private final AtomicInteger historyIds = new AtomicInteger();
  private CastService service;
  private CastEnrollmentService lifecycle;

  @BeforeEach
  void setup() {
    var context = new StoreContext();
    context.setStoreId(1L);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", null, List.of()));
    lifecycle =
        new CastEnrollmentService(enrollments, histories, snapshots, users, stores, context, audit);
    service =
        new CastService(
            enrollments,
            lifecycle,
            Mappers.getMapper(CastMapper.class),
            profiles,
            stores,
            context,
            new CastInvitationService(
                enrollments, mock(CastInvitationRepository.class), stores, context),
            definitions,
            ids -> Set.of(),
            ids -> Set.of(),
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
    when(users.findByEmail(anyString())).thenReturn(Optional.of(actor));
    when(events.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              recorded.add(event);
              return event;
            });
    when(enrollments.save(any())).thenAnswer(call -> persisted(call.getArgument(0), "enrollment"));
    when(profiles.save(any())).thenAnswer(call -> persisted(call.getArgument(0), "profile"));
    when(snapshots.save(any()))
        .thenAnswer(
            call -> {
              CastEnrollmentSnapshot snapshot = call.getArgument(0);
              recordedSnapshots.add(snapshot);
              return snapshot;
            });
    when(histories.save(any()))
        .thenAnswer(
            call -> persisted(call.getArgument(0), "history-" + historyIds.incrementAndGet()));
  }

  @Test
  void creationAuditsBothEntitiesWithoutDuplicatingInternalHistory() {
    var request = new CastCreateRequest();
    request.setName("非公開の氏名");
    request.setIntroduction("非公開の紹介");
    request.setPhotoUrl("https://example.test/private-photo");
    assertThat(service.create(request, "operator@example.test").getId()).isEqualTo("enrollment");
    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("CAST_ENROLLMENT_CREATED");
    assertThat(event.getTargetType()).isEqualTo("CAST_ENROLLMENT");
    assertThat(event.getTargetId()).isEqualTo("enrollment");
    assertThat(event.getBeforeValues()).isEmpty();
    assertThat(event.getAfterValues())
        .containsEntry("enrollment_version", "0")
        .containsEntry("profile_version", "0")
        .containsEntry("profile_id", "profile")
        .containsEntry("status", "ENROLLED")
        .containsEntry("publication_status", "UNPUBLISHED");
    assertThat(event.getAfterValues().toString())
        .doesNotContain("非公開", "https://", "operator@example.test");
  }

  @Test
  void privateOnlyEditIsAuditedButRepeatedAndEmptyInputsAreNot() {
    existing();
    service.update("enrollment", new CastUpdateRequest(), "operator@example.test");
    assertThat(recorded).isEmpty();
    var request = new CastUpdateRequest();
    request.setName("変更後の非公開名");
    service.update("enrollment", request, "operator@example.test");
    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("CAST_ENROLLMENT_UPDATED");
    assertThat(event.getAfterValues()).containsEntry("redacted_fields_changed", "name");
    assertThat(event.getBeforeValues().toString() + event.getAfterValues())
        .doesNotContain("非公開", "変更後");
    service.update("enrollment", request, "operator@example.test");
    assertThat(recorded).hasSize(1);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void publicationChangeOnlyAuditsTheProfileAndSameValueIsSilent() {
    existing();
    service.changePublication("enrollment", CastPublicationStatus.UNPUBLISHED);
    assertThat(recorded).isEmpty();
    service.changePublication("enrollment", CastPublicationStatus.PUBLISHED);
    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("CAST_PROFILE_PUBLICATION_CHANGED");
    assertThat(event.getTargetType()).isEqualTo("CAST_PROFILE");
    assertThat(event.getTargetId()).isEqualTo("profile");
    assertThat(event.getSourceType()).isEqualTo("CAST_ENROLLMENT");
    assertThat(event.getSourceId()).isEqualTo("enrollment");
    assertThat(event.getBeforeValues()).containsEntry("publication_status", "UNPUBLISHED");
    assertThat(event.getAfterValues())
        .containsEntry("publication_status", "PUBLISHED")
        .containsOnlyKeys("enrollment_id", "version", "publication_status");
    service.changePublication("enrollment", CastPublicationStatus.PUBLISHED);
    assertThat(recorded).hasSize(1);
  }

  @Test
  void lifecycleAuditsOnlyEnrollmentAndReferencesEachExistingHistory() {
    existing();
    lifecycle.suspend("enrollment", "operator@example.test");
    lifecycle.resume("enrollment", "operator@example.test");
    lifecycle.withdraw("enrollment", "operator@example.test");
    assertThat(recorded)
        .extracting(AuditEvent::getAction)
        .containsExactly(
            "CAST_ENROLLMENT_SUSPENDED", "CAST_ENROLLMENT_RESUMED", "CAST_ENROLLMENT_WITHDRAWN");
    assertThat(recorded)
        .extracting(AuditEvent::getSourceId)
        .containsExactly("history-1", "history-2", "history-3");
    for (var event : recorded) {
      assertThat(event.getTargetType()).isEqualTo("CAST_ENROLLMENT");
      assertThat(event.getTargetId()).isEqualTo("enrollment");
      assertThat(event.getSourceType()).isEqualTo("CAST_ENROLLMENT_STATUS_HISTORY");
      assertThat(event.getAfterValues())
          .containsOnlyKeys("exists", "cast_id", "status", "ended_at", "version");
    }
    assertThat(recorded.get(0).getBeforeValues()).containsEntry("status", "ENROLLED");
    assertThat(recorded.get(0).getAfterValues()).containsEntry("status", "SUSPENDED");
    assertThat(recorded.get(1).getAfterValues()).containsEntry("status", "ENROLLED");
    assertThat(recorded.get(2).getAfterValues()).containsEntry("status", "WITHDRAWN");
    assertThat(recorded.get(2).getAfterValues().get("ended_at")).isNotBlank();
    assertThatThrownBy(() -> lifecycle.withdraw("enrollment", "operator@example.test"))
        .isInstanceOf(ServiceException.class);
    assertThat(recorded).hasSize(3);
    assertThat(profiles.findByEnrollmentId("enrollment").orElseThrow().getPublicationStatus())
        .isEqualTo(CastPublicationStatus.UNPUBLISHED);
  }

  @Test
  void internalAndPublicFieldsOnlyExposeFixedCategoryNamesAndKeepHistory() {
    existing();
    when(definitions.findAllByOrderByDisplayOrderAsc())
        .thenReturn(
            List.of(
                CastFieldDefinition.builder().key("private-key").isPublic(false).build(),
                CastFieldDefinition.builder().key("public-key").isPublic(true).build()));
    var request = new CastUpdateRequest();
    request.setCustomFields(Map.of("private-key", "秘密の内部値", "public-key", "秘密の公開値"));
    service.update("enrollment", request, "operator@example.test");
    assertThat(recorded).hasSize(1);
    assertThat(recorded.getFirst().getAfterValues())
        .containsEntry("redacted_fields_changed", "internal_custom_fields,public_custom_fields");
    assertThat(recorded.getFirst().getAfterValues())
        .containsOnlyKeys(
            "exists",
            "cast_id",
            "status",
            "ended_at",
            "enrollment_version",
            "profile_id",
            "profile_version",
            "publication_status",
            "redacted_fields_changed");
    assertThat(recorded.getFirst().getAfterValues().toString())
        .doesNotContain("private-key", "public-key", "秘密");
    service.update("enrollment", request, "operator@example.test");
    assertThat(recorded).hasSize(1);
    request.setCustomFields(Map.of());
    service.update("enrollment", request, "operator@example.test");
    assertThat(recorded).hasSize(2);
    assertThat(recorded.getLast().getAfterValues())
        .containsEntry("redacted_fields_changed", "internal_custom_fields,public_custom_fields");
    assertThat(enrollments.findById("enrollment").orElseThrow().getCustomFields()).isEmpty();
    assertThat(profiles.findByEnrollmentId("enrollment").orElseThrow().getCustomFields()).isEmpty();
    assertThat(recordedSnapshots)
        .extracting(CastEnrollmentSnapshot::getCustomFields)
        .containsExactly(Map.of(), Map.of("private-key", "秘密の内部値"));
  }

  @Test
  void elevationUsesExistingSinkMetadataAndRefusedTransitionAddsNothing() {
    existing();
    var jwt =
        Jwt.withTokenValue(UUID.randomUUID().toString())
            .header("alg", "HS256")
            .subject("operator@example.test")
            .claim("elevationId", 77L)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    lifecycle.suspend("enrollment", "operator@example.test");
    assertThat(recorded).hasSize(1);
    assertThat(recorded.getFirst().getAfterValues()).containsEntry("emergency_elevation_id", "77");
    assertThat(recorded.getFirst().getActorId()).isEqualTo(9L);
    assertThat(recorded.getFirst().getStoreId()).isEqualTo(1L);
    assertThatThrownBy(() -> lifecycle.suspend("enrollment", "operator@example.test"))
        .isInstanceOf(ServiceException.class);
    var request = new CastUpdateRequest();
    request.setCustomFields(Map.of("unknown-private-key", "秘密"));
    assertThatThrownBy(() -> service.update("enrollment", request, "operator@example.test"))
        .isInstanceOf(ServiceException.class);
    assertThat(recorded).hasSize(1);
  }

  private void existing() {
    var enrollment = persisted(CastEnrollment.builder().build(), "enrollment");
    var profile =
        persisted(CastProfile.builder().enrollmentId("enrollment").name("非公開名").build(), "profile");
    when(enrollments.findScopedByIdForUpdate("enrollment")).thenReturn(Optional.of(enrollment));
    when(enrollments.findById("enrollment")).thenReturn(Optional.of(enrollment));
    when(profiles.findByEnrollmentId("enrollment")).thenReturn(Optional.of(profile));
  }

  private static <T extends StoreScopedEntity> T persisted(T entity, String id) {
    entity.setId(id);
    entity.setStoreId(1L);
    ReflectionTestUtils.setField(entity, "version", 0L);
    return entity;
  }
}
