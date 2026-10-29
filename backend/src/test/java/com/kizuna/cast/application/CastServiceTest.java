package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.domain.ApplicantReferenceCheck;
import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastEnrollmentSnapshotRepository;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastEnrollmentStatusHistory;
import com.kizuna.cast.domain.CastEnrollmentStatusHistoryRepository;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastManagementView;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.cast.domain.CastPublicationStatus;
import com.kizuna.cast.domain.OrderReferenceCheck;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CastServiceTest {
  private static final Long STORE_ID = 1L;

  @Mock CastEnrollmentRepository enrollments;
  @Mock CastProfileRepository profiles;
  @Mock CastInvitationService invitations;
  @Mock CastFieldDefinitionRepository definitions;
  @Mock AttendanceReferenceCheck attendance;
  @Mock OrderReferenceCheck orders;
  @Mock ApplicantReferenceCheck applicantReferences;
  @Mock StoreRepository stores;
  @Mock CastEnrollmentStatusHistoryRepository histories;
  @Mock CastEnrollmentSnapshotRepository snapshots;
  @Mock UserRepository users;
  @Mock BusinessAudit audit;
  CastService service;

  @BeforeEach
  void setup() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("actor", null, List.of()));
    service =
        new CastService(
            enrollments,
            new CastEnrollmentService(enrollments, histories, snapshots, users, stores, audit),
            Mappers.getMapper(CastMapper.class),
            profiles,
            stores,
            invitations,
            definitions,
            attendance,
            orders,
            applicantReferences,
            audit);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  private void existing() {
    CastEnrollment enrollment = CastEnrollment.builder().build();
    enrollment.setId("e1");
    enrollment.setStoreId(STORE_ID);
    when(enrollments.findByIdAndStoreIdForUpdate("e1", STORE_ID))
        .thenReturn(Optional.of(enrollment));
    when(profiles.findByEnrollmentIdAndStoreId("e1", STORE_ID))
        .thenReturn(Optional.of(CastProfile.builder().name("花").enrollmentId("e1").build()));
  }

  private void actor() {
    User actor =
        User.builder()
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
    var command =
        new CastCreateCommand(
            "花", null, LocalDate.of(2026, 1, 1), null, null, null, null, null, null, null, null);
    var created = service.create(STORE_ID, command, "actor");
    assertThat(created.enrollment().getId()).isEqualTo("e1");
    assertThat(created.deletable()).isTrue();
    var savedEnrollment = ArgumentCaptor.forClass(CastEnrollment.class);
    verify(enrollments).save(savedEnrollment.capture());
    assertThat(savedEnrollment.getValue().getStartedOn()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(savedEnrollment.getValue().getStatus()).isEqualTo(CastEnrollmentStatus.ENROLLED);
    var savedProfile = ArgumentCaptor.forClass(CastProfile.class);
    verify(profiles).save(savedProfile.capture());
    assertThat(savedProfile.getValue().getName()).isEqualTo("花");
    assertThat(savedProfile.getValue().getEnrollmentId()).isEqualTo("e1");
    assertThat(savedProfile.getValue().getPublicationStatus())
        .isEqualTo(CastPublicationStatus.UNPUBLISHED);
    var savedHistory = ArgumentCaptor.forClass(CastEnrollmentStatusHistory.class);
    verify(histories).save(savedHistory.capture());
    assertThat(
            List.of(
                savedEnrollment.getValue().getStoreId(),
                savedProfile.getValue().getStoreId(),
                savedHistory.getValue().getStoreId()))
        .containsOnly(STORE_ID);
  }

  @Test
  void updateSplitsValuesAndReturnsCombinedManagementView() {
    actor();
    existing();
    when(definitions.findAllByStoreIdOrderByDisplayOrderAsc(STORE_ID))
        .thenReturn(
            List.of(
                CastFieldDefinition.builder().key("memo").isPublic(false).build(),
                CastFieldDefinition.builder().key("hobby").isPublic(true).build()));
    CastUpdateRequest request = new CastUpdateRequest();
    request.setCustomFields(Map.of("memo", "内部", "hobby", "読書"));
    assertThat(service.update(STORE_ID, "e1", request, "actor").getCustomFields())
        .containsExactlyInAnyOrderEntriesOf(request.getCustomFields());
    assertThat(
            enrollments.findByIdAndStoreIdForUpdate("e1", STORE_ID).orElseThrow().getCustomFields())
        .containsOnlyKeys("memo");
    assertThat(
            profiles.findByEnrollmentIdAndStoreId("e1", STORE_ID).orElseThrow().getCustomFields())
        .containsOnlyKeys("hobby");
    request.setCustomFields(Map.of());
    assertThat(service.update(STORE_ID, "e1", request, "actor").getCustomFields()).isEmpty();
  }

  @Test
  void unknownFieldIsRejected() {
    existing();
    CastUpdateRequest request = new CastUpdateRequest();
    request.setCustomFields(Map.of("unknown", "値"));
    assertThatThrownBy(() -> service.update(STORE_ID, "e1", request, "actor"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void longValueIsRejected() {
    existing();
    when(definitions.findAllByStoreIdOrderByDisplayOrderAsc(STORE_ID))
        .thenReturn(List.of(CastFieldDefinition.builder().key("memo").isPublic(false).build()));
    CastUpdateRequest request = new CastUpdateRequest();
    request.setCustomFields(Map.of("memo", "x".repeat(501)));
    assertThatThrownBy(() -> service.update(STORE_ID, "e1", request, "actor"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void publicationChanges() {
    CastEnrollment enrollment = CastEnrollment.builder().build();
    when(enrollments.findByIdAndStoreId("e1", STORE_ID)).thenReturn(Optional.of(enrollment));
    when(profiles.findByEnrollmentIdAndStoreId("e1", STORE_ID))
        .thenReturn(Optional.of(CastProfile.builder().name("花").enrollmentId("e1").build()));
    assertThat(
            service
                .changePublication(STORE_ID, "e1", CastPublicationStatus.PUBLISHED)
                .publicationStatus())
        .isEqualTo(CastPublicationStatus.PUBLISHED);
  }

  @Test
  void missingIs404() {
    assertThatThrownBy(() -> service.get(STORE_ID, "missing"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void listResolvesReferencesOnceForTheWholePage() {
    var views =
        List.of("free", "order", "attendance").stream()
            .map(
                id -> {
                  var enrollment = CastEnrollment.builder().build();
                  enrollment.setId(id);
                  return new CastManagementView(enrollment, CastProfile.builder().name(id).build());
                })
            .toList();
    when(profiles.search(eq(STORE_ID), any(), any())).thenReturn(new PageImpl<>(views));
    when(orders.findReferencedCastIds(any())).thenReturn(Set.of("order"));
    when(attendance.findReferencedCastIds(any())).thenReturn(Set.of("attendance"));
    var response = service.list(STORE_ID, null, PageRequest.of(0, 20));
    assertThat(response.getContent())
        .extracting(r -> r.isDeletable())
        .containsExactly(true, false, false);
    verify(orders).findReferencedCastIds(Set.of("free", "order", "attendance"));
    verify(attendance).findReferencedCastIds(Set.of("free", "order", "attendance"));
  }

  @Test
  void updateReturnsReferenceAwareDeletability() {
    existing();
    when(orders.findReferencedCastIds(Set.of("e1"))).thenReturn(Set.of("e1"));
    assertThat(service.update(STORE_ID, "e1", new CastUpdateRequest(), "actor").isDeletable())
        .isFalse();
  }

  @Test
  void attendancePreventsDeletion() {
    when(enrollments.findByIdAndStoreIdForUpdate("e1", STORE_ID))
        .thenReturn(Optional.of(CastEnrollment.builder().build()));
    when(attendance.findReferencedCastIds(List.of("e1"))).thenReturn(Set.of("e1"));
    assertThatThrownBy(() -> service.delete(STORE_ID, "e1", "actor"))
        .isInstanceOf(ConflictException.class);
  }
}
