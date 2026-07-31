package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shift.api.dto.ShiftRequestMapper;
import com.kizuna.shift.api.dto.StoreShiftRequestResponse;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestKind;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestStateException;
import com.kizuna.shift.domain.ShiftRequestStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShiftRequestServiceTest {

  private static final String STAFF = "staff@kizuna.test";

  @Mock private ShiftRequestRepository shiftRequestRepository;
  @Mock private ShiftRepository shiftRepository;
  @Mock private ShiftRequestMapper shiftRequestMapper;

  @InjectMocks private ShiftRequestService shiftRequestService;

  private ShiftRequest pendingRequest() {
    ShiftRequest request =
        ShiftRequest.builder()
            .castId("c1")
            .workDate(LocalDate.of(2999, 8, 1))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .build();
    request.setId("sr1");
    return request;
  }

  @Test
  void list_returnsAllWhenStatusNull() {
    when(shiftRequestRepository.findAllByOrderByCreatedAtAsc())
        .thenReturn(List.of(pendingRequest()));
    when(shiftRequestMapper.toStoreResponse(any()))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr1").build());

    List<StoreShiftRequestResponse> result = shiftRequestService.list(null);

    assertThat(result).hasSize(1);
    verify(shiftRequestRepository, never()).findByStatusOrderByCreatedAtAsc(any());
  }

  @Test
  void list_filtersByStatus() {
    when(shiftRequestRepository.findByStatusOrderByCreatedAtAsc(ShiftRequestStatus.PENDING))
        .thenReturn(List.of(pendingRequest()));
    when(shiftRequestMapper.toStoreResponse(any()))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr1").build());

    List<StoreShiftRequestResponse> result = shiftRequestService.list("PENDING");

    assertThat(result).hasSize(1);
    verify(shiftRequestRepository, never()).findAllByOrderByCreatedAtAsc();
  }

  @Test
  void list_rejectsInvalidStatus() {
    assertThatThrownBy(() -> shiftRequestService.list("BOGUS"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("不正なステータスです");
  }

  @Test
  void approve_throwsWhenNotFound() {
    when(shiftRequestRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftRequestService.approve("missing", STAFF))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("出勤希望が見つかりません");

    verify(shiftRepository, never()).save(any());
  }

  @Test
  void approve_transitionsRequestAndCreatesConfirmedShift() {
    ShiftRequest request = pendingRequest();
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRequestRepository.save(request)).thenReturn(request);
    when(shiftRequestMapper.toStoreResponse(request))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr1").status("APPROVED").build());

    when(shiftRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Shift shift = invocation.getArgument(0);
              shift.setId("s1");
              return shift;
            });

    StoreShiftRequestResponse result = shiftRequestService.approve("sr1", STAFF);

    assertThat(result.getStatus()).isEqualTo("APPROVED");
    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.APPROVED);

    ArgumentCaptor<Shift> shiftCaptor = ArgumentCaptor.forClass(Shift.class);
    verify(shiftRepository).save(shiftCaptor.capture());
    Shift shift = shiftCaptor.getValue();
    assertThat(shift.getCastId()).isEqualTo("c1");
    assertThat(shift.getWorkDate()).isEqualTo(request.getWorkDate());
    assertThat(shift.getStartTime()).isEqualTo(request.getStartTime());
    assertThat(shift.getEndTime()).isEqualTo(request.getEndTime());
    assertThat(shift.getStatus()).isEqualTo("CONFIRMED");
    assertThat(shift.getApprovedBy()).isEqualTo(STAFF);
    assertThat(shift.getApprovedAt()).isNotNull();
    assertThat(request.getTargetShiftId()).isEqualTo("s1");
    assertThat(request.getDecidedBy()).isEqualTo(STAFF);
    assertThat(request.getDecidedAt()).isNotNull();
  }

  @Test
  void approve_whenAlreadyProcessed_throwsStateExceptionAndCreatesNoShift() {
    ShiftRequest request = pendingRequest();
    request.approve(STAFF);
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));

    assertThatThrownBy(() -> shiftRequestService.approve("sr1", STAFF))
        .isInstanceOf(ShiftRequestStateException.class);

    verify(shiftRepository, never()).save(any());
    verify(shiftRequestRepository, never()).save(any());
  }

  @Test
  void decline_transitionsRequestAndDoesNotCreateShift() {
    ShiftRequest request = pendingRequest();
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRequestRepository.save(request)).thenReturn(request);
    when(shiftRequestMapper.toStoreResponse(request))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr1").status("DECLINED").build());

    StoreShiftRequestResponse result = shiftRequestService.decline("sr1", STAFF);

    assertThat(result.getStatus()).isEqualTo("DECLINED");
    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.DECLINED);
    assertThat(request.getDecidedBy()).isEqualTo(STAFF);
    assertThat(request.getDecidedAt()).isNotNull();
    verify(shiftRepository, never()).save(any());
  }

  @Test
  void decline_throwsWhenNotFound() {
    when(shiftRequestRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftRequestService.decline("missing", STAFF))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("出勤希望が見つかりません");
  }

  // ---- 変更申請（kind=CHANGE） ----

  private ShiftRequest pendingChangeRequest() {
    ShiftRequest request =
        ShiftRequest.builder()
            .castId("c1")
            .kind(ShiftRequestKind.CHANGE)
            .targetShiftId("s1")
            .workDate(LocalDate.of(2999, 8, 2))
            .startTime(LocalTime.of(19, 0))
            .endTime(LocalTime.of(22, 0))
            .build();
    request.setId("sr1");
    request.setStoreId(1L);
    return request;
  }

  private Shift targetShift() {
    return targetShift("c1", "CONFIRMED");
  }

  private Shift targetShift(String castId, String status) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(LocalDate.of(2999, 8, 1))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(status)
            .publicVisible(false)
            .build();
    shift.setId("s1");
    shift.setStoreId(1L);
    return shift;
  }

  @Test
  void approveChange_updatesTargetShiftInPlaceWithoutCreatingANewOne() {
    ShiftRequest request = pendingChangeRequest();
    Shift target = targetShift();
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRequestRepository.save(request)).thenReturn(request);
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(target));
    when(shiftRepository.findAllById(List.of("s1"))).thenReturn(List.of(target));
    when(shiftRequestMapper.toStoreResponse(request))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr1").status("APPROVED").build());

    StoreShiftRequestResponse result = shiftRequestService.approve("sr1", STAFF);

    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.APPROVED);
    // 既存の行が更新される（新規作成ではない）ので、シフト側だけが持つ属性は申請の外側で保たれる。
    verify(shiftRepository).save(target);
    assertThat(target.getWorkDate()).isEqualTo(LocalDate.of(2999, 8, 2));
    assertThat(target.getStartTime()).isEqualTo(LocalTime.of(19, 0));
    assertThat(target.getEndTime()).isEqualTo(LocalTime.of(22, 0));
    assertThat(target.getStatus()).isEqualTo("CONFIRMED");
    assertThat(target.getCastId()).isEqualTo("c1");
    assertThat(target.isPublicVisible()).isFalse();
    // 応答には変更後の対象シフトの現在値が添う。
    assertThat(result.getCurrentStartTime()).isEqualTo(LocalTime.of(19, 0));
  }

  @Test
  void approveChange_whenTargetShiftBelongsToAnotherStore_isRejectedAndLeavesItUntouched() {
    ShiftRequest request = pendingChangeRequest();
    Shift foreign = targetShift();
    foreign.setStoreId(2L);
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> shiftRequestService.approve("sr1", STAFF))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("変更対象のシフトが見つかりません");

    verify(shiftRepository, never()).save(any());
    verify(shiftRequestRepository, never()).save(any());
    assertThat(foreign.getWorkDate()).isEqualTo(LocalDate.of(2999, 8, 1));
  }

  @Test
  void approveChange_whenTargetShiftWasReassigned_isRejectedAndLeavesItUntouched() {
    ShiftRequest request = pendingChangeRequest();
    Shift reassigned = targetShift("c2", "CONFIRMED");
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(reassigned));

    assertThatThrownBy(() -> shiftRequestService.approve("sr1", STAFF))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("変更対象のシフトが見つかりません");

    verify(shiftRepository, never()).save(any());
    verify(shiftRequestRepository, never()).save(any());
    assertThat(reassigned.getWorkDate()).isEqualTo(LocalDate.of(2999, 8, 1));
  }

  @Test
  void approveChange_whenTargetShiftIsNoLongerConfirmed_isRejectedAndLeavesItUntouched() {
    ShiftRequest request = pendingChangeRequest();
    Shift tentative = targetShift("c1", "TENTATIVE");
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(tentative));

    assertThatThrownBy(() -> shiftRequestService.approve("sr1", STAFF))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("確定済み");

    verify(shiftRepository, never()).save(any());
    verify(shiftRequestRepository, never()).save(any());
    assertThat(tentative.getWorkDate()).isEqualTo(LocalDate.of(2999, 8, 1));
  }

  @Test
  void approveChange_whenTargetShiftHasActual_isRejectedAndLeavesItUntouched() {
    ShiftRequest request = pendingChangeRequest();
    Shift target = targetShift();
    target.recordActual(LocalTime.of(18, 5), LocalTime.of(23, 10), STAFF);
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(target));

    assertThatThrownBy(() -> shiftRequestService.approve("sr1", STAFF))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("実績記録済み");

    verify(shiftRepository, never()).save(any());
    verify(shiftRequestRepository, never()).save(any());
    assertThat(target.getWorkDate()).isEqualTo(LocalDate.of(2999, 8, 1));
    assertThat(target.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(target.getEndTime()).isEqualTo(LocalTime.of(23, 0));
  }

  @Test
  void approveChange_whenTargetShiftIsGone_isRejected() {
    ShiftRequest request = pendingChangeRequest();
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRepository.findById("s1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftRequestService.approve("sr1", STAFF))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("変更対象のシフトが見つかりません");

    verify(shiftRequestRepository, never()).save(any());
  }

  @Test
  void declineChange_leavesTargetShiftUnchanged() {
    ShiftRequest request = pendingChangeRequest();
    Shift target = targetShift();
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRequestRepository.save(request)).thenReturn(request);
    when(shiftRepository.findAllById(List.of("s1"))).thenReturn(List.of(target));
    when(shiftRequestMapper.toStoreResponse(request))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr1").status("DECLINED").build());

    StoreShiftRequestResponse result = shiftRequestService.decline("sr1", STAFF);

    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.DECLINED);
    verify(shiftRepository, never()).save(any());
    assertThat(target.getWorkDate()).isEqualTo(LocalDate.of(2999, 8, 1));
    assertThat(target.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(target.getEndTime()).isEqualTo(LocalTime.of(23, 0));
    // 謝絶の応答でも、元のシフトの現在値が変更前のまま添う。
    assertThat(result.getCurrentStartTime()).isEqualTo(LocalTime.of(18, 0));
  }

  @Test
  void list_attachesCurrentShiftValuesToChangeRequests() {
    ShiftRequest request = pendingChangeRequest();
    when(shiftRequestRepository.findByStatusOrderByCreatedAtAsc(ShiftRequestStatus.PENDING))
        .thenReturn(List.of(request));
    when(shiftRepository.findAllById(List.of("s1"))).thenReturn(List.of(targetShift()));
    when(shiftRequestMapper.toStoreResponse(request))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr1").kind("CHANGE").build());

    List<StoreShiftRequestResponse> result = shiftRequestService.list("PENDING");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCurrentWorkDate()).isEqualTo(LocalDate.of(2999, 8, 1));
    assertThat(result.get(0).getCurrentStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(result.get(0).getCurrentEndTime()).isEqualTo(LocalTime.of(23, 0));
  }
}
