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
import com.kizuna.shift.domain.ShiftPatch;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestStateException;
import com.kizuna.shift.domain.ShiftRequestStatus;
import com.kizuna.shift.domain.ShiftRequestType;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
  @Mock private PlatformUserRepository platformUserRepository;

  @InjectMocks private ShiftRequestService shiftRequestService;

  private static final String ACTOR_EMAIL = "manager@kizuna.test";
  private static final Long ACTOR_ID = 42L;

  private void givenActor() {
    PlatformUser actor =
        PlatformUser.builder()
            .email(ACTOR_EMAIL)
            .password("encoded")
            .displayName("店長")
            .enabled(true)
            .userType(UserType.STAFF)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .roleIds(Set.of(1L))
            .build();
    actor.setId(ACTOR_ID);
    when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(actor));
  }

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

  private ShiftRequest pendingChangeRequest() {
    // original_* は confirmedShift() の時間帯と一致させる（申請時点の対象シフトの控え）
    ShiftRequest request =
        ShiftRequest.builder()
            .castId("c1")
            .type(ShiftRequestType.CHANGE)
            .shiftId("sh1")
            .originalWorkDate(LocalDate.of(2999, 8, 1))
            .originalStartTime(LocalTime.of(18, 0))
            .originalEndTime(LocalTime.of(23, 0))
            .workDate(LocalDate.of(2999, 8, 2))
            .startTime(LocalTime.of(19, 0))
            .endTime(LocalTime.of(22, 0))
            .build();
    request.setId("sr2");
    return request;
  }

  private Shift confirmedShift() {
    Shift shift =
        Shift.builder()
            .castId("c1")
            .workDate(LocalDate.of(2999, 8, 1))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(ShiftStatus.CONFIRMED)
            .build();
    shift.setId("sh1");
    return shift;
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

    assertThatThrownBy(() -> shiftRequestService.approve("missing", ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("出勤希望が見つかりません");

    verify(shiftRepository, never()).save(any());
  }

  @Test
  void approve_transitionsRequestAndCreatesConfirmedShift() {
    ShiftRequest request = pendingRequest();
    givenActor();
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRequestRepository.save(request)).thenReturn(request);
    when(shiftRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Shift saved = invocation.getArgument(0);
              saved.setId("sh_new");
              return saved;
            });
    when(shiftRequestMapper.toStoreResponse(request))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr1").status("APPROVED").build());

    StoreShiftRequestResponse result = shiftRequestService.approve("sr1", ACTOR_EMAIL);

    assertThat(result.getStatus()).isEqualTo("APPROVED");
    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.APPROVED);
    assertThat(request.getProcessedBy()).as("承認は processed_by を印字すること").isEqualTo(ACTOR_ID);
    assertThat(request.getProcessedAt()).as("承認は processed_at を印字すること").isNotNull();

    ArgumentCaptor<Shift> shiftCaptor = ArgumentCaptor.forClass(Shift.class);
    verify(shiftRepository).save(shiftCaptor.capture());
    Shift shift = shiftCaptor.getValue();
    assertThat(shift.getCastId()).isEqualTo("c1");
    assertThat(shift.getWorkDate()).isEqualTo(request.getWorkDate());
    assertThat(shift.getStartTime()).isEqualTo(request.getStartTime());
    assertThat(shift.getEndTime()).isEqualTo(request.getEndTime());
    assertThat(shift.getStatus()).isEqualTo(ShiftStatus.CONFIRMED);
    assertThat(shift.getCreatedBy()).as("承認で生まれた行は created_by が承認者であること").isEqualTo(ACTOR_ID);
    assertThat(request.getShiftId()).as("生成したシフトの id が申請行へ回写されること").isEqualTo(shift.getId());
  }

  @Test
  void approve_changeRequest_updatesTargetShiftPreservingStatusAndCastId() {
    ShiftRequest request = pendingChangeRequest();
    Shift target = confirmedShift();
    givenActor();
    when(shiftRequestRepository.findById("sr2")).thenReturn(Optional.of(request));
    when(shiftRepository.findById("sh1")).thenReturn(Optional.of(target));
    when(shiftRequestRepository.save(request)).thenReturn(request);
    when(shiftRequestMapper.toStoreResponse(request))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr2").status("APPROVED").build());

    shiftRequestService.approve("sr2", ACTOR_EMAIL);

    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.APPROVED);
    assertThat(target.getWorkDate()).isEqualTo(request.getWorkDate());
    assertThat(target.getStartTime()).isEqualTo(request.getStartTime());
    assertThat(target.getEndTime()).isEqualTo(request.getEndTime());
    assertThat(target.getStatus()).as("承認と独立した軸（status）は保持されること").isEqualTo(ShiftStatus.CONFIRMED);
    assertThat(target.getCastId()).isEqualTo("c1");
    assertThat(target.getUpdatedBy()).as("変更申請の承認は updated_by を承認者にすること").isEqualTo(ACTOR_ID);
    assertThat(request.getShiftId()).as("CHANGE の関連シフトは対象のまま").isEqualTo("sh1");

    // 対象シフトの更新のみで、新規シフトは作成されないこと
    ArgumentCaptor<Shift> shiftCaptor = ArgumentCaptor.forClass(Shift.class);
    verify(shiftRepository).save(shiftCaptor.capture());
    assertThat(shiftCaptor.getValue()).isSameAs(target);
  }

  @Test
  void approve_changeRequest_rejectsWhenTargetShiftNoLongerConfirmed() {
    ShiftRequest request = pendingChangeRequest();
    Shift target = confirmedShift();
    target.apply(new ShiftPatch(null, null, null, null, ShiftStatus.TENTATIVE));
    givenActor();
    when(shiftRequestRepository.findById("sr2")).thenReturn(Optional.of(request));
    when(shiftRepository.findById("sh1")).thenReturn(Optional.of(target));

    assertThatThrownBy(() -> shiftRequestService.approve("sr2", ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("確定済みでないシフト");

    verify(shiftRepository, never()).save(any());
    verify(shiftRequestRepository, never()).save(any());
    assertThat(target.getStartTime()).as("対象シフトが書き換えられないこと").isEqualTo(LocalTime.of(18, 0));
  }

  @Test
  void approve_changeRequest_rejectsWhenTargetSlotEditedAfterSubmission() {
    ShiftRequest request = pendingChangeRequest();
    Shift target = confirmedShift();
    // 申請後に店舗がシフト編集で時間帯を動かした状態
    target.apply(new ShiftPatch(null, null, LocalTime.of(20, 0), null, null));
    givenActor();
    when(shiftRequestRepository.findById("sr2")).thenReturn(Optional.of(request));
    when(shiftRepository.findById("sh1")).thenReturn(Optional.of(target));

    assertThatThrownBy(() -> shiftRequestService.approve("sr2", ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("申請後に変更されています");

    verify(shiftRepository, never()).save(any());
    verify(shiftRequestRepository, never()).save(any());
  }

  @Test
  void list_marksChangeRequestUnapprovableWhenTargetDriftedOrMissing() {
    ShiftRequest drifted = pendingChangeRequest();
    Shift editedTarget = confirmedShift();
    editedTarget.apply(new ShiftPatch(null, null, LocalTime.of(20, 0), null, null));
    when(shiftRequestRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(drifted));
    when(shiftRepository.findAllById(List.of("sh1"))).thenReturn(List.of(editedTarget));
    when(shiftRequestMapper.toStoreResponse(drifted))
        .thenReturn(
            StoreShiftRequestResponse.builder().id("sr2").type("CHANGE").shiftId("sh1").build());

    List<StoreShiftRequestResponse> result = shiftRequestService.list(null);

    assertThat(result.get(0).getApprovable()).isFalse();
  }

  @Test
  void approve_changeRequest_rejectsWhenTargetShiftReassignedToAnotherCast() {
    ShiftRequest request = pendingChangeRequest();
    Shift target = confirmedShift();
    target.apply(new ShiftPatch("other-cast", null, null, null, null));
    givenActor();
    when(shiftRequestRepository.findById("sr2")).thenReturn(Optional.of(request));
    when(shiftRepository.findById("sh1")).thenReturn(Optional.of(target));

    assertThatThrownBy(() -> shiftRequestService.approve("sr2", ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("別のキャストに変更されています");

    verify(shiftRepository, never()).save(any());
    verify(shiftRequestRepository, never()).save(any());
    assertThat(target.getStartTime()).as("対象シフトが書き換えられないこと").isEqualTo(LocalTime.of(18, 0));
  }

  @Test
  void approve_changeRequest_rejectsWhenTargetShiftAlreadyDeleted() {
    // 対象シフト削除で FK（SET NULL）により shift_id が null に落ちた変更申請
    ShiftRequest request =
        ShiftRequest.builder()
            .castId("c1")
            .type(ShiftRequestType.CHANGE)
            .workDate(LocalDate.of(2999, 8, 2))
            .startTime(LocalTime.of(19, 0))
            .endTime(LocalTime.of(22, 0))
            .build();
    request.setId("sr2");
    givenActor();
    when(shiftRequestRepository.findById("sr2")).thenReturn(Optional.of(request));

    assertThatThrownBy(() -> shiftRequestService.approve("sr2", ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("既に削除されています");

    verify(shiftRepository, never()).findById(any());
    verify(shiftRequestRepository, never()).save(any());
  }

  @Test
  void approve_changeRequest_throwsWhenTargetShiftMissing() {
    ShiftRequest request = pendingChangeRequest();
    givenActor();
    when(shiftRequestRepository.findById("sr2")).thenReturn(Optional.of(request));
    when(shiftRepository.findById("sh1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftRequestService.approve("sr2", ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("シフトが見つかりません");

    verify(shiftRepository, never()).save(any());
    verify(shiftRequestRepository, never()).save(any());
  }

  @Test
  void decline_changeRequest_leavesTargetShiftUntouched() {
    ShiftRequest request = pendingChangeRequest();
    givenActor();
    when(shiftRequestRepository.findById("sr2")).thenReturn(Optional.of(request));
    when(shiftRequestRepository.save(request)).thenReturn(request);
    when(shiftRequestMapper.toStoreResponse(request))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr2").status("DECLINED").build());

    shiftRequestService.decline("sr2", ACTOR_EMAIL);

    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.DECLINED);
    verify(shiftRepository, never()).findById(any());
    verify(shiftRepository, never()).save(any());
  }

  @Test
  void list_inlinesCurrentShiftValuesOnlyForChangeRequests() {
    // 承認済みの NEW も関連シフトを持つ。引く対象がここまで広がると、差分の提示先でない行に
    // current_* が付く（問い合わせの引数がそのまま守衛なので、stub の引数一致で赤にできる）。
    ShiftRequest newRequest = pendingRequest();
    newRequest.approve(ACTOR_ID, OffsetDateTime.now());
    newRequest.linkShift("sh_generated");
    ShiftRequest changeRequest = pendingChangeRequest();
    Shift target = confirmedShift();
    when(shiftRequestRepository.findAllByOrderByCreatedAtAsc())
        .thenReturn(List.of(newRequest, changeRequest));
    when(shiftRepository.findAllById(List.of("sh1"))).thenReturn(List.of(target));
    when(shiftRequestMapper.toStoreResponse(newRequest))
        .thenReturn(
            StoreShiftRequestResponse.builder()
                .id("sr1")
                .type("NEW")
                .shiftId("sh_generated")
                .build());
    when(shiftRequestMapper.toStoreResponse(changeRequest))
        .thenReturn(
            StoreShiftRequestResponse.builder().id("sr2").type("CHANGE").shiftId("sh1").build());

    List<StoreShiftRequestResponse> result = shiftRequestService.list(null);

    assertThat(result).hasSize(2);
    StoreShiftRequestResponse newRow = result.get(0);
    assertThat(newRow.getCurrentWorkDate()).as("承認済み NEW の関連シフトは現行日時の内联先にしない").isNull();
    assertThat(newRow.getApprovable()).as("NEW には適用可否を付けない").isNull();
    StoreShiftRequestResponse changeRow = result.get(1);
    assertThat(changeRow.getCurrentWorkDate()).isEqualTo(target.getWorkDate());
    assertThat(changeRow.getCurrentStartTime()).isEqualTo(target.getStartTime());
    assertThat(changeRow.getCurrentEndTime()).isEqualTo(target.getEndTime());
    assertThat(changeRow.getApprovable()).as("対象が申請時点のままなら適用可能").isTrue();
  }

  @Test
  void approve_whenAlreadyProcessed_throwsStateExceptionAndCreatesNoShift() {
    ShiftRequest request = pendingRequest();
    request.approve(ACTOR_ID, OffsetDateTime.now());
    givenActor();
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));

    assertThatThrownBy(() -> shiftRequestService.approve("sr1", ACTOR_EMAIL))
        .isInstanceOf(ShiftRequestStateException.class);

    verify(shiftRepository, never()).save(any());
    verify(shiftRequestRepository, never()).save(any());
  }

  @Test
  void decline_transitionsRequestAndDoesNotCreateShift() {
    ShiftRequest request = pendingRequest();
    givenActor();
    when(shiftRequestRepository.findById("sr1")).thenReturn(Optional.of(request));
    when(shiftRequestRepository.save(request)).thenReturn(request);
    when(shiftRequestMapper.toStoreResponse(request))
        .thenReturn(StoreShiftRequestResponse.builder().id("sr1").status("DECLINED").build());

    StoreShiftRequestResponse result = shiftRequestService.decline("sr1", ACTOR_EMAIL);

    assertThat(result.getStatus()).isEqualTo("DECLINED");
    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.DECLINED);
    assertThat(request.getProcessedBy()).as("却下も processed_by を印字すること").isEqualTo(ACTOR_ID);
    assertThat(request.getProcessedAt()).as("却下も processed_at を印字すること").isNotNull();
    verify(shiftRepository, never()).save(any());
  }

  @Test
  void decline_throwsWhenNotFound() {
    when(shiftRequestRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftRequestService.decline("missing", ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("出勤希望が見つかりません");
  }
}
