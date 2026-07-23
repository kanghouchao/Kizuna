package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shift.api.dto.ShiftRequestMapper;
import com.kizuna.shift.api.dto.StoreShiftRequestResponse;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
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

    assertThatThrownBy(() -> shiftRequestService.approve("missing"))
        .isInstanceOf(ServiceException.class)
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

    StoreShiftRequestResponse result = shiftRequestService.approve("sr1");

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
  }

  @Test
  void approve_whenAlreadyProcessed_throwsStateExceptionAndCreatesNoShift() {
    ShiftRequest request = pendingRequest();
    request.approve();
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));

    assertThatThrownBy(() -> shiftRequestService.approve("sr1"))
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

    StoreShiftRequestResponse result = shiftRequestService.decline("sr1");

    assertThat(result.getStatus()).isEqualTo("DECLINED");
    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.DECLINED);
    verify(shiftRepository, never()).save(any());
  }

  @Test
  void decline_throwsWhenNotFound() {
    when(shiftRequestRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftRequestService.decline("missing"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("出勤希望が見つかりません");
  }
}
