package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shift.api.dto.AttendanceCancellationRequest;
import com.kizuna.shift.api.dto.AttendanceCorrectionRequest;
import com.kizuna.shift.api.dto.AttendanceCreateRequest;
import com.kizuna.shift.api.dto.AttendanceMapperImpl;
import com.kizuna.shift.api.dto.ShiftChangeRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftCreateRequest;
import com.kizuna.shift.api.dto.ShiftMapperImpl;
import com.kizuna.shift.api.dto.ShiftRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftRequestMapperImpl;
import com.kizuna.shift.api.dto.ShiftUpdateRequest;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.domain.AttendanceCorrection;
import com.kizuna.shift.domain.AttendanceCorrectionRepository;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ShiftAuditTest {
  private final ShiftRepository shifts = mock(ShiftRepository.class);
  private final AttendanceRepository attendances = mock(AttendanceRepository.class);
  private final CastEnrollmentRepository enrollments = mock(CastEnrollmentRepository.class);
  private final StoreRepository stores = mock(StoreRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final List<AuditEvent> events = new ArrayList<>();
  private final BusinessDateService dates =
      new BusinessDateService(mock(SystemConfigService.class), Clock.systemUTC());
  private BusinessAudit audit;
  private ShiftService service;
  private AttendanceService actuals;
  private Attendance attendance;
  private final List<AttendanceCorrection> corrections = new ArrayList<>();
  private Shift row;
  private final ShiftRequestRepository requests = mock(ShiftRequestRepository.class);
  private final Map<String, ShiftRequest> requestRows = new HashMap<>();
  private CastShiftRequestService ownRequests;
  private ShiftRequestService decisions;

  @BeforeEach
  void prepare() {
    var actor =
        PlatformUser.builder()
            .email("operator@example.test")
            .password(UUID.randomUUID().toString())
            .enabled(true)
            .displayName("担当")
            .userType(UserType.STAFF)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    actor.setId(9L);
    when(users.findByEmail(anyString())).thenReturn(Optional.of(actor));
    when(users.findById(9L)).thenReturn(Optional.of(actor));
    var repository = mock(AuditEventRepository.class);
    when(repository.saveAndFlush(any(AuditEvent.class)))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              events.add(event);
              return event;
            });
    audit = new BusinessAudit(users, new AuditWriter(repository, Clock.systemUTC()));
    var enrollment = CastEnrollment.builder().build();
    enrollment.setId("cast");
    when(enrollments.findScopedByIdForUpdate("cast")).thenReturn(Optional.of(enrollment));
    when(shifts.save(any(Shift.class)))
        .thenAnswer(
            call -> {
              row = call.getArgument(0);
              row.setId("shift");
              row.setStoreId(1L);
              return row;
            });
    when(shifts.findScopedByIdForUpdate("shift")).thenAnswer(call -> Optional.ofNullable(row));
    when(shifts.findById("shift")).thenAnswer(call -> Optional.ofNullable(row));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(actor.getEmail(), null, List.of()));
    when(enrollments.findByIdForUpdate("cast")).thenReturn(Optional.of(enrollment));
    when(enrollments.findIdsByPlatformUserIdAndStoreId(9L, 1L)).thenReturn(List.of("cast"));
    when(enrollments.findIdsByPlatformUserId(9L)).thenReturn(List.of("cast"));
    when(requests.save(any(ShiftRequest.class)))
        .thenAnswer(
            call -> {
              ShiftRequest r = call.getArgument(0);
              if (r.getId() == null) r.setId("request-" + requestRows.size());
              r.setStoreId(1L);
              requestRows.put(r.getId(), r);
              return r;
            });
    when(requests.findById(anyString()))
        .thenAnswer(call -> Optional.ofNullable(requestRows.get(call.getArgument(0))));
    ownRequests =
        new CastShiftRequestService(
            users,
            enrollments,
            requests,
            shifts,
            attendances,
            new ShiftRequestMapperImpl(),
            dates,
            stores,
            audit);
    decisions =
        new ShiftRequestService(
            requests,
            enrollments,
            shifts,
            attendances,
            new ShiftRequestMapperImpl(),
            users,
            dates,
            stores,
            audit);
    when(requests.findLinkedForUpdate(anyString(), anyString(), any()))
        .thenAnswer(
            call ->
                requestRows.values().stream()
                    .filter(
                        r ->
                            call.getArgument(0).equals(r.getShiftId())
                                && r.getId().compareTo(call.getArgument(1)) > 0)
                    .sorted(java.util.Comparator.comparing(ShiftRequest::getId))
                    .toList());
    var correctionRepository = mock(AttendanceCorrectionRepository.class);
    when(correctionRepository.save(any(AttendanceCorrection.class)))
        .thenAnswer(
            call -> {
              AttendanceCorrection c = call.getArgument(0);
              c.setId("correction-" + corrections.size());
              corrections.add(c);
              return c;
            });
    when(attendances.saveAndFlush(any(Attendance.class)))
        .thenAnswer(
            call -> {
              attendance = call.getArgument(0);
              attendance.setId("attendance");
              attendance.setStoreId(1L);
              return attendance;
            });
    when(attendances.findById("attendance")).thenAnswer(call -> Optional.ofNullable(attendance));
    var castService = mock(CastService.class);
    when(castService.existsForCurrentStoreForUpdate("cast")).thenReturn(true);
    actuals =
        new AttendanceService(
            attendances,
            correctionRepository,
            new AttendanceMapperImpl(),
            shifts,
            castService,
            users,
            dates,
            audit);
    var store = new StoreContext();
    store.setStoreId(1L);
    service =
        new ShiftService(
            shifts,
            attendances,
            new ShiftMapperImpl(),
            enrollments,
            stores,
            store,
            mock(CastProfileRepository.class),
            users,
            dates,
            audit,
            requests);
  }

  private ShiftCreateRequest createRequest() {
    var request = new ShiftCreateRequest();
    request.setCastId("cast");
    request.setWorkDate(LocalDate.of(2030, 1, 2));
    request.setStartTime(LocalTime.of(10, 0));
    request.setEndTime(LocalTime.of(18, 0));
    return request;
  }

  @Test
  void directCreationIsTraceableByActorStoreAndSafeState() {
    var created = service.create(createRequest(), "operator@example.test");
    assertThat(events).hasSize(1);
    var event = events.getFirst();
    assertThat(event.getAction()).isEqualTo("SHIFT_CREATED");
    assertThat(event.getTargetId()).isEqualTo(created.getId());
    assertThat(event.getActorId()).isEqualTo(9L);
    assertThat(event.getStoreId()).isEqualTo(1L);
    assertThat(event.getBeforeValues()).isEmpty();
    assertThat(event.getAfterValues())
        .containsEntry("cast_id", "cast")
        .containsEntry("status", "TENTATIVE")
        .containsEntry("published", "true");
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void editingPublicationAndDeletionRetainOnlyActualBusinessChanges() {
    service.create(createRequest(), "operator@example.test");
    events.clear();
    var patch = new ShiftUpdateRequest();
    patch.setStartTime(LocalTime.of(11, 0));
    service.update("shift", patch, "operator@example.test");
    service.update("shift", patch, "operator@example.test");
    service.changePublication("shift", false, "operator@example.test");
    service.changePublication("shift", false, "operator@example.test");
    service.delete("shift");
    assertThat(events)
        .extracting(AuditEvent::getAction)
        .containsExactly("SHIFT_UPDATED", "SHIFT_PUBLICATION_CHANGED", "SHIFT_DELETED");
    assertThat(events.get(0).getBeforeValues()).containsEntry("start_time", "10:00");
    assertThat(events.get(0).getAfterValues()).containsEntry("start_time", "11:00");
    assertThat(events.get(1).getAfterValues()).containsEntry("published", "false");
    assertThat(events.get(2).getAfterValues())
        .containsOnlyKeys("exists")
        .containsEntry("exists", "false");
  }

  @Test
  void requestsDecisionsAndDerivedShiftsFormOneTraceableSeries() {
    var request = new ShiftRequestCreateRequest();
    request.setStoreId(1L);
    request.setWorkDate(LocalDate.of(2030, 1, 2));
    request.setStartTime(LocalTime.of(10, 0));
    request.setEndTime(LocalTime.of(18, 0));
    request.setNote("秘密希望");
    var submitted = ownRequests.submit("operator@example.test", request);
    decisions.approve(submitted.getId(), false, "operator@example.test");
    var change = new ShiftChangeRequestCreateRequest();
    change.setShiftId("shift");
    change.setWorkDate(request.getWorkDate());
    change.setStartTime(LocalTime.of(11, 0));
    change.setEndTime(request.getEndTime());
    change.setNote("秘密変更");
    var revised = ownRequests.submitChange("operator@example.test", change);
    decisions.approve(revised.getId(), null, "operator@example.test");
    var declined = ownRequests.submit("operator@example.test", request);
    decisions.decline(declined.getId(), "operator@example.test");
    assertThat(events)
        .extracting(AuditEvent::getAction)
        .containsExactly(
            "SHIFT_REQUEST_SUBMITTED",
            "SHIFT_CREATED",
            "SHIFT_REQUEST_APPROVED",
            "SHIFT_CHANGE_REQUEST_SUBMITTED",
            "SHIFT_UPDATED",
            "SHIFT_REQUEST_APPROVED",
            "SHIFT_REQUEST_SUBMITTED",
            "SHIFT_REQUEST_DECLINED");
    assertThat(events.get(1).getSourceId()).isEqualTo(submitted.getId());
    assertThat(events.get(4).getSourceId()).isEqualTo(revised.getId());
    assertThat(events.get(4).getAfterValues())
        .containsEntry("published", "false")
        .containsEntry("start_time", "11:00");
    assertThat(events.getFirst().getAfterValues()).containsEntry("redacted_fields_changed", "note");
    assertThat(events.stream().map(e -> e.getAfterValues().toString()))
        .noneMatch(s -> s.contains("秘密"));
  }

  @Test
  void attendanceCorrectionAndCancellationRetainHistoryWithoutPrivateText() {
    var request = new AttendanceCreateRequest();
    request.setCastId("cast");
    request.setActualStartAt(LocalDate.of(2030, 1, 2).atTime(10, 0));
    request.setWaitingPlace("秘密待機場所");
    actuals.record(request, "operator@example.test");
    var correction = new AttendanceCorrectionRequest();
    correction.setBusinessDate(LocalDate.of(2030, 1, 2));
    correction.setActualStartAt(request.getActualStartAt());
    correction.setWaitingPlace("新しい秘密待機場所");
    actuals.correct("attendance", correction, "operator@example.test");
    actuals.correct("attendance", correction, "operator@example.test");
    var cancellation = new AttendanceCancellationRequest();
    cancellation.setReason("秘密取消理由");
    actuals.cancel("attendance", cancellation, "operator@example.test");
    assertThatThrownBy(() -> actuals.cancel("attendance", cancellation, "operator@example.test"))
        .isInstanceOf(RuntimeException.class);
    assertThat(events)
        .extracting(AuditEvent::getAction)
        .containsExactly(
            "ATTENDANCE_RECORDED",
            "ATTENDANCE_CORRECTED",
            "ATTENDANCE_CORRECTED",
            "ATTENDANCE_CANCELLED");
    assertThat(events.get(1).getSourceId()).isEqualTo("correction-0");
    assertThat(events.get(2).getSourceId()).isEqualTo("correction-1");
    assertThat(events.get(1).getAfterValues())
        .containsEntry("redacted_fields_changed", "waiting_place");
    assertThat(events.get(2).getAfterValues()).doesNotContainKey("redacted_fields_changed");
    assertThat(events.get(3).getAfterValues())
        .containsEntry("cancelled", "true")
        .containsEntry("redacted_fields_changed", "cancelled_reason");
    assertThat(events.stream().map(e -> e.getAfterValues().toString()))
        .noneMatch(s -> s.contains("秘密"));
  }

  @Test
  void deletingShiftPreservesTheRemovedRequestLink() {
    var request = new ShiftRequestCreateRequest();
    request.setStoreId(1L);
    request.setWorkDate(LocalDate.of(2030, 1, 2));
    request.setStartTime(LocalTime.of(10, 0));
    request.setEndTime(LocalTime.of(18, 0));
    var submitted = ownRequests.submit("operator@example.test", request);
    decisions.approve(submitted.getId(), false, "operator@example.test");
    events.clear();
    service.delete("shift");
    assertThat(events)
        .extracting(AuditEvent::getAction)
        .containsExactly("SHIFT_REQUEST_UNLINKED", "SHIFT_DELETED");
    var unlink = events.getFirst();
    assertThat(unlink.getTargetId()).isEqualTo(submitted.getId());
    assertThat(unlink.getSourceType()).isEqualTo("SHIFT");
    assertThat(unlink.getSourceId()).isEqualTo("shift");
    assertThat(unlink.getBeforeValues()).containsEntry("shift_id", "shift");
    assertThat(unlink.getAfterValues())
        .containsEntry("shift_id", "")
        .containsEntry("status", "APPROVED");
  }
}
