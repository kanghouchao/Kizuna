package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastEnrollmentSnapshotRepository;
import com.kizuna.cast.domain.CastEnrollmentStatusHistoryRepository;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.cast.domain.CastPublicationStatus;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CastServiceTest {
  @Mock CastEnrollmentRepository enrollments;
  @Mock CastProfileRepository profiles;
  @Mock CastInvitationService invitations;
  @Mock CastInvitationRepository invitationRepository;
  @Mock CastFieldDefinitionRepository definitions;
  @Mock AttendanceReferenceCheck attendance;
  @Mock StoreRepository stores;
  @Mock CastEnrollmentStatusHistoryRepository histories;
  @Mock CastEnrollmentSnapshotRepository snapshots;
  @Mock PlatformUserRepository users;
  CastService service;

  @BeforeEach
  void setup() {
    StoreContext context = new StoreContext();
    context.setStoreId(1L);
    service =
        new CastService(
            enrollments,
            new CastEnrollmentService(
                enrollments, histories, snapshots, users, invitationRepository, stores, context),
            Mappers.getMapper(CastMapper.class),
            profiles,
            stores,
            context,
            invitations,
            definitions,
            attendance);
  }

  private void existing() {
    CastEnrollment enrollment = CastEnrollment.builder().build();
    enrollment.setId("e1");
    when(enrollments.findScopedByIdForUpdate("e1")).thenReturn(Optional.of(enrollment));
    when(profiles.findByEnrollmentId("e1"))
        .thenReturn(Optional.of(CastProfile.builder().name("花").enrollmentId("e1").build()));
  }

  private void actor() {
    PlatformUser actor =
        PlatformUser.builder()
            .userType(UserType.CAST)
            .email("actor@kizuna.test")
            .password("encoded")
            .build();
    actor.setId(1L);
    when(users.findByEmail("actor")).thenReturn(Optional.of(actor));
  }

  @Test
  void createIsUnpublished() {
    actor();
    when(enrollments.save(any()))
        .thenAnswer(
            inv -> {
              CastEnrollment e = inv.getArgument(0);
              e.setId("e1");
              return e;
            });
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));
    CastCreateRequest request = new CastCreateRequest();
    request.setName("花");
    var response = service.create(request, "actor");
    assertThat(response.getId()).isEqualTo("e1");
    assertThat(response.getName()).isEqualTo("花");
    assertThat(response.getPublicationStatus()).isEqualTo(CastPublicationStatus.UNPUBLISHED);
  }

  @Test
  void updateSplitsValuesAndReturnsCombinedManagementView() {
    actor();
    existing();
    when(definitions.findAllByOrderByDisplayOrderAsc())
        .thenReturn(
            List.of(
                CastFieldDefinition.builder().key("memo").isPublic(false).build(),
                CastFieldDefinition.builder().key("hobby").isPublic(true).build()));
    CastUpdateRequest request = new CastUpdateRequest();
    request.setCustomFields(Map.of("memo", "内部", "hobby", "読書"));
    assertThat(service.update("e1", request, "actor").getCustomFields())
        .containsExactlyInAnyOrderEntriesOf(request.getCustomFields());
    assertThat(enrollments.findScopedByIdForUpdate("e1").orElseThrow().getCustomFields())
        .containsOnlyKeys("memo");
    assertThat(profiles.findByEnrollmentId("e1").orElseThrow().getCustomFields())
        .containsOnlyKeys("hobby");
    request.setCustomFields(Map.of());
    assertThat(service.update("e1", request, "actor").getCustomFields()).isEmpty();
  }

  @Test
  void unknownFieldIsRejected() {
    existing();
    CastUpdateRequest request = new CastUpdateRequest();
    request.setCustomFields(Map.of("unknown", "値"));
    assertThatThrownBy(() -> service.update("e1", request, "actor"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void longValueIsRejected() {
    existing();
    when(definitions.findAllByOrderByDisplayOrderAsc())
        .thenReturn(List.of(CastFieldDefinition.builder().key("memo").isPublic(false).build()));
    CastUpdateRequest request = new CastUpdateRequest();
    request.setCustomFields(Map.of("memo", "x".repeat(501)));
    assertThatThrownBy(() -> service.update("e1", request, "actor"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void publicationChanges() {
    CastEnrollment enrollment = CastEnrollment.builder().build();
    when(enrollments.findById("e1")).thenReturn(Optional.of(enrollment));
    when(profiles.findByEnrollmentId("e1"))
        .thenReturn(Optional.of(CastProfile.builder().name("花").build()));
    assertThat(service.changePublication("e1", CastPublicationStatus.PUBLISHED).publicationStatus())
        .isEqualTo(CastPublicationStatus.PUBLISHED);
  }

  @Test
  void missingIs404() {
    assertThatThrownBy(() -> service.get("missing")).isInstanceOf(NotFoundException.class);
  }

  @Test
  void attendancePreventsDeletion() {
    when(enrollments.findScopedByIdForUpdate("e1"))
        .thenReturn(Optional.of(CastEnrollment.builder().build()));
    when(attendance.existsForCast("e1")).thenReturn(true);
    assertThatThrownBy(() -> service.delete("e1")).isInstanceOf(ConflictException.class);
  }
}
